#!/usr/bin/env node
import { parseArgs, type ParseArgsConfig } from "node:util";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";
import { createApp, createPackage, createToken, listReleases, movePointer, revokeToken, rotatePublicKey } from "./admin.ts";
import { build } from "./build.ts";
import { bundle } from "./bundle.ts";
import { ADMIN_TOKEN_ENV, DEFAULT_URL, resolveUrl, requireToken, TOKEN_ENV, UpdatesClient } from "./client.ts";
import { loadConfig, requireName } from "./config.ts";
import { generateKeyPair } from "./keys.ts";
import { publish } from "./publish.ts";
import { generateKt, generateTs } from "./schema/generate.ts";
import { loadSchema } from "./schema/load.ts";

const USAGE = `usage: tinyui build [--root <dir>] [--out <dir>] [--qjsc <path>] [--js-only] [--version <v>]
       tinyui bundle --runtime-version <rv> --signing-key <pem> [--dist <dir>] [--rollout <0-100>] [--out <dir>]
       tinyui keys generate [--out <pem>]
       tinyui schema --entry <schema.ts> [--ts <file>] [--kt <file> --package <pkg> [--object <Name>]] [--check]
       tinyui publish --channel <c> [--app <a>] [--dir <dir>] [--rollout <0-100>]
       tinyui apps create <id> --name <n> [--org <o>]
       tinyui packages create <pkg> --public-key <k> [--app <a>]
       tinyui packages rotate-key <pkg> --public-key <k> [--app <a>]
       tinyui tokens create <pkg> --channels <a,b> [--app <a>]
       tinyui tokens revoke <pkg> --id <token-id> [--app <a>]
       tinyui releases list --runtime-version <rv> [--pkg <p>] [--app <a>] [--json]
       tinyui releases rollback <version> --runtime-version <rv> --channel <c> [--pkg <p>] [--app <a>]
       tinyui releases rollout <0-100> --runtime-version <rv> --channel <c> [--pkg <p>] [--app <a>]
       tinyui releases promote <version> --runtime-version <rv> --to <c> [--pkg <p>] [--app <a>]

build      compile the pages of the package described by <root>/tinyui.config.json
  --root     project root (default: cwd)
  --out      output directory (default: <root>/dist)
  --qjsc     path to qjsc-kmp (default: $TINYUI_QJSC, then PATH)
  --js-only  emit ESM sources and source maps only, skip bytecode
  --version  package version to record (default: <createdAt>-<git sha>)

bundle     turn a build into the signed upload directory <out>/<pkg>/<rv>/ (docs/updates.md)
  --runtime-version  the host's declared runtimeVersion
  --signing-key      PKCS#8 PEM whose public key is the one in tinyui.config.json
  --dist             tinyui build output (default: ./dist)
  --rollout          percentage written to current.json (default: 100)
  --out              output root (default: <dist>/ota)

keys generate
  --out      where to write the private key (default: ./tinyui-signing-key.pem); the public key is printed

schema
  --entry    TS module whose default export is the component list
  --ts       write JSX prop types here
  --kt       write Kotlin schemas here (with --package, optional --object, default BuiltinSchemas)
  --node-import  where the generated TS imports Node / Ref from (default tinyui-core)
  --check    exit 1 if a target is not already up to date, write nothing

publish    upload a bundle and move the channel pointer (docs/updates.md §6.1)
  --channel  the channel to publish to; never defaulted, never read from the environment
  --dir      a tinyui bundle output root or one <pkg>/<rv> inside it (default: ./dist/ota)
  --rollout  overrides the percentage current.json was bundled with

the publishing and management commands talk to --url, default ${DEFAULT_URL} (or $TINYUI_UPDATES_URL)
  $${TOKEN_ENV}        publish token, for publish and releases
  $${ADMIN_TOKEN_ENV}  instance admin token, for apps, packages and tokens
  $TINYUI_APP          default for --app
  --pkg defaults to "name" in ./tinyui.config.json
`;

type Options = NonNullable<ParseArgsConfig["options"]>;
type Values = Record<string, unknown>;

const URL_OPTION: Options = { url: { type: "string" } };
const RELEASE_OPTIONS: Options = { ...URL_OPTION, app: { type: "string" }, pkg: { type: "string" }, "runtime-version": { type: "string" }, channel: { type: "string" }, to: { type: "string" }, json: { type: "boolean", default: false } };

const COMMANDS: Record<string, { options: Options; run: (v: Values, positionals: string[]) => Promise<number> }> = {
    build: {
        options: { root: { type: "string" }, out: { type: "string" }, qjsc: { type: "string" }, "js-only": { type: "boolean", default: false }, version: { type: "string" } },
        run: async (v) => {
            const result = await build({
                root: (v["root"] as string | undefined) ?? process.cwd(),
                ...(v["out"] !== undefined && { out: v["out"] as string }),
                ...(v["qjsc"] !== undefined && { qjsc: v["qjsc"] as string }),
                ...(v["version"] !== undefined && { version: v["version"] as string }),
                jsOnly: v["js-only"] as boolean,
            });
            for (const m of [...result.runtime, ...result.pages]) process.stdout.write(`${m.name} -> ${m.bin ?? m.js}\n`);
            return 0;
        },
    },
    bundle: {
        options: { "runtime-version": { type: "string" }, "signing-key": { type: "string" }, dist: { type: "string" }, rollout: { type: "string" }, out: { type: "string" } },
        run: async (v) => {
            const runtimeVersion = v["runtime-version"] as string | undefined;
            const signingKey = v["signing-key"] as string | undefined;
            if (!runtimeVersion) throw new Error("bundle: --runtime-version is required");
            if (!signingKey) throw new Error("bundle: --signing-key is required");
            const result = await bundle({
                dist: (v["dist"] as string | undefined) ?? resolve("dist"),
                runtimeVersion,
                signingKey,
                ...(v["rollout"] !== undefined && { rollout: Number(v["rollout"]) }),
                ...(v["out"] !== undefined && { out: v["out"] as string }),
            });
            process.stdout.write(`${result.version} -> ${result.dir}\n`);
            return 0;
        },
    },
    keys: {
        options: { out: { type: "string" } },
        run: async (v, positionals) => {
            if (positionals[1] !== "generate") throw new Error("keys: expected `keys generate`");
            const out = (v["out"] as string | undefined) ?? resolve("tinyui-signing-key.pem");
            const pair = generateKeyPair();
            await mkdir(dirname(out), { recursive: true });
            // the private key must never silently replace one already in use
            await writeFile(out, pair.privateKeyPem, { flag: "wx", mode: 0o600 });
            process.stderr.write(`private key written to ${out}; keep it with the publisher only\n`);
            process.stdout.write(`${pair.publicKey}\n`);
            return 0;
        },
    },
    schema: {
        options: {
            entry: { type: "string" },
            ts: { type: "string" },
            kt: { type: "string" },
            package: { type: "string" },
            object: { type: "string", default: "BuiltinSchemas" },
            "node-import": { type: "string", default: "tinyui-core" },
            check: { type: "boolean", default: false },
        },
        run: (v) => schema(v as Parameters<typeof schema>[0]),
    },
    publish: {
        options: { ...URL_OPTION, app: { type: "string" }, channel: { type: "string" }, dir: { type: "string" }, rollout: { type: "string" } },
        run: async (v) => {
            const client = publishClient(v);
            const result = await publish({
                client,
                app: await appId(v),
                // a mistyped channel is the one command that reaches every user at once
                channel: requireName("--channel", required(v["channel"], "publish: --channel is required")),
                dir: (v["dir"] as string | undefined) ?? resolve("dist", "ota"),
                ...(v["rollout"] !== undefined && { rollout: Number(v["rollout"]) }),
                onUpload: (u) => process.stderr.write(`${u.existing ? "exists  " : "uploaded"} ${u.path}\n`),
            });
            const { pkg, runtimeVersion, version, pointer } = result;
            process.stderr.write(`published ${pkg}/${runtimeVersion}/${version} to ${v["channel"] as string} at rollout ${pointer.rollout} (${result.uploaded} uploaded, ${result.existing} already there)\n`);
            process.stdout.write(`${version}\n`);
            return 0;
        },
    },
    apps: {
        options: { ...URL_OPTION, name: { type: "string" }, org: { type: "string" } },
        run: async (v, positionals) => {
            if (positionals[1] !== "create") throw new Error("apps: expected `apps create <id>`");
            const id = required(positionals[2], "apps create: an app id is required");
            const record = await createApp(adminClient(v), {
                id: requireName("an app id", id),
                name: required(v["name"], "apps create: --name is required"),
                ...(v["org"] !== undefined && { org: v["org"] as string }),
            });
            process.stderr.write(`app ${record.id} created\n`);
            process.stdout.write(`${record.id}\n`);
            return 0;
        },
    },
    packages: {
        options: { ...URL_OPTION, app: { type: "string" }, "public-key": { type: "string" } },
        run: async (v, positionals) => {
            const action = positionals[1];
            if (action !== "create" && action !== "rotate-key") throw new Error("packages: expected `packages create <pkg>` or `packages rotate-key <pkg>`");
            const client = adminClient(v);
            const app = await appId(v);
            const name = await packageName(v, positionals[2]);
            const publicKey = required(v["public-key"], `packages ${action}: --public-key is required (tinyui keys generate prints one)`);
            const record = action === "create" ? await createPackage(client, app, { name, publicKey }) : await rotatePublicKey(client, app, name, publicKey);
            process.stderr.write(`package ${app}/${record.name} ${action === "create" ? "registered" : "now verifies with the new key"}\n`);
            process.stdout.write(`${record.name}\n`);
            return 0;
        },
    },
    tokens: {
        options: { ...URL_OPTION, app: { type: "string" }, channels: { type: "string" }, id: { type: "string" } },
        run: async (v, positionals) => {
            const action = positionals[1];
            if (action !== "create" && action !== "revoke") throw new Error("tokens: expected `tokens create <pkg>` or `tokens revoke <pkg>`");
            const client = adminClient(v);
            const app = await appId(v);
            const pkg = await packageName(v, positionals[2]);
            if (action === "revoke") {
                const id = required(v["id"], "tokens revoke: --id is required (tinyui releases list does not show it; keep the id from tokens create)");
                await revokeToken(client, app, pkg, id);
                process.stderr.write(`token ${id} revoked\n`);
                return 0;
            }
            const channels = required(v["channels"], "tokens create: --channels is required, e.g. --channels staging,production")
                .split(",")
                .map((c) => c.trim())
                .filter((c) => c !== "")
                .map((c) => requireName("a channel", c));
            if (channels.length === 0) throw new Error("tokens create: --channels is empty");
            const issued = await createToken(client, app, pkg, channels);
            process.stderr.write(`token ${issued.id} for ${app}/${pkg} on ${issued.channels.join(", ")}; it is shown once\n`);
            process.stdout.write(`${issued.token}\n`);
            return 0;
        },
    },
    releases: {
        options: RELEASE_OPTIONS,
        run: async (v, positionals) => releases(v, positionals),
    },
};

async function releases(v: Values, positionals: string[]): Promise<number> {
    const action = positionals[1];
    const client = publishClient(v);
    const app = await appId(v);
    const pkg = await packageName(v, undefined);
    const runtimeVersion = required(v["runtime-version"], `releases ${action ?? ""}: --runtime-version is required`);
    if (action === "list") {
        const found = await listReleases(client, app, pkg, runtimeVersion);
        if (v["json"] as boolean) {
            process.stdout.write(`${JSON.stringify(found, null, 2)}\n`);
            return 0;
        }
        const at = (version: string) =>
            Object.entries(found.channels)
                .filter(([, pointer]) => pointer.version === version)
                .map(([channel, pointer]) => `${channel}(${pointer.rollout})`)
                .sort()
                .join(" ");
        const width = Math.max(7, ...found.versions.map((r) => r.version.length));
        process.stdout.write(`${"VERSION".padEnd(width)}  ${"PUBLISHED".padEnd(24)}  CHANNELS\n`);
        for (const release of found.versions) process.stdout.write(`${release.version.padEnd(width)}  ${release.publishedAt.padEnd(24)}  ${at(release.version)}\n`);
        return 0;
    }
    const target = { app, pkg, runtimeVersion, channel: "" };
    let body: { version?: string; rollout?: number };
    if (action === "rollback" || action === "promote") {
        const version = required(positionals[2], `releases ${action}: a version is required`);
        target.channel = requireName("the channel", required(action === "promote" ? v["to"] : v["channel"], `releases ${action}: ${action === "promote" ? "--to" : "--channel"} is required`));
        body = { version };
    } else if (action === "rollout") {
        target.channel = requireName("--channel", required(v["channel"], "releases rollout: --channel is required"));
        body = { rollout: Number(required(positionals[2], "releases rollout: a percentage is required")) };
    } else {
        throw new Error("releases: expected list, rollback, rollout or promote");
    }
    const pointer = await movePointer(client, target, body);
    process.stderr.write(`${app}/${pkg}/${runtimeVersion} ${target.channel} -> ${pointer.version} at rollout ${pointer.rollout}\n`);
    process.stdout.write(`${pointer.version}\n`);
    return 0;
}

function publishClient(v: Values): UpdatesClient {
    return new UpdatesClient({ url: resolveUrl(v["url"] as string | undefined), token: requireToken(TOKEN_ENV) });
}

function adminClient(v: Values): UpdatesClient {
    return new UpdatesClient({ url: resolveUrl(v["url"] as string | undefined), token: requireToken(ADMIN_TOKEN_ENV) });
}

async function appId(v: Values): Promise<string> {
    return requireName("--app", required((v["app"] as string | undefined) ?? process.env["TINYUI_APP"], "--app is required, or set $TINYUI_APP"));
}

/** The package is the one being built here unless another is named: `name` in tinyui.config.json is its identity. */
async function packageName(v: Values, positional: string | undefined): Promise<string> {
    const explicit = positional ?? (v["pkg"] as string | undefined);
    if (explicit !== undefined) return requireName("a package name", explicit);
    const config = await loadConfig(process.cwd()).catch(() => null);
    if (config) return config.name;
    return required(undefined, "--pkg is required here; there is no tinyui.config.json in the current directory to take it from");
}

function required(value: unknown, message: string): string {
    if (typeof value !== "string" || value === "") throw new Error(message);
    return value;
}

async function main(argv: string[]): Promise<number> {
    const command = COMMANDS[argv[0] ?? ""];
    if (!command || argv.includes("--help") || argv.includes("-h")) {
        process.stdout.write(USAGE);
        return argv.includes("--help") || argv.includes("-h") ? 0 : 2;
    }
    const { values, positionals } = parseArgs({ args: argv, allowPositionals: true, options: command.options });
    return command.run(values, positionals);
}

async function schema(v: { entry?: string; ts?: string; kt?: string; package?: string; object: string; check: boolean; "node-import": string }): Promise<number> {
    if (!v.entry) throw new Error("schema: --entry is required");
    if (!v.ts && !v.kt) throw new Error("schema: nothing to generate; pass --ts and/or --kt");
    if (v.kt && !v.package) throw new Error("schema: --kt needs --package");
    const components = await loadSchema(v.entry);
    const targets: { file: string; content: string }[] = [];
    if (v.ts) targets.push({ file: v.ts, content: generateTs(components, v["node-import"]) });
    if (v.kt) targets.push({ file: v.kt, content: generateKt(components, v.package!, v.object) });
    let stale = 0;
    for (const t of targets) {
        const current = await readFile(t.file, "utf8").catch(() => null);
        if (current === t.content) continue;
        stale++;
        if (v.check) {
            process.stderr.write(`tinyui: ${t.file} is out of date; run tinyui schema\n`);
        } else {
            await mkdir(dirname(t.file), { recursive: true });
            await writeFile(t.file, t.content);
            process.stdout.write(`${t.file}\n`);
        }
    }
    return v.check && stale > 0 ? 1 : 0;
}

main(process.argv.slice(2)).then(
    (code) => process.exit(code),
    (e: unknown) => {
        process.stderr.write(`tinyui: ${e instanceof Error ? e.message : String(e)}\n`);
        process.exit(1);
    },
);

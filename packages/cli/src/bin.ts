#!/usr/bin/env node
import { parseArgs, type ParseArgsConfig } from "node:util";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";
import { build } from "./build.ts";
import { bundle } from "./bundle.ts";
import { generateKeyPair } from "./keys.ts";
import { generateKt, generateTs } from "./schema/generate.ts";
import { loadSchema } from "./schema/load.ts";

const USAGE = `usage: tinyui build [--root <dir>] [--out <dir>] [--qjsc <path>] [--js-only] [--version <v>]
       tinyui bundle --runtime-version <rv> --signing-key <pem> [--dist <dir>] [--rollout <0-100>] [--out <dir>]
       tinyui keys generate [--out <pem>]
       tinyui schema --entry <schema.ts> [--ts <file>] [--kt <file> --package <pkg> [--object <Name>]] [--check]

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
`;

type Options = NonNullable<ParseArgsConfig["options"]>;

const COMMANDS: Record<string, { options: Options; run: (v: Record<string, unknown>, positionals: string[]) => Promise<number> }> = {
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
};

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

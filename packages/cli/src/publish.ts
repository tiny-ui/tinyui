import { readFile, readdir, realpath } from "node:fs/promises";
import { isAbsolute, join, relative, resolve, sep } from "node:path";
import type { Manifest } from "./build.ts";
import { isHostVersion, isPathSegment, type Pointer } from "./bundle.ts";
import { readHostSnapshot } from "./admin.ts";
import { segments, UpdatesError, type UpdatesClient, type UploadResult } from "./client.ts";
import type { PageRequires } from "./requires.ts";
import { parseHostSnapshot } from "./snapshot.ts";

export interface PublishOptions {
    client: UpdatesClient;
    /** `tinyui bundle` output: either `<out>` or the `<out>/<pkg>/<hostVersion>` inside it. */
    dir: string;
    app: string;
    channel: string;
    /** Overrides the `rollout` in `current.json`. */
    rollout?: number;
    concurrency?: number;
    onUpload?: (result: UploadResult) => void;
}

export interface PublishResult {
    pkg: string;
    hostVersion: string;
    version: string;
    uploaded: number;
    existing: number;
    pointer: Pointer;
}

/** The signed bundle of one (pkg, hostVersion), as `tinyui bundle` laid it out (docs/updates.md §1.2). */
interface LocalBundle {
    dir: string;
    pointer: Pointer;
    /** The bytes that were signed; they are uploaded unchanged (docs/updates.md §7). */
    manifestBytes: Uint8Array;
    manifest: Manifest & { hostVersion: string };
}

export async function publish(options: PublishOptions): Promise<PublishResult> {
    const { client, app, channel } = options;
    if (options.rollout !== undefined && (!Number.isInteger(options.rollout) || options.rollout < 0 || options.rollout > 100)) {
        throw new Error(`rollout must be an integer from 0 to 100, got ${options.rollout}`);
    }
    const concurrency = options.concurrency ?? 4;
    if (!Number.isInteger(concurrency) || concurrency < 1) throw new Error(`concurrency must be a positive integer, got ${options.concurrency}`);
    const local = await load(await discover(resolve(options.dir)));
    const { manifest, pointer } = local;
    const pkg = manifest.name;
    const hostVersion = manifest.hostVersion;
    const version = manifest.version;
    await checkHost(client, app, local.manifest);
    const prefix = segments(app, pkg, hostVersion, version);

    const files = [...new Set(Object.values(manifest.files).map((path) => `${path}.bin`)), "manifest.json"];
    const results: UploadResult[] = [];
    const versionDir = join(local.dir, version);
    await inParallel(files, concurrency, async (path) => {
        const bytes = path === "manifest.json" ? local.manifestBytes : await readInside(versionDir, path);
        const result = await client.upload(`${prefix}${segments(...path.split("/"))}`, bytes);
        results.push(result);
        options.onUpload?.(result);
    });

    const body = { version, signature: pointer.signature, rollout: options.rollout ?? pointer.rollout };
    const written = await client.json<Pointer>("PUT", `${segments(app, channel, pkg, hostVersion)}/current.json`, body);
    return {
        pkg,
        hostVersion,
        version,
        uploaded: results.filter((r) => !r.existing).length,
        existing: results.filter((r) => r.existing).length,
        pointer: written,
    };
}

/**
 * Refuses a package the target host version cannot run: a page using a capability or host component the host does
 * not provide, or built-ins of another tinyui. Such a page does not crash, so no rollback would catch it (docs/updates.md §1.3).
 */
async function checkHost(client: UpdatesClient, app: string, manifest: LocalBundle["manifest"]): Promise<void> {
    const target = `host version ${manifest.hostVersion} of ${app}`;
    if (typeof manifest.tinyui !== "string" || typeof manifest.requires !== "object" || manifest.requires === null) {
        throw new Error(`${manifest.name} ${manifest.version} was built without "tinyui" / "requires" in its manifest; rebuild it with a current tinyui-cli`);
    }
    const bytes = await readHostSnapshot(client, app, manifest.hostVersion).catch((e: unknown) => {
        if (e instanceof UpdatesError && e.status === 404) throw new Error(`${target} has no snapshot, so nothing says what it provides; the host's CI uploads it with tinyui hosts upload`);
        throw e;
    });
    const host = parseHostSnapshot(new TextDecoder().decode(bytes));
    if (host.hostVersion !== manifest.hostVersion) throw new Error(`the snapshot stored for ${target} says hostVersion ${host.hostVersion}`);
    const problems: string[] = [];
    if (host.tinyui !== manifest.tinyui) problems.push(`  built with tinyui ${manifest.tinyui}, the host runs tinyui ${host.tinyui}`);
    for (const [page, needs] of Object.entries(manifest.requires as Record<string, PageRequires>).sort(([a], [b]) => (a < b ? -1 : 1))) {
        const components = needs.components.filter((c) => !host.components.has(c));
        const capabilities = needs.capabilities.filter((c) => !host.capabilities.has(c));
        if (components.length) problems.push(`  ${page} uses host component${components.length > 1 ? "s" : ""} ${components.join(", ")}`);
        if (capabilities.length) problems.push(`  ${page} calls ${capabilities.join(", ")}`);
    }
    if (problems.length) throw new Error(`${manifest.name} ${manifest.version} cannot go to ${target}, which does not provide what it uses:\n${problems.join("\n")}`);
}

/** `<dir>` is either the `<pkg>/<hostVersion>` directory itself or the root `tinyui bundle --out` wrote it under. */
async function discover(dir: string): Promise<string> {
    if (await exists(join(dir, "current.json"))) return dir;
    const found: string[] = [];
    for (const pkg of await subdirectories(dir)) {
        for (const hostVersion of await subdirectories(join(dir, pkg))) {
            if (await exists(join(dir, pkg, hostVersion, "current.json"))) found.push(join(dir, pkg, hostVersion));
        }
    }
    if (found.length === 1) return found[0]!;
    if (found.length === 0) throw new Error(`no current.json under ${dir}; run tinyui bundle first, or point --dir at the bundle directory`);
    const list = found.map((f) => `  ${f}`).join("\n");
    throw new Error(`${dir} holds more than one bundle; publish one at a time by pointing --dir at it:\n${list}`);
}

async function load(dir: string): Promise<LocalBundle> {
    const pointer = JSON.parse(await readFile(join(dir, "current.json"), "utf8")) as Partial<Pointer>;
    if (typeof pointer.version !== "string" || typeof pointer.signature !== "string" || typeof pointer.rollout !== "number") {
        throw new Error(`${join(dir, "current.json")} is not a pointer file; expected version / rollout / signature`);
    }
    if (!Number.isInteger(pointer.rollout) || pointer.rollout < 0 || pointer.rollout > 100) {
        throw new Error(`${join(dir, "current.json")}: rollout must be an integer from 0 to 100, got ${pointer.rollout}`);
    }
    if (!isPathSegment(pointer.version)) throw new Error(`${join(dir, "current.json")}: "${pointer.version}" is not a single path segment`);
    const manifestFile = join(dir, pointer.version, "manifest.json");
    const manifestBytes = await readInside(join(dir, pointer.version), "manifest.json").catch((e: unknown) => {
        throw new Error(`cannot read ${manifestFile} (${(e as Error).message}); current.json points at a version that was not bundled here`);
    });
    const manifest = JSON.parse(Buffer.from(manifestBytes).toString("utf8")) as Manifest & { hostVersion?: string };
    for (const key of ["name", "version", "hostVersion"] as const) {
        if (typeof manifest[key] !== "string" || manifest[key] === "") throw new Error(`${manifestFile} has no "${key}"; rebuild with a current tinyui-cli`);
    }
    if (!isHostVersion(manifest.hostVersion!)) throw new Error(`${manifestFile}: hostVersion must be a positive integer, got "${manifest.hostVersion}"`);
    // the same three checks the server runs (§6.1), so a mismatch costs no upload
    if (manifest.version !== pointer.version) throw new Error(`${manifestFile} is version ${manifest.version}, current.json points at ${pointer.version}`);
    // these paths pick which files are read and uploaded, so they stay inside <version>/ and inside the URL charset
    if (typeof manifest.files !== "object" || manifest.files === null) throw new Error(`${manifestFile}: "files" must map module names to paths`);
    for (const [module, path] of Object.entries(manifest.files)) {
        if (typeof path !== "string" || !path.split("/").every(isPathSegment)) {
            throw new Error(`${manifestFile}: files["${module}"] = ${JSON.stringify(path)}; every segment must match [A-Za-z0-9._-]+`);
        }
    }
    return { dir, pointer: pointer as Pointer, manifestBytes, manifest: manifest as Manifest & { hostVersion: string } };
}

/** Lexical checks do not stop a symlink: a bundle from elsewhere could point at any file on the machine. */
async function readInside(versionDir: string, path: string): Promise<Uint8Array> {
    const file = join(versionDir, path);
    const real = await realpath(file);
    const root = await realpath(versionDir);
    const rel = relative(root, real);
    if (rel === "" || rel === ".." || rel.startsWith(`..${sep}`) || isAbsolute(rel)) throw new Error(`${file} leaves ${versionDir}; a bundle only publishes its own files`);
    return readFile(real);
}

async function subdirectories(dir: string): Promise<string[]> {
    const entries = await readdir(dir, { withFileTypes: true }).catch(() => {
        throw new Error(`${dir} does not exist; run tinyui bundle first`);
    });
    return entries.filter((e) => e.isDirectory()).map((e) => e.name);
}

async function exists(file: string): Promise<boolean> {
    return readFile(file).then(
        () => true,
        () => false,
    );
}

async function inParallel<T>(items: T[], limit: number, run: (item: T) => Promise<void>): Promise<void> {
    const queue = [...items];
    const workers = Array.from({ length: Math.min(limit, queue.length) }, async () => {
        for (let next = queue.shift(); next !== undefined; next = queue.shift()) await run(next);
    });
    await Promise.all(workers);
}

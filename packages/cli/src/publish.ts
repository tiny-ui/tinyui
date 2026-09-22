import { readFile, readdir } from "node:fs/promises";
import { join, resolve } from "node:path";
import type { Manifest } from "./build.ts";
import { isPathSegment, type Pointer } from "./bundle.ts";
import { segments, type UpdatesClient, type UploadResult } from "./client.ts";

export interface PublishOptions {
    client: UpdatesClient;
    /** `tinyui bundle` output: either `<out>` or the `<out>/<pkg>/<rv>` inside it. */
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
    runtimeVersion: string;
    version: string;
    uploaded: number;
    existing: number;
    pointer: Pointer;
}

/** The signed bundle of one (pkg, rv), as `tinyui bundle` laid it out (docs/updates.md §1.2). */
interface LocalBundle {
    dir: string;
    pointer: Pointer;
    /** The bytes that were signed; they are uploaded unchanged (docs/updates.md §7). */
    manifestBytes: Uint8Array;
    manifest: Manifest & { runtimeVersion: string };
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
    const rv = manifest.runtimeVersion;
    const version = manifest.version;
    const prefix = segments(app, pkg, rv, version);

    const files = [...new Set(Object.values(manifest.files).map((path) => `${path}.bin`)), "manifest.json"];
    const results: UploadResult[] = [];
    await inParallel(files, concurrency, async (path) => {
        const bytes = path === "manifest.json" ? local.manifestBytes : await readFile(join(local.dir, version, path));
        const result = await client.upload(`${prefix}${segments(...path.split("/"))}`, bytes);
        results.push(result);
        options.onUpload?.(result);
    });

    const body = { version, signature: pointer.signature, rollout: options.rollout ?? pointer.rollout };
    const written = await client.json<Pointer>("PUT", `${segments(app, channel, pkg, rv)}/current.json`, body);
    return {
        pkg,
        runtimeVersion: rv,
        version,
        uploaded: results.filter((r) => !r.existing).length,
        existing: results.filter((r) => r.existing).length,
        pointer: written,
    };
}

/** `<dir>` is either the `<pkg>/<rv>` directory itself or the root `tinyui bundle --out` wrote it under. */
async function discover(dir: string): Promise<string> {
    if (await exists(join(dir, "current.json"))) return dir;
    const found: string[] = [];
    for (const pkg of await subdirectories(dir)) {
        for (const rv of await subdirectories(join(dir, pkg))) {
            if (await exists(join(dir, pkg, rv, "current.json"))) found.push(join(dir, pkg, rv));
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
    if (!isPathSegment(pointer.version)) throw new Error(`${join(dir, "current.json")}: "${pointer.version}" is not a single path segment`);
    const manifestFile = join(dir, pointer.version, "manifest.json");
    const manifestBytes = await readFile(manifestFile).catch(() => {
        throw new Error(`${manifestFile} is missing; current.json points at a version that was not bundled here`);
    });
    const manifest = JSON.parse(Buffer.from(manifestBytes).toString("utf8")) as Manifest & { runtimeVersion?: string };
    for (const key of ["name", "version", "runtimeVersion"] as const) {
        if (typeof manifest[key] !== "string" || manifest[key] === "") throw new Error(`${manifestFile} has no "${key}"; rebuild with a current tinyui-cli`);
    }
    // the same three checks the server runs (§6.1), so a mismatch costs no upload
    if (manifest.version !== pointer.version) throw new Error(`${manifestFile} is version ${manifest.version}, current.json points at ${pointer.version}`);
    // these paths pick which files are read and uploaded, so they stay inside <version>/ and inside the URL charset
    if (typeof manifest.files !== "object" || manifest.files === null) throw new Error(`${manifestFile}: "files" must map module names to paths`);
    for (const [module, path] of Object.entries(manifest.files)) {
        if (typeof path !== "string" || !path.split("/").every(isPathSegment)) {
            throw new Error(`${manifestFile}: files["${module}"] = ${JSON.stringify(path)}; every segment must match [A-Za-z0-9._-]+`);
        }
    }
    return { dir, pointer: pointer as Pointer, manifestBytes, manifest: manifest as Manifest & { runtimeVersion: string } };
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

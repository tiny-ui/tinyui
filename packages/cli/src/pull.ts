import { createHash } from "node:crypto";
import { mkdir, mkdtemp, rename, rm, writeFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";
import { isPathSegment } from "./bundle.ts";
import { requireSecureUrl, segments } from "./client.ts";
import { verify } from "./keys.ts";

export interface PullOptions {
    /** Instance url; the delivery endpoints are public, no token. */
    url: string;
    app: string;
    channel: string;
    pkg: string;
    hostVersion: string;
    /** The key the package is signed with, from `tinyui.config.json`: what the host will trust too. */
    publicKey: string;
    /** Replaced as a whole with the package: `manifest.json`, `runtime/`, `pages/`. */
    out: string;
    /** Injected by the tests; defaults to the global `fetch`. */
    fetch?: typeof globalThis.fetch;
}

export interface PullResult {
    version: string;
    files: string[];
}

/**
 * The version a channel points at, as the host's embedded package (docs/updates.md §1.4): verified the way a
 * device verifies it, then written in the layout `tinyui build` produces. Nothing is written unless all of it checks out.
 */
export async function pull(options: PullOptions): Promise<PullResult> {
    const fetchImpl = options.fetch ?? globalThis.fetch;
    const base = requireSecureUrl(options.url).replace(/\/+$/, "") + segments(options.app, options.channel, options.pkg, options.hostVersion);
    const get = async (path: string): Promise<Uint8Array> => {
        const response = await fetchImpl(`${base}/${path}`);
        if (!response.ok) throw new Error(`GET ${base}/${path}: ${response.status}`);
        return new Uint8Array(await response.arrayBuffer());
    };

    const pointer = JSON.parse(new TextDecoder().decode(await get("current.json"))) as { version?: unknown; signature?: unknown };
    const version = pointer.version;
    if (typeof version !== "string" || !isPathSegment(version)) throw new Error(`current.json: version ${JSON.stringify(version)} is not a path segment`);
    if (typeof pointer.signature !== "string") throw new Error("current.json has no signature");

    const manifestBytes = await get(`${version}/manifest.json`);
    if (!verify(options.publicKey, manifestBytes, pointer.signature)) {
        throw new Error(`${version}/manifest.json does not verify against the publicKey in tinyui.config.json`);
    }
    const manifest = JSON.parse(new TextDecoder().decode(manifestBytes)) as {
        name: string;
        version: string;
        hostVersion?: string;
        runtime: string[];
        pages: string[];
        files: Record<string, string>;
        hashes: Record<string, string>;
    };
    if (manifest.name !== options.pkg) throw new Error(`the manifest is for package ${manifest.name}, not ${options.pkg}`);
    if (manifest.version !== version) throw new Error(`the manifest says version ${manifest.version}, the pointer ${version}`);
    if (manifest.hostVersion !== options.hostVersion) throw new Error(`the manifest targets host version ${manifest.hostVersion}, not ${options.hostVersion}`);

    const files = new Map<string, Uint8Array>();
    for (const module of [...manifest.runtime, ...manifest.pages]) {
        const path = `${manifest.files[module]}.bin`;
        if (!path.split("/").every(isPathSegment)) throw new Error(`manifest.files[${module}] is not a relative path: ${path}`);
        const bytes = await get(`${version}/${path}`);
        if (createHash("sha256").update(bytes).digest("hex") !== manifest.hashes[module]) throw new Error(`${path} does not match manifest.hashes`);
        files.set(path, bytes);
    }
    files.set("manifest.json", manifestBytes);

    // assembled beside the target and swapped in: a failure at any step leaves the previous package in place
    const out = resolve(options.out);
    await mkdir(dirname(out), { recursive: true });
    const staging = await mkdtemp(`${out}.pulling-`);
    const backup = `${staging}.previous`;
    let moved = false;
    try {
        for (const [path, bytes] of files) {
            await mkdir(dirname(join(staging, path)), { recursive: true });
            await writeFile(join(staging, path), bytes);
        }
        moved = await rename(out, backup).then(() => true, (e: NodeJS.ErrnoException) => {
            if (e.code === "ENOENT") return false;
            throw e;
        });
        await rename(staging, out);
    } catch (e) {
        if (moved) await rename(backup, out).catch(() => {});
        await rm(staging, { recursive: true, force: true });
        throw e;
    }
    // the new package is in place; a backup that will not go away is litter, not a failure
    await rm(backup, { recursive: true, force: true }).catch(() => {});
    return { version, files: [...files.keys()].sort() };
}

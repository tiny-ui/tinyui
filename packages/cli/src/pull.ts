import { createHash } from "node:crypto";
import { mkdir, mkdtemp, readFile, rename, rm, writeFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";
import { isPathSegment } from "./bundle.ts";
import { requireSecureUrl, segments } from "./client.ts";
import { payload, requirePackagePath } from "./payload.ts";
import { verify } from "./keys.ts";

export interface PullOptions {
    /** Instance url; the delivery endpoints are public, no token. */
    url: string;
    app: string;
    channel: string;
    hostVersion: string;
    /** The host's embedded package directory: its `manifest.json` names the package and the key to trust; replaced as a whole. */
    out: string;
    /** Only when [out] has no package yet. */
    pkg?: string;
    /** A key to trust instead of the embedded package's: the first pull, or a rotation someone checked. */
    acceptKey?: string;
    /** Injected by the tests; defaults to the global `fetch`. */
    fetch?: typeof globalThis.fetch;
}

export interface PullResult {
    /** The embedded version after the pull. */
    version: string;
    changed: boolean;
    /** Why nothing changed. */
    reason?: string;
}

interface Manifest {
    name: string;
    publicKey: string;
    version: string;
    createdAt: string;
    hostVersion?: string;
    pages: string[];
    files: Record<string, string>;
    hashes: Record<string, string>;
    i18n?: { default: string; files: Record<string, string> };
}

/**
 * Refreshes a host's embedded package to what [PullOptions.channel] points at (docs/updates.md §1.4), verified the
 * way a device verifies it against the key the host already embeds. Keeps what is there when the channel is mid-rollout
 * or not newer; writes nothing unless all of it checks out.
 */
export async function pull(options: PullOptions): Promise<PullResult> {
    const out = resolve(options.out);
    const current = await readManifest(join(out, "manifest.json"));
    const pkg = options.pkg ?? current?.name;
    if (!pkg) throw new Error(`${out} holds no package yet: pass --pkg and --accept-key <publicKey> for the first pull`);
    if (current && current.name !== pkg) throw new Error(`${out} holds package ${current.name}, not ${pkg}`);
    const trusted = options.acceptKey ?? current?.publicKey;
    if (!trusted) throw new Error(`${out} holds no package yet: pass --accept-key <publicKey> for the first pull`);

    const fetchImpl = options.fetch ?? globalThis.fetch;
    const base = requireSecureUrl(options.url).replace(/\/+$/, "") + segments(options.app, options.channel, pkg, options.hostVersion);
    const get = async (path: string): Promise<Uint8Array> => {
        const response = await fetchImpl(`${base}/${path}`);
        if (!response.ok) throw new Error(`GET ${base}/${path}: ${response.status}`);
        return new Uint8Array(await response.arrayBuffer());
    };

    const pointer = JSON.parse(new TextDecoder().decode(await get("current.json"))) as { version?: unknown; rollout?: unknown; signature?: unknown };
    const version = pointer.version;
    if (typeof version !== "string" || !isPathSegment(version)) throw new Error(`current.json: version ${JSON.stringify(version)} is not a path segment`);
    if (typeof pointer.signature !== "string") throw new Error("current.json has no signature");
    const keep = (reason: string): PullResult => ({ version: current?.version ?? "", changed: false, reason });
    // a version still rolling out is not yet what every user runs: it must not become the floor of every new install
    const rollout = typeof pointer.rollout === "number" ? pointer.rollout : 100;
    if (rollout < 100) {
        if (current?.hostVersion !== options.hostVersion) {
            throw new Error(`${options.channel} is at ${version} rolling out to ${rollout}%, and ${out} holds nothing for host version ${options.hostVersion} to keep: wait for 100%`);
        }
        return keep(`${options.channel} is at ${version} rolling out to ${rollout}%`);
    }
    if (current?.version === version && current.hostVersion === options.hostVersion) return keep(`already ${version}`);

    const manifestBytes = await get(`${version}/manifest.json`);
    const manifest = JSON.parse(new TextDecoder().decode(manifestBytes)) as Manifest;
    if (!verify(trusted, manifestBytes, pointer.signature) || manifest.publicKey !== trusted) {
        throw new Error(`${version} is not signed with the trusted key ${trusted}` + (manifest.publicKey !== trusted ? `; it names ${manifest.publicKey}: after checking the rotation, pass --accept-key ${manifest.publicKey}` : ""));
    }
    if (manifest.name !== pkg) throw new Error(`the manifest is for package ${manifest.name}, not ${pkg}`);
    if (manifest.version !== version) throw new Error(`the manifest says version ${manifest.version}, the pointer ${version}`);
    if (manifest.hostVersion !== options.hostVersion) throw new Error(`the manifest targets host version ${manifest.hostVersion}, not ${options.hostVersion}`);
    if (current?.hostVersion === options.hostVersion && manifest.createdAt <= current.createdAt) return keep(`embedded ${current.version} is not older than ${version}`);

    const files = new Map<string, Uint8Array>();
    for (const { path, hashKey } of payload(manifest)) {
        requirePackagePath(path);
        const bytes = await get(`${version}/${path}`);
        if (createHash("sha256").update(bytes).digest("hex") !== manifest.hashes[hashKey]) throw new Error(`${path} does not match manifest.hashes`);
        files.set(path, bytes);
    }
    files.set("manifest.json", manifestBytes);
    await replaceDir(out, files);
    return { version, changed: true };
}

async function readManifest(file: string): Promise<Manifest | null> {
    let text: string;
    try {
        text = await readFile(file, "utf8");
    } catch {
        return null;
    }
    return JSON.parse(text) as Manifest;
}

/** Assembled beside [out] and swapped in: a failure at any step leaves the previous package in place. */
async function replaceDir(out: string, files: Map<string, Uint8Array>): Promise<void> {
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
}

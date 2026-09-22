import { createHash } from "node:crypto";
import { copyFile, mkdir, readFile, rm, writeFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";
import type { Manifest } from "./build.ts";
import { publicKeyOf, sign } from "./keys.ts";

export interface BundleOptions {
    /** `tinyui build` output directory. */
    dist: string;
    /** The host's declared runtimeVersion; becomes a path segment and a signed manifest field. */
    runtimeVersion: string;
    /** PKCS#8 PEM whose public key must equal the manifest's `publicKey`. */
    signingKey: string;
    /** 0–100, defaults to 100. */
    rollout?: number;
    /** Output root; defaults to `<dist>/ota`. */
    out?: string;
}

export interface BundleResult {
    /** `<out>/<pkg>/<rv>`: upload it with `tinyui publish` or serve it as is (docs/updates.md §1.2). */
    dir: string;
    version: string;
    pointer: string;
    manifest: string;
}

/** Pointer file `current.json`: the only mutable file, nothing in it needs signature protection (docs/updates.md §1.2). */
export interface Pointer {
    version: string;
    rollout: number;
    /** DER-encoded ECDSA signature over the raw bytes of `<version>/manifest.json`, base64. */
    signature: string;
}

const RUNTIME_VERSION = /^[A-Za-z0-9._-]+$/;

export async function bundle(options: BundleOptions): Promise<BundleResult> {
    const dist = resolve(options.dist);
    const out = resolve(options.out ?? join(dist, "ota"));
    const rollout = options.rollout ?? 100;
    if (!Number.isInteger(rollout) || rollout < 0 || rollout > 100) throw new Error(`rollout must be an integer from 0 to 100, got ${options.rollout}`);
    if (!RUNTIME_VERSION.test(options.runtimeVersion)) throw new Error(`runtime version must match ${RUNTIME_VERSION}, got "${options.runtimeVersion}"`);

    const manifest = await readManifest(dist);
    const privateKeyPem = await readFile(options.signingKey, "utf8");
    if (publicKeyOf(privateKeyPem) !== manifest.publicKey) {
        throw new Error(`${options.signingKey} does not match the publicKey in ${dist}/manifest.json (tinyui.config.json)`);
    }
    if (!manifest.engine) throw new Error(`${dist} has no bytecode (built with --js-only); a package needs .bin files`);

    const dir = join(out, manifest.name, options.runtimeVersion);
    const versionDir = join(dir, manifest.version);
    await rm(versionDir, { recursive: true, force: true });
    for (const [module, path] of Object.entries(manifest.files)) {
        const source = join(dist, path + ".bin");
        const expected = manifest.hashes[module];
        const actual = createHash("sha256").update(await readFile(source)).digest("hex");
        if (actual !== expected) throw new Error(`${source} does not match manifest.hashes (${actual} vs ${expected}); rerun tinyui build`);
        const target = join(versionDir, path + ".bin");
        await mkdir(dirname(target), { recursive: true });
        await copyFile(source, target);
    }

    // written once, signed as written: every side verifies these exact bytes (docs/updates.md §7)
    const signed = Buffer.from(JSON.stringify({ ...manifest, runtimeVersion: options.runtimeVersion }, null, 2) + "\n");
    const manifestFile = join(versionDir, "manifest.json");
    await writeFile(manifestFile, signed);
    const pointer: Pointer = { version: manifest.version, rollout, signature: sign(privateKeyPem, signed) };
    const pointerFile = join(dir, "current.json");
    await writeFile(pointerFile, JSON.stringify(pointer, null, 2) + "\n");
    return { dir, version: manifest.version, pointer: pointerFile, manifest: manifestFile };
}

async function readManifest(dist: string): Promise<Manifest> {
    const file = join(dist, "manifest.json");
    let raw: Partial<Manifest>;
    try {
        raw = JSON.parse(await readFile(file, "utf8")) as Partial<Manifest>;
    } catch (e) {
        throw new Error(`cannot read ${file}: ${(e as Error).message}`);
    }
    for (const key of ["name", "publicKey", "version", "createdAt", "files", "hashes"] as const) {
        if (raw[key] === undefined) throw new Error(`${file} has no "${key}"; rebuild with a current tinyui-cli`);
    }
    return raw as Manifest;
}

import { createHash, randomBytes } from "node:crypto";
import { copyFile, mkdir, readFile, rename, rm, writeFile } from "node:fs/promises";
import { dirname, isAbsolute, join, relative, resolve, sep } from "node:path";
import type { Manifest } from "./build.ts";
import { isPackageName } from "./config.ts";
import { isPublicKey, publicKeyOf, sign } from "./keys.ts";

export interface BundleOptions {
    /** `tinyui build` output directory. */
    dist: string;
    /** What the host declares it provides to pages (docs/updates.md §4.1); becomes a path segment and a signed manifest field. */
    hostVersion: string;
    /** PKCS#8 PEM whose public key must equal the manifest's `publicKey`. */
    signingKey: string;
    /** 0–100, defaults to 100. */
    rollout?: number;
    /** Output root; defaults to `<dist>/ota`. */
    out?: string;
}

export interface BundleResult {
    /** `<out>/<pkg>/<hostVersion>`: upload it with `tinyui publish` or serve it as is (docs/updates.md §1.2). */
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

/** `version` becomes a directory name; one segment, never `.` or `..`. */
export function isPathSegment(value: string): boolean {
    return /^[A-Za-z0-9._-]+$/.test(value) && value !== "." && value !== "..";
}

/** A positive integer, so an App version such as `1.2.0` cannot be mistaken for a host version. */
export function isHostVersion(value: string): boolean {
    return /^[1-9][0-9]*$/.test(value);
}

export async function bundle(options: BundleOptions): Promise<BundleResult> {
    const dist = resolve(options.dist);
    const out = resolve(options.out ?? join(dist, "ota"));
    const rollout = options.rollout ?? 100;
    if (!Number.isInteger(rollout) || rollout < 0 || rollout > 100) throw new Error(`rollout must be an integer from 0 to 100, got ${options.rollout}`);
    if (!isHostVersion(options.hostVersion)) throw new Error(`host version must be a positive integer, got "${options.hostVersion}"; it counts what the host provides to pages, it is not the App version`);

    const manifest = await readManifest(dist);
    if (!isPathSegment(manifest.version)) throw new Error(`manifest version must be a single path segment, got "${manifest.version}"`);
    if (!isPackageName(manifest.name)) throw new Error(`manifest name must match [a-z0-9-]+, got "${manifest.name}"`);
    if (!isPublicKey(manifest.publicKey)) throw new Error(`manifest publicKey is not a P-256 uncompressed point in base64`);
    const privateKeyPem = await readFile(options.signingKey, "utf8");
    if (publicKeyOf(privateKeyPem) !== manifest.publicKey) {
        throw new Error(`${options.signingKey} does not match the publicKey in ${dist}/manifest.json (tinyui.config.json)`);
    }
    if (!manifest.engine) throw new Error(`${dist} has no bytecode (built with --js-only); a package needs .bin files`);

    // everything is checked before anything is written
    const files: { source: string; target: string }[] = [];
    for (const [module, path] of Object.entries(manifest.files)) {
        const source = resolve(dist, path + ".bin");
        if (!within(dist, source)) throw new Error(`manifest.files["${module}"] points outside ${dist}: ${path}`);
        // an output path is a URL path once the package is served; tinyui build refuses these at the source
        if (!path.split("/").every(isPathSegment)) throw new Error(`manifest.files["${module}"] = "${path}": every segment must match [A-Za-z0-9._-]+ to survive the delivery URL`);
        const expected = manifest.hashes[module];
        const actual = createHash("sha256").update(await readFile(source)).digest("hex");
        if (actual !== expected) throw new Error(`${source} does not match manifest.hashes (${actual} vs ${expected}); rerun tinyui build`);
        files.push({ source, target: path + ".bin" });
    }

    // written once, signed as written: every side verifies these exact bytes (docs/updates.md §7)
    const signed = Buffer.from(JSON.stringify({ ...manifest, hostVersion: options.hostVersion }, null, 2) + "\n");
    const dir = join(out, manifest.name, options.hostVersion);
    const versionDir = join(dir, manifest.version);
    const manifestFile = join(versionDir, "manifest.json");
    const existing = await readFile(manifestFile).catch(() => null);
    if (existing && !existing.equals(signed)) {
        throw new Error(`${versionDir} already holds a different build; a version is immutable, build again with a new version`);
    }
    // a version directory appears complete or not at all: staged next to it, renamed into place; the pointer likewise
    if (!existing) {
        const staging = `${versionDir}.${randomBytes(4).toString("hex")}.tmp`;
        for (const { source, target } of files) {
            const file = join(staging, target);
            await mkdir(dirname(file), { recursive: true });
            await copyFile(source, file);
        }
        await writeFile(join(staging, "manifest.json"), signed);
        try {
            await rename(staging, versionDir);
        } catch (e) {
            // the directory appeared meanwhile: never replace it, only accept it if it is this same build
            await rm(staging, { recursive: true, force: true });
            const now = await readFile(manifestFile).catch(() => null);
            if (!now) throw new Error(`${versionDir} exists without a manifest.json; remove it and run again`, { cause: e });
            if (!now.equals(signed)) throw new Error(`${versionDir} already holds a different build; a version is immutable, build again with a new version`);
        }
    }
    const pointer: Pointer = { version: manifest.version, rollout, signature: sign(privateKeyPem, signed) };
    const pointerFile = join(dir, "current.json");
    const pointerStaging = `${pointerFile}.${randomBytes(4).toString("hex")}.tmp`;
    await writeFile(pointerStaging, JSON.stringify(pointer, null, 2) + "\n");
    await rename(pointerStaging, pointerFile);
    return { dir, version: manifest.version, pointer: pointerFile, manifest: manifestFile };
}

function within(root: string, file: string): boolean {
    const rel = relative(root, file);
    return rel !== "" && rel !== ".." && !rel.startsWith(`..${sep}`) && !isAbsolute(rel);
}

async function readManifest(dist: string): Promise<Manifest> {
    const file = join(dist, "manifest.json");
    let raw: Partial<Manifest>;
    try {
        raw = JSON.parse(await readFile(file, "utf8")) as Partial<Manifest>;
    } catch (e) {
        throw new Error(`cannot read ${file}: ${(e as Error).message}`);
    }
    for (const key of ["name", "publicKey", "version", "createdAt", "engine", "tinyui"] as const) {
        if (typeof raw[key] !== "string") throw new Error(`${file} has no "${key}"; rebuild with a current tinyui-cli`);
    }
    const names = (key: "runtime" | "pages") => {
        const list = raw[key];
        if (!Array.isArray(list) || !list.every((m) => typeof m === "string")) throw new Error(`${file}: "${key}" must list module names`);
        return list as string[];
    };
    const table = (key: "files" | "hashes" | "buildIds") => {
        const map = raw[key];
        if (typeof map !== "object" || map === null || !Object.values(map).every((v) => typeof v === "string")) throw new Error(`${file}: "${key}" must map module names to strings`);
        return map as Record<string, string>;
    };
    const modules = [...names("runtime"), ...names("pages")];
    if (new Set(modules).size !== modules.length) throw new Error(`${file}: a module is listed twice`);
    for (const key of ["files", "hashes", "buildIds"] as const) {
        const keys = Object.keys(table(key)).sort();
        if (keys.join("\n") !== [...modules].sort().join("\n")) throw new Error(`${file}: "${key}" does not cover exactly the modules in "runtime" and "pages"`);
    }
    return raw as Manifest;
}

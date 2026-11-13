import { mkdir, mkdtemp, rename, rm, writeFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import { isPathSegment } from "./bundle.ts";

/** The part of a manifest that says which files a package carries besides `manifest.json` (docs/updates.md §1.1). */
export interface PayloadManifest {
    pages: string[];
    files: Record<string, string>;
    hashes: Record<string, string>;
    i18n?: { default: string; files: Record<string, string> };
}

/** Every file of the package: its path inside the package, and its key into `hashes`. */
export function payload(manifest: PayloadManifest): { path: string; hashKey: string }[] {
    return [
        ...manifest.pages.map((module) => ({ path: `${manifest.files[module]}.jsb`, hashKey: module })),
        ...Object.values(manifest.i18n?.files ?? {}).map((path) => ({ path, hashKey: path })),
    ];
}

/** A path inside a package is a URL path once served: plain relative segments only. */
export function requirePackagePath(path: string): string {
    if (!path.split("/").every(isPathSegment)) throw new Error(`"${path}": every segment must match [A-Za-z0-9._-]+ to survive the delivery URL`);
    return path;
}

/**
 * Makes [out] hold exactly [files], a host's embedded package directory (`tinyui pull`, `tinyui embed`). Assembled
 * beside [out] and swapped in: a failure at any step leaves the previous package in place.
 */
export async function replaceDir(out: string, files: Map<string, Uint8Array>): Promise<void> {
    await mkdir(dirname(out), { recursive: true });
    const staging = await mkdtemp(`${out}.incoming-`);
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

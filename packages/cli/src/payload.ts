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
        ...manifest.pages.map((module) => ({ path: `${manifest.files[module]}.bin`, hashKey: module })),
        ...Object.values(manifest.i18n?.files ?? {}).map((path) => ({ path, hashKey: path })),
    ];
}

/** A path inside a package is a URL path once served: plain relative segments only. */
export function requirePackagePath(path: string): string {
    if (!path.split("/").every(isPathSegment)) throw new Error(`"${path}": every segment must match [A-Za-z0-9._-]+ to survive the delivery URL`);
    return path;
}

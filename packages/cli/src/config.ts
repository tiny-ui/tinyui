import { readFile } from "node:fs/promises";
import { join } from "node:path";
import { isPublicKey } from "./keys.ts";

export const CONFIG_FILE = "tinyui.config.json";

/** `tinyui.config.json` at the project root (docs/build-chain.md §2). */
export interface TinyUIConfig {
    /** Package name: module name prefix, delivery path segment, signing key granularity (docs/updates.md §0). */
    name: string;
    /** Signature verification key, X9.63 uncompressed point base64 (docs/updates.md §7). */
    publicKey: string;
    /** Pages directory relative to the root; defaults to `src/pages`. */
    pages: string;
}

/** Package name: module name prefix and a path segment on the server and on disk (docs/updates.md §0). */
export function isPackageName(name: string): boolean {
    return /^[a-z0-9-]+$/.test(name);
}

export async function loadConfig(root: string): Promise<TinyUIConfig> {
    const file = join(root, CONFIG_FILE);
    let text: string;
    try {
        text = await readFile(file, "utf8");
    } catch {
        throw new Error(`${CONFIG_FILE} not found in ${root}`);
    }
    let raw: unknown;
    try {
        raw = JSON.parse(text);
    } catch (e) {
        throw new Error(`${file}: ${(e as Error).message}`);
    }
    if (typeof raw !== "object" || raw === null) throw new Error(`${file}: expected an object`);
    const { name, publicKey, pages } = raw as Record<string, unknown>;
    if (typeof name !== "string" || !isPackageName(name)) throw new Error(`${file}: "name" must match [a-z0-9-]+`);
    if (typeof publicKey !== "string" || !isPublicKey(publicKey)) {
        throw new Error(`${file}: "publicKey" must be a P-256 uncompressed point in base64 (65 bytes, 04-prefixed); tinyui keys generate prints one`);
    }
    if (pages !== undefined && (typeof pages !== "string" || pages === "")) throw new Error(`${file}: "pages" must be a directory path`);
    return { name, publicKey, pages: pages ?? "src/pages" };
}

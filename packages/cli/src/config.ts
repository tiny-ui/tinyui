import { readFile } from "node:fs/promises";
import { isAbsolute, join, relative, resolve, sep } from "node:path";
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
    /** Directory of `<locale>.json` string files relative to the root; defaults to `i18n` (docs/build-chain.md §8). */
    i18n: string;
    /** The language the others are checked against and fall back to; required once there are strings. */
    defaultLocale?: string;
    /** The file `tinyui routes --schema` writes, relative to the root; `tinyui build` puts it into the manifest (docs/build-chain.md §10). Unset, the package has no contracts. */
    contracts?: string;
    /** The host App this package is published to, as the updates instance knows it; the default for `--app` (docs/updates.md §6). */
    app?: string;
    /**
     * The host version this package is written for (docs/updates.md §4.1): what `bundle` publishes to, what `releases` look at,
     * and which host snapshot, [HOST_SNAPSHOTS]`/<hostVersion>.txt`, `routes` types native routes from. Following the host is this one number.
     */
    hostVersion?: string;
    /** The updates instance this package is published to; the default for `--url` when `$TINYUI_UPDATES_URL` is unset. */
    updatesUrl?: string;
}

/** Where a page project keeps the snapshots of the host it is written for, one `<hostVersion>.txt` each (`tinyui hosts download`). */
export const HOST_SNAPSHOTS = "tinyui-host";

/** A positive integer, so an App version such as `1.2.0` cannot be mistaken for a host version. */
export function isHostVersion(value: string): boolean {
    return /^[1-9][0-9]*$/.test(value);
}

/** Package name: module name prefix and a path segment on the server and on disk (docs/updates.md §0). */
export function isPackageName(name: string): boolean {
    return /^[a-z0-9-]+$/.test(name);
}

/** App, channel and package names share this rule; a value that breaks it would also break out of its path segment. */
export function requireName(kind: string, value: string): string {
    if (!isPackageName(value)) throw new Error(`${kind} must match [a-z0-9-]+, got "${value}"`);
    return value;
}

function insideRoot(root: string, dir: string): boolean {
    if (dir === "" || isAbsolute(dir)) return false;
    const rel = relative(root, resolve(root, dir));
    return rel !== "" && rel !== ".." && !rel.startsWith(`..${sep}`) && !isAbsolute(rel);
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
    const { name, publicKey, pages, i18n, defaultLocale, contracts, app, hostVersion, updatesUrl } = raw as Record<string, unknown>;
    if (typeof name !== "string" || !isPackageName(name)) throw new Error(`${file}: "name" must match [a-z0-9-]+`);
    if (typeof publicKey !== "string" || !isPublicKey(publicKey)) {
        throw new Error(`${file}: "publicKey" must be a P-256 uncompressed point in base64 (65 bytes, 04-prefixed); tinyui keys generate prints one`);
    }
    if (pages !== undefined && (typeof pages !== "string" || !insideRoot(root, pages))) {
        throw new Error(`${file}: "pages" must be a directory inside the project root`);
    }
    if (i18n !== undefined && (typeof i18n !== "string" || !insideRoot(root, i18n))) {
        throw new Error(`${file}: "i18n" must be a directory inside the project root`);
    }
    if (defaultLocale !== undefined && (typeof defaultLocale !== "string" || !/^[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})*$/.test(defaultLocale))) {
        throw new Error(`${file}: "defaultLocale" must be a BCP 47 tag such as "en"`);
    }
    if (contracts !== undefined && (typeof contracts !== "string" || !insideRoot(root, contracts))) {
        throw new Error(`${file}: "contracts" must be a file inside the project root`);
    }
    if (app !== undefined && (typeof app !== "string" || !isPackageName(app))) throw new Error(`${file}: "app" must match [a-z0-9-]+`);
    if (hostVersion !== undefined && (typeof hostVersion !== "string" || !isHostVersion(hostVersion))) {
        throw new Error(`${file}: "hostVersion" must be a positive integer in a string such as "3"; it counts what the host provides to pages, it is not the App version`);
    }
    if (updatesUrl !== undefined && typeof updatesUrl !== "string") throw new Error(`${file}: "updatesUrl" must be a string`);
    return {
        name, publicKey, pages: pages ?? "src/pages", i18n: i18n ?? "i18n",
        ...(defaultLocale !== undefined && { defaultLocale }), ...(contracts !== undefined && { contracts }),
        ...(app !== undefined && { app }), ...(hostVersion !== undefined && { hostVersion }), ...(updatesUrl !== undefined && { updatesUrl }),
    };
}

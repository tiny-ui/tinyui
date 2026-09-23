// Thin client over the publishing and management endpoints of docs/updates.md §6.

/** The hosted instance; `--url` or `TINYUI_UPDATES_URL` point elsewhere for a self-hosted one. */
export const DEFAULT_URL = "https://updates.tinyui.app";

export const URL_ENV = "TINYUI_UPDATES_URL";
export const TOKEN_ENV = "TINYUI_TOKEN";
export const ADMIN_TOKEN_ENV = "TINYUI_ADMIN_TOKEN";

export interface ClientOptions {
    url: string;
    token: string;
    /** Injected by the tests; defaults to the global `fetch`. */
    fetch?: typeof globalThis.fetch;
}

export interface UploadResult {
    path: string;
    sha256: string;
    /** The server already had these exact bytes: a version is immutable, re-publishing is free. */
    existing: boolean;
}

export class UpdatesError extends Error {
    readonly status: number;
    readonly method: string;
    readonly path: string;

    constructor(status: number, method: string, path: string, detail: string) {
        super(`${method} ${path}: ${status} ${detail}`);
        this.status = status;
        this.method = method;
        this.path = path;
    }
}

/** Loopback is the one place a token may travel without TLS: `wrangler dev` has no certificate. */
const LOOPBACK = new Set(["localhost", "127.0.0.1", "::1", "[::1]"]);

export class UpdatesClient {
    readonly url: string;
    readonly #token: string;
    readonly #fetch: typeof globalThis.fetch;

    constructor(options: ClientOptions) {
        this.url = requireSecureUrl(options.url).replace(/\/+$/, "");
        this.#token = options.token;
        this.#fetch = options.fetch ?? globalThis.fetch;
    }

    async json<T>(method: string, path: string, body?: unknown): Promise<T> {
        const response = await this.#send(method, path, body === undefined ? undefined : JSON.stringify(body), "application/json");
        return (await response.json()) as T;
    }

    async empty(method: string, path: string): Promise<void> {
        await this.#send(method, path, undefined, undefined);
    }

    /** One file of a version. A Uint8Array body sets content-length, which the server's size check reads. */
    /** Raw bytes back, for documents such as host snapshots that are compared byte for byte. */
    async bytes(path: string): Promise<Uint8Array> {
        const response = await this.#send("GET", path, undefined, undefined);
        return new Uint8Array(await response.arrayBuffer());
    }

    async upload(path: string, bytes: Uint8Array): Promise<UploadResult> {
        return this.putBytes<UploadResult>(path, bytes);
    }

    async putBytes<T>(path: string, bytes: Uint8Array): Promise<T> {
        const response = await this.#send("PUT", path, bytes, "application/octet-stream");
        return (await response.json()) as T;
    }

    async #send(method: string, path: string, body: string | Uint8Array | undefined, contentType: string | undefined): Promise<Response> {
        const response = await this.#fetch(`${this.url}${path}`, {
            method,
            headers: {
                authorization: `Bearer ${this.#token}`,
                ...(contentType !== undefined && { "content-type": contentType }),
            },
            ...(body !== undefined && { body }),
        });
        if (!response.ok) throw new UpdatesError(response.status, method, path, await detail(response));
        return response;
    }
}

export function resolveUrl(explicit: string | undefined): string {
    return requireSecureUrl(explicit ?? process.env[URL_ENV] ?? DEFAULT_URL);
}

export function requireSecureUrl(url: string): string {
    let parsed: URL;
    try {
        parsed = new URL(url);
    } catch {
        throw new Error(`not an instance url: ${url}`);
    }
    if (parsed.protocol === "https:" || (parsed.protocol === "http:" && LOOPBACK.has(parsed.hostname))) return url;
    throw new Error(`${url} would carry the token in cleartext; use https, or http on localhost only`);
}

/** Credentials never come from the command line: they would land in shell history and in `ps` output. */
export function requireToken(variable: typeof TOKEN_ENV | typeof ADMIN_TOKEN_ENV): string {
    const token = process.env[variable];
    if (!token) throw new Error(`${variable} is not set; this command needs it (tinyui tokens create issues publish tokens)`);
    return token;
}

async function detail(response: Response): Promise<string> {
    const text = await response.text().catch(() => "");
    try {
        const parsed: unknown = JSON.parse(text);
        if (typeof parsed === "object" && parsed !== null && typeof (parsed as { error?: unknown }).error === "string") {
            return (parsed as { error: string }).error;
        }
    } catch {
        // not JSON: the body itself is the best detail there is
    }
    return text === "" ? response.statusText : text;
}

/**
 * A request path from raw values. Encoding alone is not enough: `encodeURIComponent` leaves `.` and `..`
 * intact and fetch then normalises `/../` away, which would silently address another route.
 */
export function segments(...parts: string[]): string {
    for (const part of parts) {
        if (part === "" || part === "." || part === "..") throw new Error(`"${part}" cannot be a path segment`);
    }
    return `/${parts.map(encodeURIComponent).join("/")}`;
}

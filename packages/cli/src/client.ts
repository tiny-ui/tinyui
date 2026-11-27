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
    /** Told each time a request that got no answer is about to be sent again. */
    onRetry?: (message: string) => void;
    /** How long to wait before each further attempt; its length is how many there are. Injected by the tests. */
    retryDelaysMs?: readonly number[];
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

/**
 * Sent again when no answer came back (a dropped connection, a timeout below HTTP): repeating them changes nothing —
 * the same bytes to the same path, the pointer at the same version. POST is not: it issues a token or creates an app,
 * and the first attempt may have reached the server. Neither is DELETE, which answers 404 the second time.
 */
const REPEATABLE = new Set(["GET", "PUT"]);

/** A publish is tens of requests in a row; one dropped connection should not cost all of them. */
const RETRY_DELAYS_MS: readonly number[] = [500, 2000];

/** Loopback is the one place a token may travel without TLS: `wrangler dev` has no certificate. */
const LOOPBACK = new Set(["localhost", "127.0.0.1", "::1", "[::1]"]);

export class UpdatesClient {
    readonly url: string;
    readonly #token: string;
    readonly #fetch: typeof globalThis.fetch;
    readonly #onRetry: ((message: string) => void) | undefined;
    readonly #retryDelaysMs: readonly number[];

    constructor(options: ClientOptions) {
        this.url = requireSecureUrl(options.url).replace(/\/+$/, "");
        this.#token = options.token;
        this.#fetch = options.fetch ?? globalThis.fetch;
        this.#onRetry = options.onRetry;
        this.#retryDelaysMs = options.retryDelaysMs ?? RETRY_DELAYS_MS;
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
        const response = await this.#answered(method, path, {
            method,
            headers: {
                authorization: `Bearer ${this.#token}`,
                ...(contentType !== undefined && { "content-type": contentType }),
            },
            ...(body !== undefined && { body }),
        });
        // an answer is final, whatever its status: the server refused, and asking again would be refused again
        if (!response.ok) throw new UpdatesError(response.status, method, path, await detail(response));
        return response;
    }

    /** The response, sending a repeatable request again while none comes back. */
    async #answered(method: string, path: string, init: RequestInit): Promise<Response> {
        const delays = REPEATABLE.has(method) ? this.#retryDelaysMs : [];
        for (let attempt = 0; ; attempt++) {
            try {
                return await this.#fetch(`${this.url}${path}`, init);
            } catch (e) {
                const failure = `${method} ${path}: ${unanswered(e)}`;
                const delay = delays[attempt];
                if (delay === undefined) {
                    throw new Error(attempt === 0 ? failure : `${failure}, after ${attempt + 1} attempts`, { cause: e });
                }
                this.#onRetry?.(`${failure}; trying again (${attempt + 2} of ${delays.length + 1})`);
                await new Promise((resolve) => setTimeout(resolve, delay));
            }
        }
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

/** Node's fetch says only "fetch failed"; what went wrong is on `cause` (ECONNRESET, UND_ERR_SOCKET, …). */
function unanswered(e: unknown): string {
    const message = e instanceof Error ? e.message : String(e);
    const cause: unknown = e instanceof Error ? e.cause : undefined;
    const why = typeof cause === "object" && cause !== null ? ((cause as { code?: unknown }).code ?? (cause as { message?: unknown }).message) : undefined;
    return typeof why === "string" && why !== "" ? `${message} (${why})` : message;
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

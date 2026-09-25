// docs/native-api.md §6: J3 `http.request` through a channel; non-2xx rejects E_HTTP with status, headers and body.
import { internal } from "tinyui-core";

export interface HttpOptions {
    headers?: Record<string, string>;
    /** ms; rejects with E_TIMEOUT */
    timeout?: number;
}

export interface HttpResponse<T = unknown> {
    status: number;
    headers: Record<string, string>;
    /** parsed JSON body, or the text when it is not JSON */
    body: T;
}

export interface HttpClient {
    request<T = unknown>(method: string, url: string, body?: unknown, options?: HttpOptions): Promise<HttpResponse<T>>;
    get<T = unknown>(url: string, options?: HttpOptions): Promise<HttpResponse<T>>;
    post<T = unknown>(url: string, body?: unknown, options?: HttpOptions): Promise<HttpResponse<T>>;
    put<T = unknown>(url: string, body?: unknown, options?: HttpOptions): Promise<HttpResponse<T>>;
    delete<T = unknown>(url: string, options?: HttpOptions): Promise<HttpResponse<T>>;
}

function channel(name: string): HttpClient {
    const request = <T>(method: string, url: string, body?: unknown, options: HttpOptions = {}) =>
        internal.call<HttpResponse<T>>("http.request", { channel: name, method, url, ...(body !== undefined && { body }), ...options });
    return {
        request,
        get: (url, options) => request("GET", url, undefined, options),
        post: (url, body, options) => request("POST", url, body, options),
        put: (url, body, options) => request("PUT", url, body, options),
        delete: (url, options) => request("DELETE", url, undefined, options),
    };
}

const fallback = channel("default");

/**
 * A channel the host registered (`"app"`): its requests go out with the host's identity. [name] must be a string
 * literal so `tinyui build` can list it (docs/build-chain.md §5.1).
 */
export function client(name: string): HttpClient {
    return channel(name);
}

export const request = fallback.request;
export const get = fallback.get;
export const post = fallback.post;
export const put = fallback.put;
export { del as delete };
const del = fallback.delete;

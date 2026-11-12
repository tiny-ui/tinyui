// docs/native-api.md §3; the host implements Navigator (docs/app-model.md).
import { internal } from "tinyui-core";

export type Params = Record<string, string | number | boolean | null>;

/** J4: opens `page` (a module name such as `pages/detail`) on top of this one. */
export function push(page: string, params: Params = {}): void {
    internal.send("navigation.push", { page, params });
}

export interface Since {
    /**
     * The first page of the run to close: the nearest page of that name at or below this one in its stack. It and
     * everything above it, up to this page, close together. A page names the start of its own flow, never what the
     * flow was opened from.
     */
    since?: string;
}

/** J4: closes this page (or the run from `since` up to it) and opens `page` where it stood. */
export function replace(page: string, params: Params = {}, options: Since = {}): void {
    internal.send("navigation.replace", { page, params, ...(options.since === undefined ? {} : { since: options.since }) });
}

/** J4: closes this page (or the run from `since` up to it); `result` reaches the page below through `onResult`. */
export function pop(result: unknown = null, options: Since = {}): void {
    internal.send("navigation.pop", { result, ...(options.since === undefined ? {} : { since: options.since }) });
}

/** K5 `navigation.result`: the page that was pushed from here popped with a result. Call during render. */
export function onResult(fn: (result: unknown, from: string) => void): void {
    internal.onEmit("navigation.result", (p) => { const r = p as { result: unknown; from: string }; fn(r.result, r.from); });
}

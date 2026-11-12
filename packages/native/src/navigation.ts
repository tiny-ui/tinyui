// docs/native-api.md §3; the host implements Navigator (docs/app-model.md).
import { internal } from "tinyui-core";

/** What crosses to a page of another package or to a native route: flat, JSON. */
export type Params = Record<string, string | number | boolean | null>;

/** Augmented by `tinyui routes --ts`: each page of this package and the props it takes (docs/build-chain.md §10). */
export interface Routes {}

/** Augmented by `tinyui routes --ts`: `{ name: "<package>" }`. */
export interface RoutePackage {}

/** The props of a page module, `{}` for a page that takes none; what the generated `Routes` is written with. */
export type PageProps<M> = M extends { default: (...args: infer A) => unknown } ? (A extends [infer P, ...unknown[]] ? P : {}) : never;

type OwnPage = [keyof Routes] extends [never] ? string : keyof Routes & string;
type OwnName = RoutePackage extends { name: infer N extends string } ? `${N}/${string}` : never;
/** A page name outside this package: another package's page or a route the host's `Navigator` knows. */
type Foreign<P extends string> = P extends OwnName ? never : P;
type RouteArgs<K extends keyof Routes> = {} extends Routes[K] ? [params?: Routes[K]] : [params: Routes[K]];

export interface Since {
    /**
     * The first page of the run to close: the nearest page of that name at or below this one in its stack. It and
     * everything above it, up to this page, close together. A page names the start of its own flow, never what the
     * flow was opened from.
     */
    since?: OwnPage;
}

/**
 * J4: opens `page` (a module name such as `shop/detail`) on top of this one. A page of this package is checked
 * against `Routes`: its name must exist and `params` must be its props. Any other name is taken as it is.
 */
export function push<K extends keyof Routes & string>(page: K, ...params: RouteArgs<K>): void;
export function push<P extends string>(page: Foreign<P>, params?: Params): void;
export function push(page: string, params: object = {}): void {
    internal.send("navigation.push", { page, params });
}

/** J4: closes this page (or the run from `since` up to it) and opens `page` where it stood; typed as [push]. */
export function replace<K extends keyof Routes & string>(page: K, ...rest: [...RouteArgs<K>, options?: Since]): void;
export function replace<P extends string>(page: Foreign<P>, params?: Params, options?: Since): void;
export function replace(page: string, params: object = {}, options: Since = {}): void {
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

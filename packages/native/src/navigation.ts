// docs/native-api.md §3; the host implements Navigator (docs/app-model.md).
import { internal } from "tinyui-core";

/**
 * How the host shows a page (docs/native-api.md §3). A page that is not a plain screen says so itself, as a string
 * literal the build reads: `export const presentation: Presentation = "sheet";`. Whoever opens it need not know.
 */
export type Presentation = "screen" | "sheet";

/** A JSON value: what params are made of. */
export type Json = string | number | boolean | null | Json[] | { [key: string]: Json };

/** What crosses to a page of another package: a JSON object. */
export type Params = Record<string, Json>;

/**
 * Augmented by `tinyui routes --ts … --host <snapshot>`: each native route of the host the package is written for
 * and the params it takes (docs/native-api.md §3).
 */
export interface HostRoutes {}

/** Augmented by `tinyui routes --ts`: each page of this package and the props it takes (docs/build-chain.md §10). */
export interface Routes {}

/** Augmented by `tinyui routes --ts`: each page of this package that exports a type named `Result`, and that type. */
export interface RouteResults {}

/** Augmented by `tinyui routes --ts`: `{ name: "<package>" }`. */
export interface RoutePackage {}

/** The props of a page module, `{}` for a page that takes none; what the generated `Routes` is written with. */
export type PageProps<M> = M extends { default: (...args: infer A) => unknown } ? (A extends [infer P, ...unknown[]] ? P : {}) : never;

type OwnPage = [keyof Routes] extends [never] ? string : keyof Routes & string;
type OwnName = RoutePackage extends { name: infer N extends string } ? `${N}/${string}` : never;
/** A page name outside this package that is not a native route either: another package's page. */
type Foreign<P extends string> = P extends OwnName | keyof HostRoutes ? never : P;
type HostArgs<K extends keyof HostRoutes> = {} extends HostRoutes[K] ? [params?: HostRoutes[K]] : [params: HostRoutes[K]];
/** What `page` closes with: its `Result`, nothing but `null` for a page that declares none, anything before `tinyui routes` ran. */
type ResultOf<K> = [keyof Routes] extends [never] ? unknown : K extends keyof RouteResults ? RouteResults[K] : never;
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
 * against `Routes`: its name must exist and `params` must be its props. A native route of the host is checked
 * against `HostRoutes` the same way. Any other name is taken as it is.
 */
export function push<K extends keyof Routes & string>(page: K, ...params: RouteArgs<K>): void;
export function push<K extends keyof HostRoutes & string>(page: K, ...params: HostArgs<K>): void;
export function push<P extends string>(page: Foreign<P>, params?: Params): void;
export function push(page: string, params: object = {}): void {
    internal.send("navigation.push", { page, params });
}

/** J4: closes this page (or the run from `since` up to it) and opens `page` where it stood; typed as [push]. */
export function replace<K extends keyof Routes & string>(page: K, ...rest: [...RouteArgs<K>, options?: Since]): void;
export function replace<K extends keyof HostRoutes & string>(page: K, ...rest: [...HostArgs<K>, options?: Since]): void;
export function replace<P extends string>(page: Foreign<P>, params?: Params, options?: Since): void;
export function replace(page: string, params: object = {}, options: Since = {}): void {
    internal.send("navigation.replace", { page, params, ...(options.since === undefined ? {} : { since: options.since }) });
}

/**
 * J4: closes this page (or the run from `since` up to it); `result` reaches the page below through `onResult`, as
 * `null` when there is none. A page that hands something back exports its type as `Result`, which is what the
 * receiving side is typed with: `export type Result = …; const close = (result: Result) => navigation.pop(result);`.
 */
export function pop(result: unknown = null, options: Since = {}): void {
    internal.send("navigation.pop", { result, ...(options.since === undefined ? {} : { since: options.since }) });
}

/**
 * K5 `navigation.result`: a page opened from here closed. Call during render.
 *
 * Every close arrives, with or without a result: `result` is what the page popped with, `null` when it popped with
 * nothing or the user closed it (system back, a sheet swiped away). Naming the `page` keeps the others out and types
 * the result as the `Result` that page exports; a flow that went on by `replace` reports under its last page.
 */
export function onResult<K extends OwnPage>(page: K, fn: (result: ResultOf<K> | null) => void): void;
export function onResult(fn: (result: unknown, from: string) => void): void;
export function onResult(a: string | ((result: unknown, from: string) => void), b?: (result: never) => void): void {
    const fn = typeof a === "function" ? a : (result: unknown, from: string) => { if (from === a) (b as (result: unknown) => void)(result); };
    internal.onEmit("navigation.result", (p) => { const r = p as { result: unknown; from: string }; fn(r.result ?? null, r.from); });
}

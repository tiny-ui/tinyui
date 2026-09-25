export { VERSION } from "./version.ts";
export { pageVisible, manifest } from "./page.ts";
export type { HostManifest } from "./page.ts";
export { signal, memo, effect, onCleanup, untrack } from "./reactive.ts";
export { observable, unwrap } from "./observable.ts";
export { h, Fragment, thunk, ref } from "./node.ts";
export type { Node, Ref, Component } from "./node.ts";
export { For, Show } from "./control.ts";
export type { ForProps, ShowProps } from "./control.ts";
export { resource } from "./resource.ts";
export type { ResourceActions } from "./resource.ts";
export { HostError } from "./host.ts";
export type { HostErrorDetails } from "./host.ts";
export * as internal from "./internal.ts";
export * from "./components.ts";

// Globals the engine provides to page code; there is no DOM lib to declare them (docs/adr-005-engine.md).
declare global {
    interface ImportMeta {
        /** `tinyui:<module name>`, e.g. `tinyui:sample/home`. */
        readonly url: string;
    }
    /** Runs [fn] once after [ms], in a transaction of its own; gone with the page (docs/runtime-api.md §7). */
    function setTimeout(fn: () => void, ms?: number): number;
    function clearTimeout(id: number | undefined): void;
}

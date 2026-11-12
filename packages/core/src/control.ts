// For / Show / Keyed / BottomSheet: docs/runtime-api.md §4; slot mechanics in node.ts.
import { disposeOwner, effect, onCleanup, signal, untrack, type Owner } from "./reactive.ts";
import { CONTROL, h, renderInOwner, Slot, thunk, type Node } from "./node.ts";

/** `T` without its falsy values: what a condition holds while it is true. */
export type Truthy<T> = Exclude<T, false | null | undefined | 0 | "">;

export interface ShowProps<T> {
    when: T;
    /** Gets `when` as an accessor: it follows the value while the branch stays, and is never falsy in it. */
    children: (value: () => Truthy<T>) => Node;
    fallback?: () => Node;
}

type Control<F> = F & { [CONTROL]: true };

export const Show: Control<<T>(props: ShowProps<T>) => Node> = Object.assign(function Show<T>(props: ShowProps<T>): Node {
    const slot = new Slot();
    let current: { owner: Owner; node: number } | null = null;
    let shown: boolean | undefined;
    // the last truthy `when`: what the branch reads, so it never sees the falsy value that is about to remove it
    const [value, setValue] = signal<unknown>(undefined);
    effect(() => {
        const when = props.when;
        const truthy = !!when;
        untrack(() => {
            if (truthy) setValue(() => when);
            if (truthy === shown) return;
            shown = truthy;
            if (current) {
                disposeOwner(current.owner);
                slot.remove(current.node);
                current = null;
            }
            const render = truthy ? () => props.children(value as () => Truthy<T>) : props.fallback;
            if (render) {
                current = renderInOwner(render, truthy ? "<Show> children" : "<Show> fallback");
                slot.insert(current.node, 0);
            }
        });
    });
    onCleanup(() => { if (current) disposeOwner(current.owner); });
    return slot as unknown as Node;
}, { [CONTROL]: true as const });

export interface ForProps<T> {
    each: T[];
    key: (item: T, index: number) => string | number;
    children: (item: () => T, index: () => number) => Node;
}

interface Row<T> {
    owner: Owner;
    node: number;
    setItem: (v: T) => void;
    setIndex: (i: number) => void;
}

export const For: Control<<T>(props: ForProps<T>) => Node> = Object.assign(function For<T>(props: ForProps<T>): Node {
    const slot = new Slot();
    const rows = new Map<string | number, Row<T>>();
    let order: (string | number)[] = [];
    effect(() => {
        const list = props.each ?? [];
        const keys: (string | number)[] = [];
        const seen = new Set<string | number>();
        for (let i = 0; i < list.length; i++) {
            const k = props.key(list[i]!, i);
            if (seen.has(k)) throw new Error(`<For> duplicate key ${String(k)}`);
            seen.add(k);
            keys.push(k);
        }
        untrack(() => {
            for (const k of order) {
                if (!seen.has(k)) {
                    const row = rows.get(k)!;
                    disposeOwner(row.owner);
                    slot.remove(row.node);
                    rows.delete(k);
                }
            }
            for (let i = 0; i < keys.length; i++) {
                const k = keys[i]!, item = list[i]!;
                const row = rows.get(k);
                if (row) {
                    row.setItem(item);
                    row.setIndex(i);
                    if (slot.nodes[i] !== row.node) slot.move(row.node, i);
                } else {
                    const [getItem, setItem] = signal<T>(item);
                    const [getIndex, setIndex] = signal(i);
                    const { owner, node } = renderInOwner(() => props.children(getItem, getIndex), "<For> row");
                    rows.set(k, { owner, node, setItem, setIndex });
                    slot.insert(node, i);
                }
            }
            order = keys;
        });
    });
    onCleanup(() => { for (const row of rows.values()) disposeOwner(row.owner); });
    return slot as unknown as Node;
}, { [CONTROL]: true as const });

export interface KeyedProps<T> {
    /** Any value, falsy ones included; a different one (`!==`) rebuilds the content. */
    by: T;
    /** Builds the content with the value `by` had at that moment; it is fixed for the life of that content. */
    children: (value: T) => Node;
}

/** One node that is rebuilt from scratch whenever `by` changes: what resets the state inside it. */
export const Keyed: Control<<T>(props: KeyedProps<T>) => Node> = Object.assign(function Keyed<T>(props: KeyedProps<T>): Node {
    const slot = new Slot();
    let current: { owner: Owner; node: number } | null = null;
    let last: unknown;
    effect(() => {
        const by = props.by;
        if (current && by === last) return;
        last = by;
        untrack(() => {
            if (current) {
                disposeOwner(current.owner);
                slot.remove(current.node);
                current = null;
            }
            current = renderInOwner(() => props.children(by), "<Keyed> children");
            slot.insert(current.node, 0);
        });
    });
    onCleanup(() => { if (current) disposeOwner(current.owner); });
    return slot as unknown as Node;
}, { [CONTROL]: true as const });

export interface BottomSheetProps<T = boolean> {
    /** Truthy slides the sheet in, falsy animates it out. Pass the data the sheet is about to get it as a snapshot in `children`. */
    open: T;
    /** Fires after the user swiped, tapped the scrim or pressed back; make `open` falsy in it. */
    onDismiss?: () => void;
    /** Builds the content, once per opening, with the value `open` had at that moment. */
    children: (value: Truthy<T>) => Node;
}

/**
 * M3 modal bottom sheet over the page (docs/components.md §3). Its content is built anew each time it opens, so
 * what the user typed last time is gone on both sides of the bridge; nothing is built while it has never been open.
 * Closing keeps the content for the hide animation: it is disposed at the next opening or with the page.
 */
export function BottomSheet<T>(props: BottomSheetProps<T>): Node {
    const render = props.children;
    if (typeof render !== "function") throw new Error("<BottomSheet> children must be a function: {() => <Content />}");
    const slot = new Slot();
    let current: { owner: Owner; node: number } | null = null;
    let last: unknown = undefined;
    effect(() => {
        const value = props.open;
        const opened = !!value && value !== last;
        last = value;
        if (!opened) return;
        untrack(() => {
            if (current) {
                disposeOwner(current.owner);
                slot.remove(current.node);
                current = null;
            }
            current = renderInOwner(() => render(value as Truthy<T>), "<BottomSheet> children");
            slot.insert(current.node, 0);
        });
    });
    onCleanup(() => { if (current) disposeOwner(current.owner); });
    const onDismiss = props.onDismiss;
    return h("BottomSheet", { open: thunk(() => !!props.open), ...(onDismiss ? { onDismiss } : {}) }, slot);
}

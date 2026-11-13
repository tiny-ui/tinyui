// docs/native-api.md §7: this package's persistent key-value, read synchronously from memory.
import { effect, HostError, internal, resource, signal, untrack, type ResourceActions } from "tinyui-core";

/** J2: the stored value, or undefined. */
export function get<T = unknown>(key: string): T | undefined {
    const v = internal.query<T | null>("storage.get", { key });
    return v === null ? undefined : v;
}

/** J2: throws HostError E_QUOTA when the package would exceed its limit, E_UNSUPPORTED when the host has no storage. */
export function set(key: string, value: unknown): void {
    const code = internal.query<string | null>("storage.set", { key, value: JSON.stringify(value) });
    if (code !== null) throw new HostError(code, `storage.set("${key}") failed: ${code}`);
}

/** J4 */
export function remove(key: string): void {
    internal.send("storage.remove", { key });
}

/** J4: every key of this package. */
export function clear(): void {
    internal.send("storage.clear", {});
}

export type CachedActions<T> = ResourceActions<T>;

/**
 * Stale-while-revalidate over [storage]: `resource` that starts with the stored value and stores each new result.
 * A fetch that fails keeps what is shown and sets `error`; so does a result that could not be stored (E_QUOTA).
 * `lazy`, `load()` and `refetch()` are `resource`'s (docs/runtime-api.md §6).
 */
export function cached<T>(key: string, fetcher: () => Promise<T>, options: { lazy?: boolean } = {}): [data: () => T | undefined, actions: CachedActions<T>] {
    const stored = get<T>(key);
    const [storeError, setStoreError] = signal<unknown>(undefined);
    const [data, actions] = resource<T>(() => { setStoreError(undefined); return fetcher(); }, { ...options, ...(stored === undefined ? {} : { initial: stored }) });
    // only the newest fetch writes `data`, so only its result is stored
    let last: T | undefined = stored;
    effect(() => {
        const value = data();
        if (value === last) return;
        last = value;
        try { set(key, value); } catch (e) { untrack(() => setStoreError(e)); }
    });
    return [data, { ...actions, error: () => actions.error() ?? storeError() }];
}

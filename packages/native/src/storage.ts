// docs/native-api.md §7: this package's persistent key-value, read synchronously from memory.
import { HostError, internal, onCleanup, signal } from "tinyui-core";

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

export interface CachedActions {
    loading: () => boolean;
    error: () => unknown;
    refetch: () => void;
}

/**
 * Stale-while-revalidate over [storage]: starts with the stored value, then runs [fetcher]; a success replaces it
 * and is stored, a failure keeps it and sets `error`. Shaped like `resource`.
 */
export function cached<T>(key: string, fetcher: () => Promise<T>): [data: () => T | undefined, actions: CachedActions] {
    const [data, setData] = signal<T | undefined>(get<T>(key));
    const [loading, setLoading] = signal(false);
    const [error, setError] = signal<unknown>(undefined);
    let disposed = false;
    let latest = 0;
    onCleanup(() => { disposed = true; });
    const run = () => {
        // only the newest run may settle: an older fetch finishing late must not overwrite it
        const run = ++latest;
        const current = () => !disposed && run === latest;
        setLoading(true);
        setError(undefined);
        let pending: Promise<T>;
        try {
            pending = fetcher();
        } catch (e) {
            pending = Promise.reject(e);
        }
        pending.then(
            (v) => {
                if (!current()) return;
                setData(() => v);
                setLoading(false);
                try { set(key, v); } catch (e) { setError(e); }
            },
            (e: unknown) => { if (!current()) return; setError(e); setLoading(false); },
        );
    };
    run();
    return [data, { loading, error, refetch: run }];
}

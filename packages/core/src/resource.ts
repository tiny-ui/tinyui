import { effect, insideRender, onCleanup, signal, untrack } from "./reactive.ts";

export interface ResourceActions<T> {
    loading: () => boolean;
    error: () => unknown;
    /**
     * The data, fetching it only if needed: what is there is returned at once, a fetch on its way is joined rather
     * than repeated, and one runs when nothing was fetched yet or the last one failed. Rejects with what `error()` holds.
     */
    load: () => Promise<T>;
    /** Fetches again whatever is there; settles like [load]. */
    refetch: () => Promise<T>;
}

export interface ResourceOptions<T> {
    /** Nothing is fetched until the first `load()` / `refetch()`; for data only some paths through the page need. */
    lazy?: boolean;
    /** What `data()` holds before the first result. It does not count as fetched: `load()` still fetches. */
    initial?: T;
}

/**
 * docs/runtime-api.md §6: signals are created now, written when the promise settles. Only the newest fetch may
 * write them; with a `source`, a changed value (`!==`) makes what was fetched stale and fetches again.
 */
export function resource<T>(fetcher: () => Promise<T>, options?: ResourceOptions<T>): [data: () => T | undefined, actions: ResourceActions<T>];
export function resource<S, T>(source: () => S, fetcher: (source: S) => Promise<T>, options?: ResourceOptions<T>): [data: () => T | undefined, actions: ResourceActions<T>];
export function resource<S, T>(
    a: (() => S) | (() => Promise<T>),
    b?: ((source: S) => Promise<T>) | ResourceOptions<T>,
    c?: ResourceOptions<T>,
): [data: () => T | undefined, actions: ResourceActions<T>] {
    if (!insideRender()) throw new Error("resource() called outside a synchronous render period");
    const source = typeof b === "function" ? (a as () => S) : undefined;
    const fetcher = (typeof b === "function" ? b : a) as (source: S) => Promise<T>;
    const options = (typeof b === "function" ? c : b) ?? {};

    const [data, setData] = signal<T | undefined>(options.initial);
    const [loading, setLoading] = signal(false);
    const [error, setError] = signal<unknown>(undefined);
    let disposed = false;
    onCleanup(() => { disposed = true; });

    let latest = 0;
    /** The newest fetch while it is on its way. */
    let pending: Promise<T> | null = null;
    /** Whether `data` is the result of the newest fetch, for the current source. */
    let fresh = false;
    /** Whether a fetch was ever asked for: a lazy resource that nobody used does not start on a source change. */
    let wanted = false;
    let current = undefined as S;
    // the newest fetch's result, kept apart from the signals: they stop being written once the owner is disposed
    let value: T | undefined;
    let failure: unknown;

    /** How the newest fetch ended, or will end. */
    const outcome = (): Promise<T> => pending ?? (fresh ? Promise.resolve(value as T) : Promise.reject(failure));

    function run(): Promise<T> {
        const id = ++latest;
        wanted = true;
        fresh = false;
        if (!disposed) {
            setLoading(true);
            setError(undefined);
        }
        let fetched: Promise<T>;
        try {
            fetched = fetcher(current);
        } catch (e) {
            fetched = Promise.reject(e);
        }
        const settled: Promise<T> = fetched.then(
            (v) => {
                // overtaken by a newer fetch: its caller gets that one's outcome, the signals are not touched
                if (id !== latest) return outcome();
                pending = null;
                fresh = true;
                value = v;
                if (!disposed) {
                    setData(() => v);
                    setLoading(false);
                }
                return v;
            },
            (e: unknown) => {
                if (id !== latest) return outcome();
                pending = null;
                failure = e;
                if (!disposed) {
                    setError(e);
                    setLoading(false);
                }
                throw e;
            },
        );
        // a failure nobody awaits is in error(), not an unhandled rejection
        settled.catch(() => {});
        pending = settled;
        return settled;
    }

    const load = (): Promise<T> => (fresh ? Promise.resolve(value as T) : pending ?? run());

    if (source) {
        let first = true;
        effect(() => {
            const next = source();
            untrack(() => {
                const changed = !first && next !== current;
                current = next;
                if (first ? !options.lazy : changed && wanted) run();
                else if (changed) fresh = false;
                first = false;
            });
        });
    } else if (!options.lazy) {
        run();
    }
    return [data, { loading, error, load, refetch: run }];
}

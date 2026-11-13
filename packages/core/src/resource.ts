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
    /**
     * What everyone waiting right now gets: settled by the newest fetch, whichever fetch they started waiting on. A
     * fetch that overtakes another takes its waiters along at once, so a slow or stuck older fetch holds nobody up.
     */
    let round: { promise: Promise<T>; resolve: (value: T) => void; reject: (error: unknown) => void } | null = null;
    /** Whether `data` is the result of the newest fetch, for the current source. */
    let fresh = false;
    /** Whether a fetch was ever asked for: a lazy resource that nobody used does not start on a source change. */
    let wanted = false;
    let current = undefined as S;
    // the newest result, kept apart from the signal: that stops being written once the owner is disposed
    let value: T | undefined;

    function run(): Promise<T> {
        const id = ++latest;
        wanted = true;
        fresh = false;
        if (!disposed) {
            setLoading(true);
            setError(undefined);
        }
        const waiting = (round ??= newRound<T>());
        let fetched: Promise<T>;
        try {
            fetched = fetcher(current);
        } catch (e) {
            fetched = Promise.reject(e);
        }
        fetched.then(
            (v) => {
                // overtaken by a newer fetch: the signals and the waiters are that one's to settle
                if (id !== latest) return;
                round = null;
                fresh = true;
                value = v;
                if (!disposed) {
                    setData(() => v);
                    setLoading(false);
                }
                waiting.resolve(v);
            },
            (e: unknown) => {
                if (id !== latest) return;
                round = null;
                if (!disposed) {
                    setError(e);
                    setLoading(false);
                }
                waiting.reject(e);
            },
        );
        return waiting.promise;
    }

    const load = (): Promise<T> => (fresh ? Promise.resolve(value as T) : round?.promise ?? run());

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

function newRound<T>(): { promise: Promise<T>; resolve: (value: T) => void; reject: (error: unknown) => void } {
    let resolve!: (value: T) => void, reject!: (error: unknown) => void;
    const promise = new Promise<T>((res, rej) => { resolve = res; reject = rej; });
    // a failure nobody awaits is in error(), not an unhandled rejection
    promise.catch(() => {});
    return { promise, resolve, reject };
}

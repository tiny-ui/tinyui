import { insideRender, onCleanup, signal } from "./reactive.ts";

/** One thing at a time across several actions: see [actionLock]. */
export interface ActionLock {
    /** Whether any action sharing this lock is running. */
    held: () => boolean;
}

export interface ActionOptions {
    /** Share one lock with other actions: while any of them runs, calls to the rest are dropped too. */
    lock?: ActionLock;
}

/** Takes the lock, or answers null when it is held. */
const take = new WeakMap<ActionLock, () => (() => void) | null>();

/**
 * A lock several actions share (docs/runtime-api.md §6.1): a page whose three ways to sign in must not overlap gives
 * all three the same lock, and disables its buttons on `held()`. Created during render, it stops writing `held` once
 * its own scope is gone, whichever action was holding it.
 */
export function actionLock(): ActionLock {
    const [held, setHeld] = signal(false);
    let taken = false;
    let disposed = false;
    if (insideRender()) onCleanup(() => { disposed = true; });
    const lock: ActionLock = { held };
    take.set(lock, () => {
        if (taken) return null;
        taken = true;
        if (!disposed) setHeld(true);
        return () => {
            taken = false;
            // the lock's scope decides, not the action's: a lock outliving the action that held it must read free again
            if (!disposed) setHeld(false);
        };
    });
    return lock;
}

/**
 * docs/runtime-api.md §6.1: what a button does, as an operation that cannot overlap itself. `run(...)` calls [fn]
 * unless a run is still on its way, in which case the call is dropped and resolves with `undefined`; that is decided
 * synchronously, at the call, not by a disabled button. `running()` is true from the call until [fn] settles, either
 * way; what [fn] throws reaches the caller of `run`.
 */
export function action<A extends unknown[], T>(
    fn: (...args: A) => Promise<T> | T,
    options: ActionOptions = {},
): [run: (...args: A) => Promise<T | undefined>, running: () => boolean] {
    if (!insideRender()) throw new Error("action() called outside a synchronous render period");
    const [running, setRunning] = signal(false);
    const acquire = take.get(options.lock ?? actionLock());
    if (!acquire) throw new Error("action(): `lock` must come from actionLock()");
    let disposed = false;
    onCleanup(() => { disposed = true; });

    async function run(...args: A): Promise<T | undefined> {
        // a call that outlives the page (a kept reference, a late timer) starts nothing
        if (disposed) return undefined;
        const release = acquire!();
        if (release === null) return undefined;
        setRunning(true);
        try {
            return await fn(...args);
        } finally {
            release();
            // the page may be gone by now; its signals are nobody's to write
            if (!disposed) setRunning(false);
        }
    }
    return [run, running];
}

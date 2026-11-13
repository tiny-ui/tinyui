// Runtime signals: docs/js-runtime.html §1 / §3 for the model, docs/runtime-api.md §2 for the contract.

export interface Owner {
    effects: Effect[];
    cleanups: (() => void)[];
    disposed: boolean;
}

export interface Effect {
    fn: () => void;
    deps: Set<Effect>[];
    owner: Owner | null;
    cleanups: (() => void)[];
    queued: boolean;
    disposed: boolean;
    runs: number;
    memo: Memo<unknown> | null;
    /** A signal it read was written: it has to run again. */
    direct: boolean;
    /**
     * Memos it read that may have a new value, each with the version it had when it went stale: it runs again only if
     * one moved on. Null until a memo it reads goes stale: most effects are bindings that never read one.
     */
    stale: Map<Memo<unknown>, number> | null;
}

interface Memo<T> {
    effect: Effect;
    subs: Set<Effect>;
    value: T;
    /** Something upstream changed since the value was computed; it may still turn out to be the same value. */
    dirty: boolean;
    /** Counts the times the value actually changed (`!==`): what its readers compare to tell a recompute from a change. */
    version: number;
    computed: boolean;
}

let currentEffect: Effect | null = null;
let currentOwner: Owner | null = null;
const pending: Effect[] = [];
const ran: Effect[] = [];

const MAX_RUNS_PER_FLUSH = 100;

export function createOwner(): Owner {
    return { effects: [], cleanups: [], disposed: false };
}

/** Runs [fn] as a synchronous render period under [owner]: effects it creates belong to the owner. */
export function runWithOwner<T>(owner: Owner, fn: () => T): T {
    const prevOwner = currentOwner, prevEffect = currentEffect;
    currentOwner = owner;
    currentEffect = null;
    try {
        return fn();
    } finally {
        currentOwner = prevOwner;
        currentEffect = prevEffect;
    }
}

export function disposeOwner(owner: Owner): void {
    if (owner.disposed) return;
    owner.disposed = true;
    for (const e of owner.effects) disposeEffect(e);
    owner.effects.length = 0;
    const cleanups = owner.cleanups.splice(0).reverse();
    for (const c of cleanups) c();
}

export function insideRender(): boolean {
    return currentOwner !== null;
}

/** True while an effect is collecting dependencies; stores use it to skip allocating subscriber sets. */
export function tracking(): boolean {
    return currentEffect !== null;
}

export function signal<T>(init: T): [get: () => T, set: (next: T | ((prev: T) => T)) => void] {
    let value = init;
    const subs = new Set<Effect>();
    const get = () => {
        track(subs);
        return value;
    };
    const set = (next: T | ((prev: T) => T)) => {
        const v = typeof next === "function" ? (next as (prev: T) => T)(value) : next;
        if (v === value) return;
        value = v;
        notify(subs);
    };
    return [get, set];
}

export function memo<T>(fn: () => T): () => T {
    const effect = createEffectRecord(() => {
        const v = fn();
        if (!m.computed || v !== m.value) {
            m.value = v;
            m.version++;
        }
        m.computed = true;
    });
    const m: Memo<T> = { effect, subs: new Set(), value: undefined as T, dirty: true, version: 0, computed: false };
    effect.memo = m as Memo<unknown>;
    effect.direct = true;
    return () => {
        track(m.subs);
        refresh(m as Memo<unknown>);
        return m.value;
    };
}

/** Brings a memo up to date: recomputed only if something it read really changed, its version bumped only if the result did. */
function refresh(m: Memo<unknown>): void {
    if (!m.dirty || m.effect.disposed) return;
    if (changed(m.effect)) runEffect(m.effect);
    else settle(m.effect);
    m.dirty = false;
}

/** Whether what [e] read changed since it last ran: a signal written, or a memo whose value moved on once recomputed. */
function changed(e: Effect): boolean {
    if (e.direct) return true;
    if (e.stale) {
        for (const [m, version] of e.stale) {
            refresh(m);
            if (m.version !== version) return true;
        }
    }
    return false;
}

function settle(e: Effect): void {
    e.direct = false;
    e.stale?.clear();
}

export function effect(fn: () => void): void {
    runEffect(createEffectRecord(fn));
}

export function onCleanup(fn: () => void): void {
    if (currentEffect) currentEffect.cleanups.push(fn);
    else if (currentOwner) currentOwner.cleanups.push(fn);
    else throw new Error("onCleanup() called outside a synchronous render period");
}

export function untrack<T>(fn: () => T): T {
    const prev = currentEffect;
    currentEffect = null;
    try {
        return fn();
    } finally {
        currentEffect = prev;
    }
}

/** Re-runs every queued effect until the queue is empty. Throws (E2) on an update loop or a render error. */
export function runPending(): void {
    try {
        for (let i = 0; i < pending.length; i++) {
            const e = pending[i]!;
            e.queued = false;
            if (e.disposed) continue;
            // queued because a memo it reads went stale, and every such memo came out with the value it had: nothing to redo
            if (!changed(e)) {
                settle(e);
                continue;
            }
            if (++e.runs > MAX_RUNS_PER_FLUSH) throw new Error("update loop: an effect re-ran more than 100 times in one flush");
            runEffect(e);
        }
    } finally {
        pending.length = 0;
        for (const e of ran) e.runs = 0;
        ran.length = 0;
    }
}

function createEffectRecord(fn: () => void): Effect {
    if (!currentOwner) throw new Error("effect() called outside a synchronous render period");
    const e: Effect = { fn, deps: [], owner: currentOwner, cleanups: [], queued: false, disposed: false, runs: 0, memo: null, direct: false, stale: null };
    currentOwner.effects.push(e);
    return e;
}

function runEffect(e: Effect): void {
    if (e.disposed) return;
    unsubscribe(e);
    runCleanups(e);
    // before the run, not after: what the run itself writes has to count as a change for the next one
    settle(e);
    const prevEffect = currentEffect, prevOwner = currentOwner;
    currentEffect = e;
    currentOwner = e.owner;
    try {
        e.fn();
    } catch (t) {
        // a memo that failed has no value for this state: the next read computes it again
        e.direct = true;
        throw t;
    } finally {
        currentEffect = prevEffect;
        currentOwner = prevOwner;
    }
}

function disposeEffect(e: Effect): void {
    e.disposed = true;
    unsubscribe(e);
    runCleanups(e);
}

function unsubscribe(e: Effect): void {
    for (const subs of e.deps) subs.delete(e);
    e.deps.length = 0;
}

function runCleanups(e: Effect): void {
    const cleanups = e.cleanups.splice(0).reverse();
    for (const c of cleanups) c();
}

export function track(subs: Set<Effect>): void {
    const e = currentEffect;
    if (!e || subs.has(e)) return;
    subs.add(e);
    e.deps.push(subs);
}

/**
 * Tells the readers in [subs] that what they read changed. [via] is the memo whose readers these are, when the change
 * is only that it went stale: its value is recomputed on demand and may come out the same, so they are marked "maybe".
 */
export function notify(subs: Set<Effect>, via: Memo<unknown> | null = null): void {
    for (const e of subs) {
        if (via === null) e.direct = true;
        else if (!e.stale?.has(via)) (e.stale ??= new Map()).set(via, via.version);
        if (e.memo) {
            if (!e.memo.dirty) {
                e.memo.dirty = true;
                notify(e.memo.subs, e.memo);
            }
        } else if (!e.queued) {
            e.queued = true;
            pending.push(e);
            if (e.runs === 0) ran.push(e);
        }
    }
}

import assert from "node:assert/strict";
import { afterEach, describe, it } from "node:test";
import { bridge } from "./host-stub.ts";
import { drain, mount, tinyui, transaction, unmount } from "./helpers.ts";
import { Column, h, HostError, internal, pageVisible, resource, signal, Text, thunk } from "../src/index.ts"
const { call, query, onEmit } = internal;;
import * as timers from "../src/timers.ts";

const g = globalThis as Record<string, unknown>;
afterEach(() => unmount());

describe("resource + K3", () => {
    it("fires the host call at creation and writes signals when resolved", async () => {
        mount(() => {
            const [user, { loading }] = resource(() => call<{ name: string }>("http.get", { url: "/me" }));
            return h(Column, null, h(Text, { text: thunk(() => loading() ? "loading" : user()!.name) }));
        });
        assert.deepEqual(bridge.calls.map((c) => [c.name, c.args]), [["http.get", { url: "/me" }]]);
        assert.deepEqual(bridge.ops().filter((o) => o[0] === "p"), [["p", 1, "text", "loading"]]);
        tinyui().resolve(bridge.calls[0]!.cbId, JSON.stringify({ name: "harlon" }));
        await drain();
        const before = bridge.applied.length;
        tinyui().flush();
        assert.deepEqual(bridge.applied.slice(before).flat(), [["p", 1, "text", "harlon"]]);
        unmount();
    });

    it("rejection lands in error() with the call site's stack and is not reported", async () => {
        let seen: HostError | undefined;
        mount(() => {
            const [, { error }] = resource(() => call("http.get"));
            return h(Column, null, h(Text, { text: thunk(() => { const e = error(); if (e instanceof HostError) seen = e; return e instanceof HostError ? e.code : "ok"; }) }));
        });
        tinyui().reject(bridge.calls.at(-1)!.cbId, JSON.stringify({ code: "E_NET", message: "offline" }));
        await drain();
        const before = bridge.applied.length;
        tinyui().flush();
        assert.deepEqual(bridge.applied.slice(before).flat(), [["p", 1, "text", "E_NET"]]);
        assert.equal(bridge.reports.length, 0);
        assert.match(seen!.stack ?? "", /at resource /, "the stack is where call() happened (inside resource's fetcher)");
        assert.doesNotMatch(seen!.stack ?? "", /rejectPending/, "not where reject() arrived");
        unmount();
    });

    it("a non-JSON host result rejects instead of hanging", async () => {
        mount(() => {
            const [, { error, loading }] = resource(() => call("x"));
            return h(Column, null, h(Text, { text: thunk(() => loading() ? "loading" : String((error() as HostError).code)) }));
        });
        tinyui().resolve(bridge.calls.at(-1)!.cbId, "{not json");
        await drain();
        const before = bridge.applied.length;
        tinyui().flush();
        assert.deepEqual(bridge.applied.slice(before).flat(), [["p", 1, "text", "E_BAD_JSON"]]);
    });

    it("a result arriving after unmount is dropped", async () => {
        mount(() => {
            const [v] = resource(() => call("x"));
            return h(Column, null, h(Text, { text: thunk(() => String(v())) }));
        });
        const cbId = bridge.calls.at(-1)!.cbId;
        unmount();
        tinyui().resolve(cbId, "1");
        await drain();
        const before = bridge.applied.length;
        tinyui().flush();
        assert.equal(bridge.applied.length, before);
    });
});

describe("resource: load, lazy, source", () => {
    /** A fetcher whose promises the test settles by hand, in any order. */
    function manual<T>() {
        const calls: { arg: unknown; resolve: (v: T) => void; reject: (e: unknown) => void }[] = [];
        const fetcher = (arg?: unknown) => new Promise<T>((resolve, reject) => { calls.push({ arg, resolve, reject }); });
        return { calls, fetcher };
    }
    type Actions<T> = { loading: () => boolean; error: () => unknown; load: () => Promise<T>; refetch: () => Promise<T> };

    it("lazy fetches nothing until load(); load() joins a fetch on its way and reuses a result", async () => {
        const { calls, fetcher } = manual<string>();
        let data: () => string | undefined = () => undefined, actions: Actions<string> | undefined;
        mount(() => { [data, actions] = resource(() => fetcher(), { lazy: true }); return h(Text, { text: "x" }); });
        assert.equal(calls.length, 0);
        assert.equal(actions!.loading(), false);
        const a = actions!.load(), b = actions!.load();
        assert.equal(calls.length, 1, "the second load joined the first");
        assert.equal(actions!.loading(), true);
        calls[0]!.resolve("codes");
        assert.deepEqual(await Promise.all([a, b]), ["codes", "codes"]);
        assert.equal(data(), "codes");
        assert.equal(await actions!.load(), "codes");
        assert.equal(calls.length, 1, "what is there is not fetched again");
        void actions!.refetch();
        assert.equal(calls.length, 2, "refetch always fetches");
    });

    it("a failed fetch rejects load(), stays in error(), and the next load() tries again", async () => {
        const { calls, fetcher } = manual<string>();
        let actions: Actions<string> | undefined;
        mount(() => { [, actions] = resource(() => fetcher()); return h(Text, { text: "x" }); });
        const failed = actions!.load();
        assert.equal(calls.length, 1, "joined the fetch started at creation");
        calls[0]!.reject(new Error("offline"));
        await assert.rejects(failed, /offline/);
        assert.equal((actions!.error() as Error).message, "offline");
        const again = actions!.load();
        assert.equal(calls.length, 2);
        assert.equal(actions!.error(), undefined);
        calls[1]!.resolve("ok");
        assert.equal(await again, "ok");
        await drain();
        assert.equal(bridge.reports.length, 0);
    });

    it("only the newest fetch writes; whoever awaited an older one gets the newest outcome", async () => {
        const { calls, fetcher } = manual<string>();
        let data: () => string | undefined = () => undefined, actions: Actions<string> | undefined;
        mount(() => { [data, actions] = resource(() => fetcher()); return h(Text, { text: "x" }); });
        const older = actions!.load();
        const newer = actions!.refetch();
        calls[1]!.resolve("newer");
        assert.equal(await newer, "newer");
        calls[0]!.resolve("older");
        assert.equal(await older, "newer", "the late result is dropped");
        assert.equal(data(), "newer");
        // and while the newest is still on its way
        const third = actions!.refetch(), fourth = actions!.refetch();
        calls[2]!.reject(new Error("stale failure"));
        calls[3]!.resolve("fourth");
        assert.deepEqual(await Promise.all([third, fourth]), ["fourth", "fourth"]);
        assert.equal(actions!.error(), undefined);
    });

    it("a fetch that never settles holds nobody up once a newer one has", async () => {
        const { calls, fetcher } = manual<string>();
        let data: () => string | undefined = () => undefined, actions: Actions<string> | undefined;
        mount(() => { [data, actions] = resource(() => fetcher()); return h(Text, { text: "x" }); });
        const stuck = actions!.load();
        const newer = actions!.refetch();
        calls[1]!.resolve("newer");
        // calls[0] is never settled
        assert.deepEqual(await Promise.all([stuck, newer]), ["newer", "newer"]);
        assert.equal(data(), "newer");
        assert.equal(actions!.loading(), false);
    });

    it("a source hands its value to the fetcher and fetches again when it changes", async () => {
        const { calls, fetcher } = manual<string>();
        let data: () => string | undefined = () => undefined, actions: Actions<string> | undefined;
        mount(() => {
            const [locale, setLocale] = signal("en");
            g["__setLocale"] = setLocale;
            [data, actions] = resource(locale, (l) => fetcher(l));
            return h(Text, { text: "x" });
        });
        const setLocale = g["__setLocale"] as (v: string) => void;
        assert.deepEqual(calls.map((c) => c.arg), ["en"]);
        const waiting = actions!.load();
        transaction(() => setLocale("zh"));
        assert.deepEqual(calls.map((c) => c.arg), ["en", "zh"]);
        calls[0]!.resolve("rules");
        calls[1]!.resolve("规则");
        assert.equal(await waiting, "规则", "the load that started under the old source waits for the new one");
        assert.equal(data(), "规则");
        transaction(() => setLocale("zh"));
        assert.equal(calls.length, 2, "the same value is not a change");
    });

    it("a lazy resource with a source stays idle through source changes until it is asked for", async () => {
        const { calls, fetcher } = manual<string>();
        let actions: Actions<string> | undefined;
        mount(() => {
            const [locale, setLocale] = signal("en");
            g["__setLocale"] = setLocale;
            [, actions] = resource(locale, (l) => fetcher(l), { lazy: true });
            return h(Text, { text: "x" });
        });
        const setLocale = g["__setLocale"] as (v: string) => void;
        transaction(() => setLocale("zh"));
        assert.equal(calls.length, 0);
        void actions!.load();
        assert.deepEqual(calls.map((c) => c.arg), ["zh"]);
        transaction(() => setLocale("en"));
        assert.deepEqual(calls.map((c) => c.arg), ["zh", "en"], "once in use it follows the source");
    });

    it("initial shows before the first result and does not count as fetched", async () => {
        const { calls, fetcher } = manual<string>();
        let data: () => string | undefined = () => undefined, actions: Actions<string> | undefined;
        mount(() => { [data, actions] = resource(() => fetcher(), { lazy: true, initial: "stored" }); return h(Text, { text: "x" }); });
        assert.equal(data(), "stored");
        const loaded = actions!.load();
        assert.equal(calls.length, 1);
        calls[0]!.resolve("fetched");
        assert.equal(await loaded, "fetched");
    });
});

describe("other entries", () => {
    it("query returns parsed JSON; timers use the cbId space", () => {
        bridge.queries["device.info"] = { os: "android" };
        assert.deepEqual(query("device.info"), { os: "android" });
        const id = timers.setTimeout(() => {}, 10);
        assert.equal(bridge.calls.at(-1)!.name, "timer.schedule");
        timers.clearTimeout(id);
        assert.deepEqual(bridge.sent.at(-1), { name: "timer.cancel", args: { cbId: id } });
    });

    it("visible() and emit() reach subscribed effects in one transaction", () => {
        mount(() => {
            const [net, setNet] = signal("?");
            onEmit("network", (p) => setNet((p as { online: boolean }).online ? "on" : "off"));
            return h(Column, null, h(Text, { text: thunk(() => `${pageVisible() ? "v" : "h"}:${net()}`) }));
        });
        assert.deepEqual(transaction(() => tinyui().visible(false)), [["p", 1, "text", "h:?"]]);
        assert.deepEqual(transaction(() => tinyui().emit("network", JSON.stringify({ online: true }))), [["p", 1, "text", "h:on"]]);
        unmount();
        tinyui().visible(true);
    });
});

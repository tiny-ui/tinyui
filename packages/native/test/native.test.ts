import assert from "node:assert/strict";
import { afterEach, describe, it } from "node:test";
import { bridge } from "./host-stub.ts";
import { Column, h, Text, thunk } from "tinyui-core";
import { analytics, cached, events, HostError, host, http, i18n, linking, navigation, session, storage, store, ui } from "../src/index.ts";

type Entries = { mount(page: unknown, props: string, host: string): void; unmount(): void; flush(): void; resolve(id: number, json: string): void; emit(topic: string, json: string): void };
const tinyui = (): Entries => (globalThis as Record<string, unknown>)["__tinyui"] as Entries;
const HOST = JSON.stringify({ components: {}, capabilities: [] });
const drain = () => new Promise((r) => setTimeout(r, 0));
afterEach(() => tinyui().unmount());

describe("tinyui-native", () => {
    it("http.get is a J3 http.request that resolves through K3", async () => {
        const p = http.get<{ ok: boolean }>("/me", { timeout: 100 });
        const call = bridge.calls.at(-1)!;
        assert.equal(call.name, "http.request");
        assert.deepEqual(call.args, { channel: "default", method: "GET", url: "/me", timeout: 100 });
        tinyui().resolve(call.cbId, JSON.stringify({ status: 200, headers: {}, body: { ok: true } }));
        await drain();
        assert.deepEqual(await p, { status: 200, headers: {}, body: { ok: true } });
    });

    it("http.client names its channel, and E_HTTP carries status and body", async () => {
        const p = http.client("app").post("https://api/x", { plan: "annual" });
        const call = bridge.calls.at(-1)!;
        assert.deepEqual(call.args, { channel: "app", method: "POST", url: "https://api/x", body: { plan: "annual" } });
        const rejected = assert.rejects(p, (e: HostError) => e.code === "E_HTTP" && e.status === 409 && (e.body as { error: string }).error === "dup");
        ((globalThis as Record<string, unknown>)["__tinyui"] as { reject(id: number, json: string): void })
            .reject(call.cbId, JSON.stringify({ code: "E_HTTP", message: "409", status: 409, headers: { a: "b" }, body: { error: "dup" } }));
        await rejected;
    });

    it("storage reads and writes through J2; a returned code throws", () => {
        bridge.queries["storage.get"] = { v: 1 };
        assert.deepEqual(storage.get("k"), { v: 1 });
        bridge.queries["storage.set"] = null;
        storage.set("k", { v: 2 });
        bridge.queries["storage.set"] = "E_QUOTA";
        assert.throws(() => storage.set("k", "x".repeat(10)), (e: HostError) => e.code === "E_QUOTA");
        storage.remove("k");
        assert.deepEqual(bridge.sent.at(-1), { name: "storage.remove", args: { key: "k" } });
    });

    it("cached starts from storage and replaces it with the fetched value", async () => {
        bridge.queries["storage.get"] = "old";
        bridge.queries["storage.set"] = null;
        let data: () => string | undefined = () => undefined;
        tinyui().mount(() => { [data] = cached<string>("c", () => Promise.resolve("new")); return h(Text, { text: "x" }); }, "{}", HOST);
        assert.equal(data(), "old");
        await drain();
        assert.equal(data(), "new");
    });

    it("cached keeps only the newest run and turns a throwing fetcher into its error", async () => {
        bridge.queries["storage.get"] = null;
        bridge.queries["storage.set"] = null;
        const resolvers: ((v: string) => void)[] = [];
        let first = true;
        let data: () => string | undefined = () => undefined;
        let actions: { error: () => unknown; refetch: () => void } | undefined;
        tinyui().mount(() => {
            [data, actions] = cached<string>("r", () => {
                if (first) { first = false; throw new Error("sync"); }
                return new Promise<string>((r) => resolvers.push(r));
            });
            return h(Text, { text: "x" });
        }, "{}", HOST);
        await drain();
        assert.equal((actions!.error() as Error).message, "sync");
        actions!.refetch();
        actions!.refetch();
        resolvers[1]!("newer");
        resolvers[0]!("older");
        await drain();
        assert.equal(data(), "newer");
    });

    it("i18n.t fills placeholders and re-runs bindings when the locale changes", () => {
        bridge.queries["i18n.locale"] = "zh";
        bridge.queries["i18n.t"] = "省 {percent}";
        tinyui().mount(() => h(Text, { text: thunk(() => `${i18n.locale()}:${i18n.t("savings", { percent: "43%" })}`) }), "{}", HOST);
        tinyui().flush();
        assert.deepEqual(bridge.applied.at(-1)!.filter((o) => o[0] === "p"), [["p", 1, "text", "zh:省 43%"]]);
        bridge.queries["i18n.t"] = "Save {percent}";
        tinyui().emit("i18n.locale", JSON.stringify({ locale: "en" }));
        tinyui().flush();
        assert.deepEqual(bridge.applied.at(-1), [["p", 1, "text", "en:Save 43%"]]);
    });

    it("session.state follows K5 and signIn is a J3", () => {
        bridge.queries["session.get"] = { loggedIn: false, userId: null };
        tinyui().mount(() => h(Text, { text: thunk(() => String(session.state().loggedIn)) }), "{}", HOST);
        tinyui().flush();
        assert.deepEqual(bridge.sent.at(-1), { name: "session.subscribe", args: {} });
        tinyui().emit("session", JSON.stringify({ loggedIn: true, userId: "u1" }));
        tinyui().flush();
        assert.deepEqual(bridge.applied.at(-1), [["p", 1, "text", "true"]]);
        void session.signIn("paywall");
        assert.deepEqual([bridge.calls.at(-1)!.name, bridge.calls.at(-1)!.args], ["session.signIn", { source: "paywall" }]);
    });

    it("ui, linking and analytics map to their bridge names", () => {
        void ui.toast("hi", { action: "undo" });
        assert.deepEqual([bridge.calls.at(-1)!.name, bridge.calls.at(-1)!.args], ["ui.toast", { message: "hi", action: "undo" }]);
        void ui.confirm({ message: "sure?" });
        assert.equal(bridge.calls.at(-1)!.name, "ui.confirm");
        void linking.openUrl("https://x");
        assert.deepEqual([bridge.calls.at(-1)!.name, bridge.calls.at(-1)!.args], ["linking.openUrl", { url: "https://x" }]);
        analytics.track("checkout_step", { step: "plan_selected", plan: "annual" });
        assert.deepEqual(bridge.sent.at(-1), { name: "analytics.track", args: { name: "checkout_step", props: { step: "plan_selected", plan: "annual" } } });
        assert.throws(() => analytics.track("x", { nested: {} as unknown as string }), (e: HostError) => e.code === "E_INVALID");
    });

    it("host.call is a J3 under the host's own name", async () => {
        const p = host.call<{ url: string }>("checkout.start", { plan: "annual" });
        const call = bridge.calls.at(-1)!;
        assert.deepEqual([call.name, call.args], ["checkout.start", { plan: "annual" }]);
        tinyui().resolve(call.cbId, JSON.stringify({ url: "https://pay" }));
        await drain();
        assert.deepEqual(await p, { url: "https://pay" });
    });

    it("store.watch reads the snapshot, subscribes, and follows K5", () => {
        bridge.queries["store.get"] = { n: 1 };
        tinyui().mount(() => {
            const cart = store.watch<{ n: number }>("cart"); // at render, like any subscription
            return h(Column, null, h(Text, { text: thunk(() => String((cart() ?? { n: 0 }).n)) }));
        }, "{}", HOST);
        tinyui().flush();
        assert.deepEqual(bridge.sent.at(-1), { name: "store.subscribe", args: { key: "cart" } });
        assert.deepEqual(bridge.applied.at(-1)!.filter((o) => o[0] === "p"), [["p", 1, "text", "1"]]);
        tinyui().emit("store:cart", JSON.stringify({ value: { n: 5 } }));
        tinyui().flush();
        assert.deepEqual(bridge.applied.at(-1), [["p", 1, "text", "5"]]);
        store.set("cart", { n: 6 });
        assert.deepEqual(bridge.sent.at(-1), { name: "store.set", args: { key: "cart", value: '{"n":6}' } });
    });

    it("events.on subscribes once per handler and navigation is fire-and-forget", () => {
        const seen: unknown[] = [];
        tinyui().mount(() => { events.on("net", (p) => seen.push(p)); navigation.onResult((r) => seen.push(r)); return h(Text, { text: "x" }); }, "{}", HOST);
        tinyui().flush();
        assert.deepEqual(bridge.sent.at(-1), { name: "events.subscribe", args: { topic: "net" } });
        tinyui().emit("net", JSON.stringify({ online: true }));
        tinyui().emit("navigation.result", JSON.stringify({ result: 42, from: "pages/x" }));
        assert.deepEqual(seen, [{ online: true }, 42]);
        navigation.push("pages/detail", { id: 1 });
        assert.deepEqual(bridge.sent.at(-1), { name: "navigation.push", args: { page: "pages/detail", params: { id: 1 } } });
    });
});

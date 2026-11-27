import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { UpdatesClient, UpdatesError } from "../src/client.ts";

/** A `fetch` that plays [script] in order: an Error is thrown as a request that got no answer, a Response is returned. */
function scripted(script: (Error | Response)[]) {
    const calls: { method: string; url: string }[] = [];
    const fetch: typeof globalThis.fetch = async (input, init) => {
        calls.push({ method: init?.method ?? "GET", url: String(input) });
        const next = script.shift();
        if (next === undefined) throw new Error("the script ran out");
        if (next instanceof Error) throw next;
        return next;
    };
    return { fetch, calls };
}

function dropped(code = "ECONNRESET"): Error {
    return new TypeError("fetch failed", { cause: Object.assign(new Error("socket hang up"), { code }) });
}

function ok(body: unknown = {}): Response {
    return new Response(JSON.stringify(body), { status: 200, headers: { "content-type": "application/json" } });
}

describe("requests that get no answer", () => {
    const url = "https://updates.example.com";
    const bytes = new Uint8Array([1, 2, 3]);

    it("sends an upload again and says so", async () => {
        const { fetch, calls } = scripted([dropped(), ok({ path: "pages/home.jsb", sha256: "abc", existing: false })]);
        const retries: string[] = [];
        const client = new UpdatesClient({ url, token: "t", fetch, onRetry: (m) => retries.push(m), retryDelaysMs: [0, 0] });
        const result = await client.upload("/shop/login/3/v1/pages/home.jsb", bytes);
        assert.equal(result.path, "pages/home.jsb");
        assert.equal(calls.length, 2);
        assert.deepEqual(retries, ["PUT /shop/login/3/v1/pages/home.jsb: fetch failed (ECONNRESET); trying again (2 of 3)"]);
    });

    it("gives up after the last attempt, naming the request", async () => {
        const { fetch, calls } = scripted([dropped(), dropped(), dropped("UND_ERR_SOCKET")]);
        const client = new UpdatesClient({ url, token: "t", fetch, retryDelaysMs: [0, 0] });
        await assert.rejects(
            () => client.bytes("/apps/shop/hosts/3"),
            (e: Error) => e.message === "GET /apps/shop/hosts/3: fetch failed (UND_ERR_SOCKET), after 3 attempts" && e.cause instanceof TypeError,
        );
        assert.equal(calls.length, 3);
    });

    it("does not send a POST again: the first one may have issued the token", async () => {
        const { fetch, calls } = scripted([dropped(), ok()]);
        const client = new UpdatesClient({ url, token: "t", fetch, retryDelaysMs: [0, 0] });
        await assert.rejects(() => client.json("POST", "/apps/shop/packages/login/tokens", { channels: ["staging"] }), /^Error: POST \/apps\/shop\/packages\/login\/tokens: fetch failed \(ECONNRESET\)$/);
        assert.equal(calls.length, 1);
    });

    it("takes an answer as final, whatever its status", async () => {
        const { fetch, calls } = scripted([new Response(JSON.stringify({ error: "the pointer only moves forward" }), { status: 409 }), ok()]);
        const client = new UpdatesClient({ url, token: "t", fetch, retryDelaysMs: [0, 0] });
        await assert.rejects(
            () => client.json("PUT", "/shop/staging/login/3/current.json", {}),
            (e: Error) => e instanceof UpdatesError && e.status === 409,
        );
        assert.equal(calls.length, 1);
    });
});

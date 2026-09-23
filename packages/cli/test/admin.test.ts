import assert from "node:assert/strict";
import { after, before, describe, it } from "node:test";
import { createApp, createAppToken, createPackage, createToken, listReleases, movePointer, revokeAppToken, revokeToken, rotatePublicKey, uploadHostSnapshot } from "../src/admin.ts";
import { ADMIN_TOKEN_ENV, DEFAULT_URL, requireToken, resolveUrl, segments, TOKEN_ENV, UpdatesClient, URL_ENV } from "../src/client.ts";
import { requireName } from "../src/config.ts";
import { startFakeServer, type FakeServer } from "./helpers.ts";

describe("management commands", () => {
    let server: FakeServer;
    let client: UpdatesClient;

    before(async () => {
        server = await startFakeServer();
        client = new UpdatesClient({ url: server.url, token: "admin-token" });
    });
    after(() => server.close());

    function last() {
        const request = server.requests.at(-1)!;
        return { method: request.method, path: request.path, auth: request.auth, body: request.body.length === 0 ? null : (JSON.parse(request.body.toString("utf8")) as Record<string, unknown>) };
    }

    it("creates an app, a package and rotates its key", async () => {
        const app = await createApp(client, { id: "demo", name: "Demo", org: "tiny-ui" });
        assert.equal(app.id, "demo");
        assert.deepEqual(last(), { method: "POST", path: "/apps", auth: "Bearer admin-token", body: { id: "demo", name: "Demo", org: "tiny-ui" } });

        await createPackage(client, "demo", { name: "fixture", publicKey: "BASE64" });
        assert.equal(last().path, "/apps/demo/packages");
        assert.deepEqual(last().body, { name: "fixture", publicKey: "BASE64" });

        // five segments, same shape as an upload path: the management route has to win
        const rotated = await rotatePublicKey(client, "demo", "fixture", "ROTATED");
        assert.equal(last().method, "PUT");
        assert.equal(last().path, "/apps/demo/packages/fixture/publicKey");
        assert.deepEqual(last().body, { publicKey: "ROTATED" });
        assert.equal(rotated.name, "fixture");
        assert.equal(rotated.publicKey, "ROTATED");
        assert.ok(rotated.publicKeyUpdatedAt);
    });

    it("issues a token for the named channels and revokes it by id", async () => {
        const issued = await createToken(client, "demo", "fixture", ["staging", "production"]);
        assert.equal(issued.token, "publish-token-shown-once");
        assert.equal(last().path, "/apps/demo/packages/fixture/tokens");
        assert.deepEqual(last().body, { channels: ["staging", "production"] });

        await revokeToken(client, "demo", "fixture", "tok_1");
        assert.deepEqual(last(), { method: "DELETE", path: "/apps/demo/packages/fixture/tokens/tok_1", auth: "Bearer admin-token", body: null });
    });

    it("lists releases and moves the pointer for a rollback, a promotion and a rollout change", async () => {
        const releases = await listReleases(client, "demo", "fixture", "1");
        assert.equal(last().path, "/demo/fixture/1/releases");
        assert.equal(releases.versions.length, 2);
        assert.equal(releases.channels["staging"]?.rollout, 20);

        const target = { app: "demo", pkg: "fixture", hostVersion: "1" };
        const rolledBack = await movePointer(client, { ...target, channel: "production" }, { version: "20260921T080000Z-aaaaaa" });
        assert.equal(last().path, "/demo/production/fixture/1/pointer");
        assert.equal(rolledBack.version, "20260921T080000Z-aaaaaa");

        await movePointer(client, { ...target, channel: "staging" }, { rollout: 50 });
        assert.deepEqual(last().body, { rollout: 50 });
    });

    it("issues and revokes app tokens, and uploads a host snapshot once", async () => {
        const issued = await createAppToken(client, "demo");
        assert.equal(issued.token, "app-token-shown-once");
        assert.deepEqual(last(), { method: "POST", path: "/apps/demo/tokens", auth: "Bearer admin-token", body: null });
        await revokeAppToken(client, "demo", "app_1");
        assert.equal(last().method, "DELETE");
        assert.equal(last().path, "/apps/demo/tokens/app_1");

        const snapshot = new TextEncoder().encode("hostVersion 2\ncapabilities\n  checkout.start\n");
        const first = await uploadHostSnapshot(client, "demo", "2", snapshot);
        assert.equal(first.existing, false);
        const request = server.requests.at(-1)!;
        assert.equal(request.path, "/apps/demo/hosts/2");
        assert.deepEqual(new Uint8Array(request.body), snapshot);
        assert.equal((await uploadHostSnapshot(client, "demo", "2", snapshot)).existing, true);
        await assert.rejects(uploadHostSnapshot(client, "demo", "2", new TextEncoder().encode("other")), /409 a shipped host version is frozen/);
    });

    it("encodes each segment and holds names to the rule the server enforces", () => {
        assert.equal(segments("demo", "a/b", "1"), "/demo/a%2Fb/1");
        // page paths are ASCII by construction (tinyui build refuses the rest); encoding is the last line of defence
        assert.equal(segments("demo", "fixture", "1", "订单.bin"), "/demo/fixture/1/%E8%AE%A2%E5%8D%95.bin");
        // encodeURIComponent leaves dots alone, so a traversing value is refused rather than escaped
        for (const traversal of ["..", ".", ""]) assert.throws(() => segments("demo", traversal, "1"), /cannot be a path segment/);
        assert.throws(() => requireName("--app", ".."), /--app must match \[a-z0-9-\]\+/);
        assert.throws(() => requireName("--channel", "Production"), /--channel must match/);
        assert.equal(requireName("--app", "trendingai"), "trendingai");
    });

    it("takes the instance from --url, then the environment, then the hosted default", () => {
        const saved = process.env[URL_ENV];
        try {
            delete process.env[URL_ENV];
            assert.equal(resolveUrl(undefined), DEFAULT_URL);
            assert.equal(resolveUrl("https://ota.example.com"), "https://ota.example.com");
            process.env[URL_ENV] = "https://from-env.example.com";
            assert.equal(resolveUrl(undefined), "https://from-env.example.com");
            assert.equal(resolveUrl("https://ota.example.com"), "https://ota.example.com");
        } finally {
            if (saved === undefined) delete process.env[URL_ENV];
            else process.env[URL_ENV] = saved;
        }
    });

    it("names the environment variable a command needs instead of taking a token on the command line", () => {
        for (const variable of [TOKEN_ENV, ADMIN_TOKEN_ENV] as const) {
            const saved = process.env[variable];
            try {
                delete process.env[variable];
                assert.throws(() => requireToken(variable), new RegExp(`${variable} is not set`));
                process.env[variable] = "from-env";
                assert.equal(requireToken(variable), "from-env");
            } finally {
                if (saved === undefined) delete process.env[variable];
                else process.env[variable] = saved;
            }
        }
    });

    it("refuses to carry a token over plain http unless the instance is loopback", () => {
        assert.throws(() => resolveUrl("http://updates.example.com"), /cleartext/);
        assert.throws(() => new UpdatesClient({ url: "http://updates.example.com", token: "t" }), /cleartext/);
        assert.throws(() => resolveUrl("not-a-url"), /not an instance url/);
        assert.equal(resolveUrl("https://updates.example.com"), "https://updates.example.com");
        assert.equal(resolveUrl("http://localhost:8787"), "http://localhost:8787");
        assert.equal(resolveUrl("http://127.0.0.1:8787"), "http://127.0.0.1:8787");
    });

    it("strips a trailing slash off the instance url", async () => {
        const trailing = new UpdatesClient({ url: `${server.url}/`, token: "admin-token" });
        await createApp(trailing, { id: "demo2", name: "Demo 2" });
        assert.equal(last().path, "/apps");
    });
});

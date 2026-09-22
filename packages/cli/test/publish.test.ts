import assert from "node:assert/strict";
import { cp, mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { after, before, describe, it } from "node:test";
import { bundle } from "../src/bundle.ts";
import { UpdatesClient, UpdatesError } from "../src/client.ts";
import { generateKeyPair } from "../src/keys.ts";
import { publish } from "../src/publish.ts";
import { fakeDist, startFakeServer, type FakeServer } from "./helpers.ts";

const pair = generateKeyPair();
const VERSION = "20260922T090000Z-3f2a1c";
const FILES = ["runtime/core.bin", "runtime/native.bin", "pages/home.bin", "pages/orders.bin", "manifest.json"];

describe("tinyui publish", () => {
    let tmp: string;
    let signingKey: string;
    let server: FakeServer;
    let client: UpdatesClient;

    before(async () => {
        tmp = await mkdtemp(join(tmpdir(), "tinyui-publish-"));
        signingKey = join(tmp, "signing-key.pem");
        await writeFile(signingKey, pair.privateKeyPem);
        server = await startFakeServer();
        client = new UpdatesClient({ url: server.url, token: "publish-token" });
    });
    after(async () => {
        await server.close();
        await rm(tmp, { recursive: true, force: true });
    });

    /** One signed bundle under `<tmp>/<label>/dist/ota`, as `tinyui bundle` writes it. */
    async function bundled(label: string, edit?: Parameters<typeof fakeDist>[2], rollout = 100): Promise<string> {
        const dist = await fakeDist(join(tmp, label, "dist"), pair.publicKey, edit);
        const result = await bundle({ dist, runtimeVersion: "1", signingKey, rollout });
        return join(dist, "ota");
    }

    function requestsSince(mark: number) {
        return server.requests.slice(mark).map((r) => `${r.method} ${r.path}`);
    }

    it("uploads every file of the version and moves the pointer last", async () => {
        const mark = server.requests.length;
        const result = await publish({ client, dir: await bundled("whole"), app: "demo", channel: "staging" });

        assert.equal(result.pkg, "fixture");
        assert.equal(result.runtimeVersion, "1");
        assert.equal(result.version, VERSION);
        assert.equal(result.uploaded, FILES.length);
        assert.equal(result.existing, 0);

        const sent = requestsSince(mark);
        assert.deepEqual(
            sent.slice(0, -1).sort(),
            FILES.map((f) => `PUT /demo/fixture/1/${VERSION}/${f}`).sort(),
        );
        assert.equal(sent.at(-1), "PUT /demo/staging/fixture/1/current.json");

        const pointer = server.requests.at(-1)!;
        assert.equal(pointer.auth, "Bearer publish-token");
        const body = JSON.parse(pointer.body.toString("utf8")) as { version: string; rollout: number; signature: string };
        assert.equal(body.version, VERSION);
        assert.equal(body.rollout, 100);
        // the signature travels in the pointer, never recomputed here (docs/updates.md §7)
        const bundledPointer = JSON.parse(await readFile(join(tmp, "whole", "dist", "ota", "fixture", "1", "current.json"), "utf8")) as { signature: string };
        assert.equal(body.signature, bundledPointer.signature);
    });

    it("uploads the manifest bytes that were signed, with a content-length the server can check", async () => {
        const mark = server.requests.length;
        const dir = await bundled("bytes");
        await publish({ client, dir, app: "demo", channel: "staging" });
        const upload = server.requests.slice(mark).find((r) => r.path.endsWith("/manifest.json") && r.method === "PUT")!;
        const onDisk = await readFile(join(dir, "fixture", "1", VERSION, "manifest.json"));
        assert.deepEqual(upload.body, onDisk);
        assert.equal(upload.contentLength, String(onDisk.byteLength));
        assert.equal(upload.contentType, "application/octet-stream");
    });

    it("re-publishing the same version uploads nothing and still moves the pointer", async () => {
        const dir = await bundled("again");
        await publish({ client, dir, app: "demo", channel: "staging" });
        const mark = server.requests.length;
        const result = await publish({ client, dir, app: "demo", channel: "production" });
        assert.equal(result.uploaded, 0);
        assert.equal(result.existing, FILES.length);
        assert.equal(requestsSince(mark).at(-1), "PUT /demo/production/fixture/1/current.json");
    });

    it("--rollout overrides the percentage the bundle was signed with", async () => {
        const dir = await bundled("rollout", undefined, 100);
        await publish({ client, dir, app: "demo", channel: "staging", rollout: 5 });
        const body = JSON.parse(server.requests.at(-1)!.body.toString("utf8")) as { rollout: number };
        assert.equal(body.rollout, 5);
        await assert.rejects(publish({ client, dir, app: "demo", channel: "staging", rollout: 101 }), /rollout must be an integer from 0 to 100/);
    });

    it("finds the single bundle under a root and names them all when there are several", async () => {
        const root = join(tmp, "multi", "ota");
        await cp(join(await bundled("one"), "fixture"), join(root, "fixture"), { recursive: true });
        const result = await publish({ client, dir: root, app: "demo", channel: "staging" });
        assert.equal(result.pkg, "fixture");

        const second = await fakeDist(join(tmp, "multi-two", "dist"), pair.publicKey, (m) => {
            m.name = "extra";
            m.pages = m.pages.map((page) => page.replace("fixture/", "extra/"));
            for (const table of [m.files, m.hashes, m.buildIds]) {
                for (const [module, value] of Object.entries(table)) {
                    if (!module.startsWith("fixture/")) continue;
                    table[module.replace("fixture/", "extra/")] = value;
                    delete table[module];
                }
            }
        });
        await bundle({ dist: second, runtimeVersion: "1", signingKey });
        await cp(join(second, "ota", "extra"), join(root, "extra"), { recursive: true });
        await assert.rejects(publish({ client, dir: root, app: "demo", channel: "staging" }), /holds more than one bundle/);
    });

    it("takes the package and runtime version from the manifest, not from the directory name", async () => {
        const renamed = join(tmp, "artifacts", "build-1234", "1");
        await cp(join(await bundled("renamed"), "fixture", "1"), renamed, { recursive: true });
        const mark = server.requests.length;
        const result = await publish({ client, dir: renamed, app: "demo", channel: "staging" });
        assert.equal(result.pkg, "fixture");
        assert.ok(requestsSince(mark).every((r) => r.includes("/demo/fixture/1/") || r === "PUT /demo/staging/fixture/1/current.json"));
    });

    it("checks the pointer against the manifest locally, before uploading anything", async () => {
        const dir = await bundled("mismatch");
        const pointerFile = join(dir, "fixture", "1", "current.json");
        const pointer = JSON.parse(await readFile(pointerFile, "utf8")) as Record<string, unknown>;
        await writeFile(pointerFile, JSON.stringify({ ...pointer, version: "20260101T000000Z-000000" }));
        const mark = server.requests.length;
        await assert.rejects(publish({ client, dir, app: "demo", channel: "staging" }), /points at a version that was not bundled here/);
        assert.deepEqual(requestsSince(mark), []);

        await writeFile(pointerFile, JSON.stringify({ rollout: 100 }));
        await assert.rejects(publish({ client, dir, app: "demo", channel: "staging" }), /is not a pointer file/);
        await rm(pointerFile);
        await assert.rejects(publish({ client, dir, app: "demo", channel: "staging" }), /no current.json under/);
    });

    it("refuses a bundle whose own paths would read outside it, and a concurrency that uploads nothing", async () => {
        const dir = await bundled("traversal");
        const manifestFile = join(dir, "fixture", "1", VERSION, "manifest.json");
        const manifest = JSON.parse(await readFile(manifestFile, "utf8")) as { files: Record<string, string> };
        await writeFile(manifestFile, JSON.stringify({ ...manifest, files: { ...manifest.files, "fixture/home": "../../../../etc/passwd" } }));
        const mark = server.requests.length;
        await assert.rejects(publish({ client, dir, app: "demo", channel: "staging" }), /every segment must match/);
        assert.deepEqual(requestsSince(mark), [], "nothing outside the bundle is read, let alone uploaded");

        const pointerFile = join(dir, "fixture", "1", "current.json");
        const pointer = JSON.parse(await readFile(pointerFile, "utf8")) as Record<string, unknown>;
        await writeFile(pointerFile, JSON.stringify({ ...pointer, version: ".." }));
        await assert.rejects(publish({ client, dir, app: "demo", channel: "staging" }), /is not a single path segment/);

        // zero workers would upload nothing and still move the pointer
        await assert.rejects(publish({ client, dir: await bundled("workers"), app: "demo", channel: "staging", concurrency: 0 }), /concurrency must be a positive integer/);
        assert.deepEqual(requestsSince(mark), []);
    });

    it("passes the server's own words through", async () => {
        const dir = await bundled("rejected");
        server.reply(`/demo/staging/fixture/1/current.json`, { status: 409, body: { error: "manifest name fixture is not the path package other" } });
        const failure = await publish({ client, dir, app: "demo", channel: "staging" }).then(
            () => null,
            (e: unknown) => e as UpdatesError,
        );
        assert.ok(failure instanceof UpdatesError);
        assert.equal(failure.status, 409);
        assert.match(failure.message, /409 manifest name fixture is not the path package other/);

        const anonymous = new UpdatesClient({ url: server.url, token: "" });
        await assert.rejects(publish({ client: anonymous, dir, app: "demo", channel: "staging" }), /401 a token is required/);
    });
});

import assert from "node:assert/strict";
import { mkdir, mkdtemp, readdir, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { after, before, describe, it } from "node:test";
import { bundle } from "../src/bundle.ts";
import { generateKeyPair } from "../src/keys.ts";
import { pull } from "../src/pull.ts";
import { fakeDist } from "./helpers.ts";

const pair = generateKeyPair();
const URL = "https://updates.example";

describe("tinyui pull", () => {
    let tmp: string;
    let hostDir: string;
    let requests: string[];

    /** The delivery endpoints of docs/updates.md §2 over one bundled directory: `<url>/demo/production/<pkg>/<hostVersion>/…`. */
    const fetch: typeof globalThis.fetch = async (input) => {
        const url = String(input);
        requests.push(url);
        const prefix = `${URL}/demo/production/fixture/1/`;
        if (!url.startsWith(prefix)) return new Response("not found", { status: 404 });
        try {
            return new Response(await readFile(join(hostDir, url.slice(prefix.length))));
        } catch {
            return new Response("not found", { status: 404 });
        }
    };

    const options = (out: string) => ({ url: URL, app: "demo", channel: "production", pkg: "fixture", hostVersion: "1", publicKey: pair.publicKey, out, fetch });

    before(async () => {
        tmp = await mkdtemp(join(tmpdir(), "tinyui-pull-"));
        const signingKey = join(tmp, "key.pem");
        await writeFile(signingKey, pair.privateKeyPem);
        const dist = await fakeDist(join(tmp, "dist"), pair.publicKey);
        hostDir = (await bundle({ dist, hostVersion: "1", signingKey })).dir;
    });
    after(async () => {
        await rm(tmp, { recursive: true, force: true });
    });

    it("writes the pointed version in the build layout, replacing what was there", async () => {
        requests = [];
        const out = join(tmp, "embedded");
        await mkdir(join(out, "pages"), { recursive: true });
        await writeFile(join(out, "pages", "removed.bin"), "stale");

        const result = await pull(options(out));

        assert.equal(result.version, "20260922T090000Z-3f2a1c");
        assert.deepEqual(result.files, ["manifest.json", "pages/home.bin", "pages/orders.bin", "runtime/core.bin", "runtime/native.bin"]);
        assert.deepEqual((await readdir(join(out, "pages"))).sort(), ["home.bin", "orders.bin"]);
        const served = join(hostDir, result.version);
        assert.deepEqual(await readFile(join(out, "manifest.json")), await readFile(join(served, "manifest.json")), "the signed bytes, untouched");
        assert.deepEqual(await readFile(join(out, "pages/home.bin")), await readFile(join(served, "pages/home.bin")));
        assert.ok(requests.every((r) => r.startsWith(`${URL}/demo/production/fixture/1/`)));
    });

    it("writes nothing when the signature, a hash or the host version is off", async () => {
        const out = join(tmp, "kept");
        await mkdir(out, { recursive: true });
        await writeFile(join(out, "marker"), "untouched");

        const other = generateKeyPair();
        await assert.rejects(pull({ ...options(out), publicKey: other.publicKey }), /does not verify/);

        const page = join(hostDir, "20260922T090000Z-3f2a1c", "pages", "home.bin");
        const original = await readFile(page);
        await writeFile(page, "tampered");
        try {
            await assert.rejects(pull(options(out)), /pages\/home\.bin does not match manifest\.hashes/);
        } finally {
            await writeFile(page, original);
        }

        await assert.rejects(pull({ ...options(out), hostVersion: "2" }), /404/);
        assert.equal(await readFile(join(out, "marker"), "utf8"), "untouched");
    });
});

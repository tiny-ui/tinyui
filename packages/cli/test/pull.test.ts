import assert from "node:assert/strict";
import { mkdtemp, readdir, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { after, before, describe, it } from "node:test";
import { bundle } from "../src/bundle.ts";
import { generateKeyPair } from "../src/keys.ts";
import { pull } from "../src/pull.ts";
import { fakeDist } from "./helpers.ts";

const pair = generateKeyPair();
const URL = "https://updates.example";
const OLD = "20260922T090000Z-3f2a1c";
const NEW = "20260923T090000Z-4b3c2d";

describe("tinyui pull", () => {
    let tmp: string;
    /** `<tmp>/server/fixture/1`: bundles as the delivery endpoints serve them; the pointer is written per test. */
    let hostDir: string;

    const fetch: typeof globalThis.fetch = async (input) => {
        const prefix = `${URL}/demo/production/fixture/1/`;
        const url = String(input);
        if (!url.startsWith(prefix)) return new Response("not found", { status: 404 });
        try {
            return new Response(await readFile(join(hostDir, url.slice(prefix.length))));
        } catch {
            return new Response("not found", { status: 404 });
        }
    };

    async function publish(version: string, createdAt: string, key = pair, rollout = 100): Promise<void> {
        const signingKey = join(tmp, `${version}.pem`);
        await writeFile(signingKey, key.privateKeyPem);
        const dist = await fakeDist(join(tmp, version), key.publicKey, (m) => {
            m.version = version;
            m.createdAt = createdAt;
        });
        const result = await bundle({ dist, hostVersion: "1", signingKey, rollout, out: join(tmp, "server") });
        hostDir = result.dir;
    }

    const into = (out: string, extra: object = {}) => ({ url: URL, app: "demo", channel: "production", hostVersion: "1", out, fetch, ...extra });

    before(async () => {
        tmp = await mkdtemp(join(tmpdir(), "tinyui-pull-"));
    });
    after(async () => {
        await rm(tmp, { recursive: true, force: true });
    });

    it("needs the package and its key for the first pull, then trusts what it embedded", async () => {
        await publish(OLD, "2026-09-22T09:00:00Z");
        const out = join(tmp, "embedded");
        await assert.rejects(pull(into(out)), /pass --pkg and --accept-key/);

        const first = await pull(into(out, { pkg: "fixture", acceptKey: pair.publicKey }));
        assert.deepEqual(first, { version: OLD, changed: true });
        assert.deepEqual((await readdir(out)).sort(), ["manifest.json", "pages", "runtime"]);
        assert.deepEqual(await readFile(join(out, "manifest.json")), await readFile(join(hostDir, OLD, "manifest.json")), "the signed bytes, untouched");
        assert.equal((await pull(into(out))).reason, `already ${OLD}`);

        await publish(NEW, "2026-09-23T09:00:00Z");
        assert.deepEqual(await pull(into(out)), { version: NEW, changed: true });
        assert.deepEqual((await readdir(join(out, "pages"))).sort(), ["home.bin", "orders.bin"]);
    });

    it("keeps the embedded package while the channel rolls out, or when it is not newer", async () => {
        await publish(NEW, "2026-09-23T09:00:00Z");
        const out = join(tmp, "kept");
        await pull(into(out, { pkg: "fixture", acceptKey: pair.publicKey }));

        await publish("20260924T090000Z-5c4d3e", "2026-09-24T09:00:00Z", pair, 50);
        assert.deepEqual(await pull(into(out)), { version: NEW, changed: false, reason: "production is at 20260924T090000Z-5c4d3e rolling out to 50%" });
        await assert.rejects(pull(into(join(tmp, "empty"), { pkg: "fixture", acceptKey: pair.publicKey })), /holds nothing for host version 1 to keep/);

        await publish("20260921T090000Z-000000", "2026-09-21T09:00:00Z");
        assert.equal((await pull(into(out))).changed, false, "an older version never replaces the floor");
    });

    it("refuses a package signed with another key unless that key is accepted, and writes nothing on a bad file", async () => {
        const out = join(tmp, "trusted");
        await publish(OLD, "2026-09-22T09:00:00Z");
        await pull(into(out, { pkg: "fixture", acceptKey: pair.publicKey }));

        const rotated = generateKeyPair();
        const ROTATED = "20260925T090000Z-6d5e4f";
        await publish(ROTATED, "2026-09-25T09:00:00Z", rotated);
        await assert.rejects(pull(into(out)), (e: Error) => e.message.includes(`pass --accept-key ${rotated.publicKey}`));
        assert.equal(JSON.parse(await readFile(join(out, "manifest.json"), "utf8")).version, OLD);

        const page = join(hostDir, ROTATED, "pages", "home.bin");
        await writeFile(page, "tampered");
        await assert.rejects(pull(into(out, { acceptKey: rotated.publicKey })), /pages\/home\.bin does not match manifest\.hashes/);
        assert.equal(JSON.parse(await readFile(join(out, "manifest.json"), "utf8")).version, OLD);
    });
});

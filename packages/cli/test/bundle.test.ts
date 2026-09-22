import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { mkdir, mkdtemp, readFile, readdir, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { after, before, describe, it } from "node:test";
import { build, type Manifest } from "../src/build.ts";
import { bundle, type Pointer } from "../src/bundle.ts";
import { generateKeyPair, verify } from "../src/keys.ts";
import { findQjsc } from "../src/qjsc.ts";

const fixtures = join(import.meta.dirname, "fixtures");
const signingKey = join(fixtures, "keys", "signing-key.pem");
const config = JSON.parse(await readFile(join(fixtures, "app", "tinyui.config.json"), "utf8")) as { name: string; publicKey: string };
const qjsc = await findQjsc();

/** A `tinyui build` output written by hand: bundle only needs the manifest and the `.bin` files it lists. */
async function fakeDist(dir: string, edit: (m: Manifest) => void = () => {}): Promise<string> {
    const files = { "tinyui-core": "runtime/core", "tinyui-native": "runtime/native", "fixture/home": "pages/home", "fixture/订单": "pages/订单" };
    const hashes: Record<string, string> = {};
    for (const [module, path] of Object.entries(files)) {
        const bytes = Buffer.concat([Buffer.from("QJKB"), Buffer.alloc(8), Buffer.from("a".repeat(40)), Buffer.from(module)]);
        await mkdir(join(dir, path, ".."), { recursive: true });
        await writeFile(join(dir, path + ".bin"), bytes);
        hashes[module] = createHash("sha256").update(bytes).digest("hex");
    }
    const manifest: Manifest = {
        runtime: ["tinyui-core", "tinyui-native"],
        pages: ["fixture/home", "fixture/订单"],
        files,
        buildIds: Object.fromEntries(Object.keys(files).map((m) => [m, "00000000"])),
        name: config.name,
        publicKey: config.publicKey,
        version: "20260922T090000Z-3f2a1c",
        createdAt: "2026-09-22T09:00:00Z",
        engine: "a".repeat(40),
        protocol: 1,
        hashes,
    };
    edit(manifest);
    await writeFile(join(dir, "manifest.json"), JSON.stringify(manifest, null, 2) + "\n");
    return dir;
}

describe("tinyui bundle", () => {
    let tmp: string;
    before(async () => { tmp = await mkdtemp(join(tmpdir(), "tinyui-bundle-")); });
    after(() => rm(tmp, { recursive: true, force: true }));

    it("writes current.json next to an immutable <version>/ holding manifest.json and the bytecode", async () => {
        const dist = await fakeDist(join(tmp, "dist"));
        const result = await bundle({ dist, runtimeVersion: "1", signingKey, rollout: 30 });
        assert.equal(result.dir, join(dist, "ota", "fixture", "1"));
        assert.equal(result.version, "20260922T090000Z-3f2a1c");
        const versionDir = join(result.dir, result.version);
        assert.deepEqual((await readdir(versionDir, { recursive: true })).filter((f) => f.includes(".")).sort(), [
            "manifest.json",
            "pages/home.bin",
            "pages/订单.bin",
            "runtime/core.bin",
            "runtime/native.bin",
        ]);
        const pointer = JSON.parse(await readFile(result.pointer, "utf8")) as Pointer;
        assert.deepEqual(Object.keys(pointer), ["version", "rollout", "signature"]);
        assert.equal(pointer.version, result.version);
        assert.equal(pointer.rollout, 30);
        const manifest = JSON.parse(await readFile(result.manifest, "utf8")) as Manifest & { runtimeVersion: string };
        assert.equal(manifest.runtimeVersion, "1");
        assert.equal(manifest.name, "fixture");
        assert.ok(!("signature" in manifest) && !("rollout" in manifest), "nothing mutable in the signed file");
    });

    it("signs the raw bytes of <version>/manifest.json, so the public key verifies the file as stored", async () => {
        const dist = await fakeDist(join(tmp, "dist-raw"));
        const result = await bundle({ dist, runtimeVersion: "1", signingKey });
        const pointer = JSON.parse(await readFile(result.pointer, "utf8")) as Pointer;
        const bytes = await readFile(result.manifest);
        assert.ok(verify(config.publicKey, bytes, pointer.signature));
        // non-ASCII module names survive as written: no canonicalization on any side
        assert.match(bytes.toString("utf8"), /"fixture\/订单"/);
        const reserialized = Buffer.from(JSON.stringify(JSON.parse(bytes.toString("utf8"))));
        assert.ok(!verify(config.publicKey, reserialized, pointer.signature), "a re-serialization is a different message");
    });

    it("defaults rollout to 100 and lets it change without touching the signature", async () => {
        const dist = await fakeDist(join(tmp, "dist-rollout"));
        const result = await bundle({ dist, runtimeVersion: "1", signingKey });
        const pointer = JSON.parse(await readFile(result.pointer, "utf8")) as Pointer;
        assert.equal(pointer.rollout, 100);
        pointer.rollout = 5;
        await writeFile(result.pointer, JSON.stringify(pointer));
        const edited = JSON.parse(await readFile(result.pointer, "utf8")) as Pointer;
        assert.ok(verify(config.publicKey, await readFile(result.manifest), edited.signature));
        await assert.rejects(bundle({ dist, runtimeVersion: "1", signingKey, rollout: 101 }), /rollout must be an integer from 0 to 100/);
    });

    it("refuses a signing key that does not match the package's publicKey", async () => {
        const dist = await fakeDist(join(tmp, "dist-key"));
        const other = join(tmp, "other.pem");
        await writeFile(other, generateKeyPair().privateKeyPem);
        await assert.rejects(bundle({ dist, runtimeVersion: "1", signingKey: other }), /does not match the publicKey/);
    });

    it("refuses a build whose bytecode changed since manifest.json was written", async () => {
        const dist = await fakeDist(join(tmp, "dist-stale"));
        await writeFile(join(dist, "pages", "home.bin"), "tampered");
        await assert.rejects(bundle({ dist, runtimeVersion: "1", signingKey }), /does not match manifest.hashes/);
    });

    it("refuses a --js-only build, a manifest from an older CLI and a bad runtime version", async () => {
        const jsOnly = await fakeDist(join(tmp, "dist-jsonly"), (m) => { m.engine = ""; });
        await assert.rejects(bundle({ dist: jsOnly, runtimeVersion: "1", signingKey }), /has no bytecode/);
        const old = await fakeDist(join(tmp, "dist-old"), (m) => { delete (m as Partial<Manifest>).publicKey; });
        await assert.rejects(bundle({ dist: old, runtimeVersion: "1", signingKey }), /has no "publicKey"/);
        const dist = await fakeDist(join(tmp, "dist-rv"));
        await assert.rejects(bundle({ dist, runtimeVersion: "1/2", signingKey }), /runtime version must match/);
    });

    it("bundles a real build end to end", { skip: !qjsc && "qjsc-kmp not found" }, async () => {
        const dist = join(tmp, "real");
        const built = await build({ root: join(fixtures, "app"), out: dist });
        const result = await bundle({ dist, runtimeVersion: "1", signingKey });
        const pointer = JSON.parse(await readFile(result.pointer, "utf8")) as Pointer;
        const bytes = await readFile(result.manifest);
        assert.ok(verify(config.publicKey, bytes, pointer.signature));
        const manifest = JSON.parse(bytes.toString("utf8")) as Manifest;
        for (const m of [...built.runtime, ...built.pages]) {
            const copied = await readFile(join(result.dir, result.version, manifest.files[m.name]! + ".bin"));
            assert.equal(createHash("sha256").update(copied).digest("hex"), manifest.hashes[m.name]);
        }
    });
});

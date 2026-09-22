import assert from "node:assert/strict";
import { createHash, generateKeyPairSync } from "node:crypto";
import { cp, mkdir, mkdtemp, readFile, readdir, rm, stat, symlink, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { after, before, describe, it } from "node:test";
import { build, type Manifest } from "../src/build.ts";
import { bundle, type Pointer } from "../src/bundle.ts";
import { generateKeyPair, verify } from "../src/keys.ts";
import { findQjsc } from "../src/qjsc.ts";
import { fakeDist as writeFakeDist } from "./helpers.ts";

const fixtures = join(import.meta.dirname, "fixtures");
const qjsc = await findQjsc();
// the signing key lives only in this process: nothing under test/ holds a private key
const pair = generateKeyPair();
const config = { name: "fixture", publicKey: pair.publicKey };

const fakeDist = (dir: string, edit?: (m: Manifest) => void) => writeFakeDist(dir, config.publicKey, edit);

describe("tinyui bundle", () => {
    let tmp: string;
    let signingKey: string;
    before(async () => {
        tmp = await mkdtemp(join(tmpdir(), "tinyui-bundle-"));
        signingKey = join(tmp, "signing-key.pem");
        await writeFile(signingKey, pair.privateKeyPem);
    });
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

    it("refuses a signing key that does not match the package's publicKey, or a publicKey that is not P-256", async () => {
        const dist = await fakeDist(join(tmp, "dist-key"));
        const other = join(tmp, "other.pem");
        await writeFile(other, generateKeyPair().privateKeyPem);
        await assert.rejects(bundle({ dist, runtimeVersion: "1", signingKey: other }), /does not match the publicKey/);
        const p384 = generateKeyPairSync("ec", { namedCurve: "P-384" });
        const { x, y } = p384.publicKey.export({ format: "jwk" }) as { x: string; y: string };
        const point = Buffer.concat([Buffer.from([4]), Buffer.from(x, "base64url"), Buffer.from(y, "base64url")]).toString("base64");
        const wrongCurve = await fakeDist(join(tmp, "dist-p384"), (m) => { m.publicKey = point; });
        const p384Key = join(tmp, "p384.pem");
        await writeFile(p384Key, p384.privateKey.export({ type: "pkcs8", format: "pem" }));
        await assert.rejects(bundle({ dist: wrongCurve, runtimeVersion: "1", signingKey: p384Key }), /not a P-256/);
    });

    it("refuses a build whose bytecode changed since manifest.json was written, touching nothing", async () => {
        const dist = await fakeDist(join(tmp, "dist-stale"));
        await writeFile(join(dist, "pages", "home.bin"), "tampered");
        await assert.rejects(bundle({ dist, runtimeVersion: "1", signingKey }), /does not match manifest.hashes/);
        await assert.rejects(readdir(join(dist, "ota")), /ENOENT/, "nothing was written");
    });

    it("keeps a version immutable: the same build again is fine, a different one is refused", async () => {
        const dist = await fakeDist(join(tmp, "dist-immutable"));
        const first = await bundle({ dist, runtimeVersion: "1", signingKey, rollout: 10 });
        const before = await stat(first.manifest);
        const again = await bundle({ dist, runtimeVersion: "1", signingKey, rollout: 90 });
        assert.equal((JSON.parse(await readFile(again.pointer, "utf8")) as Pointer).rollout, 90);
        assert.ok((await readFile(first.manifest)).equals(await readFile(again.manifest)));
        assert.equal((await stat(again.manifest)).mtimeMs, before.mtimeMs, "an identical version is left untouched");
        assert.deepEqual((await readdir(first.dir)).filter((f) => f.endsWith(".tmp")), [], "no staging left behind");
        const rebuilt = await fakeDist(join(tmp, "dist-immutable-2"), (m) => { m.createdAt = "2026-09-22T10:00:00Z"; });
        await assert.rejects(bundle({ dist: rebuilt, runtimeVersion: "1", signingKey, out: join(dist, "ota") }), /already holds a different build/);
        // a directory without a manifest is never cleaned up on the caller's behalf
        const partial = await fakeDist(join(tmp, "dist-partial"));
        await mkdir(join(partial, "ota", "fixture", "1", "20260922T090000Z-3f2a1c", "runtime"), { recursive: true });
        await assert.rejects(bundle({ dist: partial, runtimeVersion: "1", signingKey }), /exists without a manifest.json/);
        assert.deepEqual((await readdir(join(partial, "ota", "fixture", "1"))).filter((f) => f.endsWith(".tmp")), []);
    });

    it("never lets a version, runtime version or file path leave its directory", async () => {
        const escaping = await fakeDist(join(tmp, "dist-escape"), (m) => { m.version = "../../escape"; });
        await assert.rejects(bundle({ dist: escaping, runtimeVersion: "1", signingKey }), /manifest version must be a single path segment/);
        const dot = await fakeDist(join(tmp, "dist-dot"), (m) => { m.version = ".."; });
        await assert.rejects(bundle({ dist: dot, runtimeVersion: "1", signingKey }), /manifest version must be a single path segment/);
        const dist = await fakeDist(join(tmp, "dist-rv-escape"));
        await assert.rejects(bundle({ dist, runtimeVersion: "..", signingKey }), /runtime version must be a single path segment/);
        const name = await fakeDist(join(tmp, "dist-name"), (m) => { m.name = "../pkg"; });
        await assert.rejects(bundle({ dist: name, runtimeVersion: "1", signingKey }), /manifest name must match/);
        const files = await fakeDist(join(tmp, "dist-files"), (m) => { m.files["tinyui-core"] = "../outside/core"; });
        await mkdir(join(tmp, "outside"), { recursive: true });
        await writeFile(join(tmp, "outside", "core.bin"), "x");
        await assert.rejects(bundle({ dist: files, runtimeVersion: "1", signingKey }), /points outside/);
        await assert.rejects(readdir(join(tmp, "escape")), /ENOENT/);
    });

    it("refuses a --js-only build, a manifest from an older CLI and a bad runtime version", async () => {
        const jsOnly = await fakeDist(join(tmp, "dist-jsonly"), (m) => { m.engine = ""; });
        await assert.rejects(bundle({ dist: jsOnly, runtimeVersion: "1", signingKey }), /has no bytecode/);
        const old = await fakeDist(join(tmp, "dist-old"), (m) => { delete (m as Partial<Manifest>).publicKey; });
        await assert.rejects(bundle({ dist: old, runtimeVersion: "1", signingKey }), /has no "publicKey"/);
        const missing = await fakeDist(join(tmp, "dist-missing"), (m) => { delete m.files["tinyui-native"]; });
        await assert.rejects(bundle({ dist: missing, runtimeVersion: "1", signingKey }), /"files" does not cover exactly/);
        const extra = await fakeDist(join(tmp, "dist-extra"), (m) => { m.hashes["ghost"] = "00"; });
        await assert.rejects(bundle({ dist: extra, runtimeVersion: "1", signingKey }), /"hashes" does not cover exactly/);
        const dist = await fakeDist(join(tmp, "dist-rv"));
        await assert.rejects(bundle({ dist, runtimeVersion: "1/2", signingKey }), /runtime version must be a single path segment/);
    });

    it("bundles a real build end to end", { skip: !qjsc && "qjsc-kmp not found" }, async () => {
        const root = join(tmp, "app");
        await cp(join(fixtures, "app"), root, { recursive: true });
        await writeFile(join(root, "tinyui.config.json"), JSON.stringify(config));
        // the copy sits outside the workspace, so it borrows this package's node_modules for tinyui-core / tinyui-native
        await symlink(join(import.meta.dirname, "..", "node_modules"), join(root, "node_modules"));
        const dist = join(tmp, "real");
        const built = await build({ root, out: dist });
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

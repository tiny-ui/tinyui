import assert from "node:assert/strict";
import { mkdir, mkdtemp, readdir, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join, relative, sep } from "node:path";
import { after, before, describe, it } from "node:test";
import { embed } from "../src/embed.ts";
import { findQjsc } from "../src/qjsc.ts";

const root = join(import.meta.dirname, "fixtures", "app");
// an embedded package is bytecode: without an engine build on this machine there is nothing to embed
const qjsc = await findQjsc();

async function tree(dir: string): Promise<string[]> {
    const entries = await readdir(dir, { recursive: true, withFileTypes: true });
    return entries.filter((e) => e.isFile()).map((e) => relative(dir, join(e.parentPath, e.name)).split(sep).join("/")).sort();
}

describe("tinyui embed", { skip: qjsc ? false : "no qjsc-kmp on this machine" }, () => {
    let tmp: string;
    before(async () => { tmp = await mkdtemp(join(tmpdir(), "tinyui-embed-")); });
    after(() => rm(tmp, { recursive: true, force: true }));

    it("writes the manifest, the bytecode and the strings, and nothing else of the build", async () => {
        const out = join(tmp, "host", "fixture");
        await mkdir(join(tmp, "host"));
        const result = await embed({ root, out, dist: join(tmp, "dist") });
        const files = await tree(out);
        assert.deepEqual(files, result.files);
        assert.ok(files.includes("manifest.json"));
        assert.ok(files.some((f) => f.endsWith(".jsb")));
        assert.ok(files.every((f) => f === "manifest.json" || f.endsWith(".jsb") || /^i18n\/.+\.json$/.test(f)), files.join(", "));
        const manifest = JSON.parse(await readFile(join(out, "manifest.json"), "utf8")) as { version: string; pages: string[]; files: Record<string, string> };
        assert.equal(manifest.version, result.version);
        for (const page of manifest.pages) assert.ok(files.includes(`${manifest.files[page]}.jsb`), page);
        assert.deepEqual(await readFile(join(out, "manifest.json")), await readFile(join(tmp, "dist", "manifest.json")), "the manifest is the build's, byte for byte");
    });

    it("--maps adds each page's source map", async () => {
        const out = join(tmp, "host-maps", "fixture");
        await mkdir(join(tmp, "host-maps"));
        const files = (await embed({ root, out, dist: join(tmp, "dist"), maps: true })).files;
        assert.equal(files.filter((f) => f.endsWith(".js.map")).length, files.filter((f) => f.endsWith(".jsb")).length);
    });

    it("replaces the directory as a whole: what an earlier package had does not linger", async () => {
        const out = join(tmp, "host-stale", "fixture");
        await mkdir(join(tmp, "host-stale"));
        await embed({ root, out, dist: join(tmp, "dist"), maps: true });
        await mkdir(join(out, "pages", "gone"), { recursive: true });
        await writeFile(join(out, "pages", "gone", "old.jsb"), "stale");
        const files = (await embed({ root, out, dist: join(tmp, "dist") })).files;
        assert.deepEqual(await tree(out), files);
        assert.ok(!files.some((f) => f.includes("gone") || f.endsWith(".js.map")));
    });

    it("refuses a path whose parent is not there: a mistyped --out grows no directories", async () => {
        await assert.rejects(embed({ root, out: join(tmp, "typo", "resources", "fixture"), dist: join(tmp, "dist") }), /resources does not exist/);
        assert.ok(!(await tree(tmp)).some((f) => f.startsWith("typo/")));
    });

    it("refuses a build directory that is the output or overlaps it, before building", async () => {
        const out = join(tmp, "host-overlap", "fixture");
        await mkdir(join(tmp, "host-overlap"));
        await embed({ root, out, dist: join(tmp, "dist") });
        const before = await tree(out);
        await assert.rejects(embed({ root, out, dist: out }), /overlap/);
        await assert.rejects(embed({ root, out, dist: join(out, "build") }), /overlap/);
        await assert.rejects(embed({ root, out, dist: join(tmp, "host-overlap") }), /overlap/);
        assert.deepEqual(await tree(out), before, "the package there is untouched");
    });

    it("refuses a directory that holds another package, or files that are not a package", async () => {
        const other = join(tmp, "other");
        await mkdir(other, { recursive: true });
        await writeFile(join(other, "manifest.json"), JSON.stringify({ name: "another" }));
        await assert.rejects(embed({ root, out: other, dist: join(tmp, "dist") }), /holds package another, not fixture/);
        assert.deepEqual(await tree(other), ["manifest.json"], "left as it was");

        const nameless = join(tmp, "nameless");
        await mkdir(nameless, { recursive: true });
        await writeFile(join(nameless, "manifest.json"), JSON.stringify({ pages: [] }));
        await writeFile(join(nameless, "notes.txt"), "keep me");
        await assert.rejects(embed({ root, out: nameless, dist: join(tmp, "dist") }), /names no package/);
        assert.deepEqual(await tree(nameless), ["manifest.json", "notes.txt"]);

        const notAPackage = join(tmp, "sources");
        await mkdir(notAPackage, { recursive: true });
        await writeFile(join(notAPackage, "App.kt"), "class App");
        await assert.rejects(embed({ root, out: notAPackage, dist: join(tmp, "dist") }), /is not empty and holds no manifest\.json/);
        assert.deepEqual(await tree(notAPackage), ["App.kt"]);
    });
});

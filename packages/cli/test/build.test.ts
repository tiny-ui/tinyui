import assert from "node:assert/strict";
import { copyFile, mkdir, mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { after, before, describe, it } from "node:test";
import { build, type BuildResult } from "../src/build.ts";
import { findQjsc } from "../src/qjsc.ts";

const root = join(import.meta.dirname, "fixtures", "app");

describe("tinyui build", () => {
    let out: string;
    let result: BuildResult;
    let qjsc: string | undefined;

    before(async () => {
        out = await mkdtemp(join(tmpdir(), "tinyui-build-"));
        qjsc = await findQjsc();
        result = await build({ root, out, jsOnly: !qjsc });
    });
    after(() => rm(out, { recursive: true, force: true }));

    it("names pages <pkg>/<path under src/pages>", () => {
        assert.deepEqual(result.pages.map((m) => m.name), ["fixture/home", "fixture/nested/detail"]);
        assert.deepEqual(result.runtime.map((m) => m.name), ["tinyui-core", "tinyui-native"]);
    });

    it("turns JSX into h() calls with the factory imported from tinyui-core", async () => {
        const js = await readFile(join(out, "pages", "home.js"), "utf8");
        assert.match(js, /import \{ h, Fragment, thunk \} from "tinyui-core"/);
        assert.match(js, /h\(Column, null, .*h\(Text, \{ text: label \}\)/s);
        assert.match(js, /h\(Text, \{ text: thunk\(\(\) => title\(name\)\) \}\)/, "call expressions are wrapped");
        assert.doesNotMatch(js, /interface Props|: Props/, "types are erased");
    });

    it("ignores the project's react-jsx tsconfig: output is h(), never a jsx-runtime import", async () => {
        const js = await readFile(join(out, "pages", "home.js"), "utf8");
        assert.doesNotMatch(js, /jsx-runtime/);
        assert.match(js, /h\(Column/);
    });

    it("inlines relative imports and keeps runtime modules as bare specifiers", async () => {
        const js = await readFile(join(out, "pages", "home.js"), "utf8");
        assert.match(js, /function title\(name\)/, "../lib/format.ts is bundled in");
        assert.doesNotMatch(js, /from "\.\.?\//, "no relative imports survive");
        assert.match(js, /from "tinyui-core"/);
    });

    it("imports the JSX factory only from pages that use JSX", async () => {
        const js = await readFile(join(out, "pages", "nested", "detail.js"), "utf8");
        assert.doesNotMatch(js, /tinyui-core/, "no JSX, no factory import: the runtime module does not export h yet");
    });

    it("keeps ES2025 syntax as is", async () => {
        const js = await readFile(join(out, "pages", "nested", "detail.js"), "utf8");
        assert.match(js, /await Promise\.resolve\(true\)/, "top-level await is not rewritten");
        assert.match(js, /import\.meta\.url/);
    });

    it("writes a source map next to every module", async () => {
        for (const m of [...result.runtime, ...result.pages]) {
            const map = JSON.parse(await readFile(m.map, "utf8")) as { sources: string[] };
            assert.ok(map.sources.length > 0, `${m.name} has sources`);
        }
        const home = JSON.parse(await readFile(join(out, "pages", "home.js.map"), "utf8")) as { sources: string[] };
        assert.ok(home.sources.includes("src/pages/home.tsx"), `sources are root-relative: ${home.sources}`);
        const core = JSON.parse(await readFile(join(out, "runtime", "core.js.map"), "utf8")) as { sources: string[] };
        assert.ok(core.sources.every((s) => s.includes(":") || !s.startsWith("/")), `runtime sources are relative: ${core.sources}`);
    });

    it("bundles each runtime module on its own", async () => {
        const core = await readFile(join(out, "runtime", "core.js"), "utf8");
        assert.match(core, /export \{/);
        assert.doesNotMatch(core, /from "tinyui-/);
    });

    it("rejects two sources for one page name", async () => {
        const clashRoot = await mkdtemp(join(tmpdir(), "tinyui-clash-"));
        try {
            await mkdir(join(clashRoot, "src", "pages"), { recursive: true });
            await copyFile(join(root, "tinyui.config.json"), join(clashRoot, "tinyui.config.json"));
            await writeFile(join(clashRoot, "src", "pages", "home.ts"), "export default () => 1;");
            await writeFile(join(clashRoot, "src", "pages", "home.tsx"), "export default () => 2;");
            await assert.rejects(build({ root: clashRoot, jsOnly: true }), /page fixture\/home has two sources/);
        } finally {
            await rm(clashRoot, { recursive: true, force: true });
        }
    });

    it("lists everything in manifest.json", async () => {
        const manifest = JSON.parse(await readFile(result.manifest, "utf8"));
        assert.deepEqual(manifest.runtime, ["tinyui-core", "tinyui-native"]);
        assert.deepEqual(manifest.pages, ["fixture/home", "fixture/nested/detail"]);
        assert.deepEqual(Object.keys(manifest.buildIds), [...manifest.runtime, ...manifest.pages]);
        assert.deepEqual(manifest.files, {
            "tinyui-core": "runtime/core",
            "tinyui-native": "runtime/native",
            "fixture/home": "pages/home",
            "fixture/nested/detail": "pages/nested/detail",
        });
        for (const id of Object.values(manifest.buildIds)) assert.match(id as string, /^[0-9a-f]{8}$/);
        assert.equal(manifest.buildIds["fixture/home"], result.pages[0]!.buildId);
    });

    it("records the package identity and what hot updates compare (docs/updates.md §1.1)", async () => {
        const manifest = JSON.parse(await readFile(result.manifest, "utf8"));
        const config = JSON.parse(await readFile(join(root, "tinyui.config.json"), "utf8"));
        assert.equal(manifest.name, "fixture");
        assert.equal(manifest.publicKey, config.publicKey);
        assert.match(manifest.createdAt, /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$/);
        assert.match(manifest.version, /^\d{8}T\d{6}Z-([0-9a-f]{7,}|nogit)$/);
        assert.ok(manifest.version.startsWith(manifest.createdAt.replace(/[-:]/g, "")), `${manifest.version} starts with the compact createdAt`);
        assert.equal(manifest.protocol, 1);
        if (qjsc) {
            assert.match(manifest.engine, /^[0-9a-f]{40}$/);
            assert.deepEqual(Object.keys(manifest.hashes), [...manifest.runtime, ...manifest.pages]);
            for (const hash of Object.values(manifest.hashes)) assert.match(hash as string, /^[0-9a-f]{64}$/);
        } else {
            assert.equal(manifest.engine, "");
            assert.deepEqual(manifest.hashes, {});
        }
    });

    it("takes --version as is, if it can be a directory name", async () => {
        const dir = await mkdtemp(join(tmpdir(), "tinyui-version-"));
        try {
            const r = await build({ root, out: dir, jsOnly: true, version: "1.2.3" });
            assert.equal(JSON.parse(await readFile(r.manifest, "utf8")).version, "1.2.3");
            await assert.rejects(build({ root, out: dir, jsOnly: true, version: "../x" }), /single path segment/);
        } finally {
            await rm(dir, { recursive: true, force: true });
        }
    });

    it("refuses a root without tinyui.config.json, and a pages directory outside the root", async () => {
        const bare = await mkdtemp(join(tmpdir(), "tinyui-noconfig-"));
        try {
            await assert.rejects(build({ root: bare, jsOnly: true }), /tinyui\.config\.json not found/);
            const config = JSON.parse(await readFile(join(root, "tinyui.config.json"), "utf8"));
            await writeFile(join(bare, "tinyui.config.json"), JSON.stringify({ ...config, pages: "../elsewhere" }));
            await assert.rejects(build({ root: bare, jsOnly: true }), /"pages" must be a directory inside the project root/);
            await writeFile(join(bare, "tinyui.config.json"), JSON.stringify({ ...config, pages: "..pages" }));
            await assert.rejects(build({ root: bare, jsOnly: true }), /no pages found under .*\.\.pages/, "a dot-dot prefix is still inside the root");
        } finally {
            await rm(bare, { recursive: true, force: true });
        }
    });

    it("refuses a page whose path cannot be a URL segment", async () => {
        const project = await mkdtemp(join(tmpdir(), "tinyui-nonascii-"));
        try {
            await copyFile(join(root, "tinyui.config.json"), join(project, "tinyui.config.json"));
            await mkdir(join(project, "src", "pages"), { recursive: true });
            await copyFile(join(root, "src", "pages", "home.tsx"), join(project, "src", "pages", "订单.tsx"));
            await assert.rejects(build({ root: project, jsOnly: true }), /"订单" must match \[A-Za-z0-9\._-\]\+; a page path becomes a URL segment/);
        } finally {
            await rm(project, { recursive: true, force: true });
        }
    });

    it("compiles every module to bytecode when qjsc-kmp is available", { skip: !process.env["TINYUI_QJSC"] && "TINYUI_QJSC not set" }, async () => {
        for (const m of [...result.runtime, ...result.pages]) {
            assert.ok(m.bin, `${m.name} has bytecode`);
            const bytes = await readFile(m.bin!);
            assert.equal(bytes.subarray(0, 4).toString("latin1"), "QJKB", `${m.name} bytecode header`);
        }
    });
});

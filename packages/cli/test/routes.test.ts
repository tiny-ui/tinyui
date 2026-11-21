import assert from "node:assert/strict";
import { mkdir, mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { after, before, describe, it } from "node:test";
import { execFile } from "node:child_process";
import { fileURLToPath } from "node:url";
import { promisify } from "node:util";
import { discoverPages } from "../src/build.ts";
import { generateKeyPair } from "../src/keys.ts";
import { routesTypes } from "../src/routes.ts";

const bin = fileURLToPath(new URL("../src/bin.ts", import.meta.url));
const tinyui = (...args: string[]) => promisify(execFile)(process.execPath, [bin, ...args]);

describe("tinyui routes", () => {
    let root: string;
    before(async () => {
        root = await mkdtemp(join(tmpdir(), "tinyui-routes-"));
        await writeFile(join(root, "tinyui.config.json"), JSON.stringify({ name: "shop", publicKey: generateKeyPair().publicKey }));
        await mkdir(join(root, "src/pages/checkout"), { recursive: true });
        await writeFile(join(root, "src/pages/home.tsx"), "export default function Home() { return 0; }");
        await writeFile(join(root, "src/pages/checkout/pay.tsx"), "export default function Pay(props: { id: number }) { return props.id; }");
    });
    after(() => rm(root, { recursive: true, force: true }));

    it("lists every page under its package name, importing it relative to the generated file", async () => {
        const pages = await discoverPages("shop", join(root, "src/pages"));
        const text = routesTypes("shop", pages, join(root, "src/generated/routes.ts"));
        assert.match(text, /name: "shop";/);
        assert.match(text, /"shop\/checkout\/pay": PageProps<typeof import\("\.\.\/pages\/checkout\/pay"\)>;/);
        assert.match(text, /"shop\/home": PageProps<typeof import\("\.\.\/pages\/home"\)>;/);
        assert.ok(text.indexOf('"shop/checkout/pay"') < text.indexOf('"shop/home"'), "sorted by name");
        assert.match(routesTypes("shop", pages, join(root, "routes.ts")), /import\("\.\/src\/pages\/home"\)/);
    });

    it("lists what a page pops with when it exports a Result type", async () => {
        const pages = await discoverPages("shop", join(root, "src/pages"));
        const text = routesTypes("shop", pages, join(root, "src/generated/routes.ts"), new Set(["shop/checkout/pay"]));
        assert.match(text, /interface RouteResults \{\n        "shop\/checkout\/pay": import\("\.\.\/pages\/checkout\/pay"\)\.Result;\n    \}/);
        assert.match(routesTypes("shop", pages, join(root, "src/generated/routes.ts")), /interface RouteResults \{\n    \}/, "none: the interface is still there");
    });

    it("writes the file, and --check fails once a page was added", async () => {
        const out = join(root, "src/generated/routes.ts");
        await tinyui("routes", "--root", root, "--ts", out);
        assert.match(await readFile(out, "utf8"), /"shop\/home"/);
        await tinyui("routes", "--root", root, "--ts", out, "--check");
        assert.doesNotMatch(await readFile(out, "utf8"), /\.Result;/);
        await writeFile(join(root, "src/pages/checkout/pay.tsx"), "export type Result = { paid: boolean };\nexport default function Pay(props: { id: number }) { return props.id; }");
        await assert.rejects(tinyui("routes", "--root", root, "--ts", out, "--check"), /out of date/, "a page that starts handing back a result changes the file");
        await tinyui("routes", "--root", root, "--ts", out);
        assert.match(await readFile(out, "utf8"), /"shop\/checkout\/pay": import\("\.\.\/pages\/checkout\/pay"\)\.Result;/);
        await writeFile(join(root, "src/pages/new.tsx"), "export default function New() { return 0; }");
        await assert.rejects(tinyui("routes", "--root", root, "--ts", out, "--check"), /out of date; run tinyui routes/);
    });
});

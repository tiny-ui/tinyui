import assert from "node:assert/strict";
import { cp, mkdtemp, readFile, rm, symlink, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { after, before, describe, it } from "node:test";
import { build, type Manifest } from "../src/build.ts";
import { analyzePage, RequiresError } from "../src/requires.ts";

const fixture = join(import.meta.dirname, "fixtures", "host-app");

// what esbuild leaves at the top of a bundled page: the two externals, then each merged module under its banner
const CORE = `import { h, Fragment, thunk } from "tinyui-core";\n`;
const NATIVE = `import { host } from "tinyui-native";\n`;
const TA = `var ta = { Icon: "ta.Icon", Loading: "ta.Loading" };\n`;

function rejects(code: string, message: RegExp) {
    assert.throws(() => analyzePage("shop/home", code), (e: unknown) => e instanceof RequiresError && message.test(e.message));
}

describe("host requirements of a page", () => {
    let out: string;
    before(async () => {
        out = await mkdtemp(join(tmpdir(), "tinyui-requires-"));
    });
    after(() => rm(out, { recursive: true, force: true }));

    it("finds them in a real build and writes them into the manifest per page", async () => {
        const result = await build({ root: fixture, out: join(out, "dist"), jsOnly: true });
        const manifest = JSON.parse(await readFile(result.manifest, "utf8")) as Manifest;
        assert.deepEqual(manifest.requires, {
            "shop/plain": { components: [], capabilities: [] },
            // ta.Badge through the local PlanCard, ta.Loading through a hand-written h() choosing between two;
            // ta.Unused is in the generated object but never rendered
            "shop/shop": { components: ["ta.Badge", "ta.Icon", "ta.Loading"], capabilities: ["billing.prices", "checkout.start"] },
        });
    });

    it("refuses a build with a page it cannot follow, naming the source file", async () => {
        const root = join(out, "bad-app");
        await cp(fixture, root, { recursive: true });
        // the copy sits outside the workspace, so it borrows this package's node_modules for tinyui-core / tinyui-native
        await symlink(join(import.meta.dirname, "..", "node_modules"), join(root, "node_modules"));
        await writeFile(join(root, "src", "host", "track.ts"), `import { host } from "tinyui-native";\nexport const track = (kind: string) => host.call(\`analytics.\${kind}\`);\n`);
        await writeFile(join(root, "src", "pages", "tracked.tsx"), `import { Text } from "tinyui-core";\nimport { track } from "../host/track.ts";\nexport default function Tracked() {\n    return <Text text="x" onClick={() => track("tap")} />;\n}\n`);
        await assert.rejects(build({ root, out: join(out, "bad-dist"), jsOnly: true }), (e: unknown) => {
            assert.ok(e instanceof RequiresError);
            assert.match(e.message, /shop\/tracked uses its host in a way tinyui build cannot follow/);
            assert.match(e.message, /src\/host\/track\.ts: host\.call\(`analytics\.\$\{kind\}`\)/);
            assert.match(e.message, /must be a string literal/);
            return true;
        });
    });

    it("takes literal capability names, bracketed or backquoted, and only those", () => {
        const code = `${NATIVE}host.call("a.b");\nhost["call"]("c.d", {});\nhost.call(\`e.f\`);\n`;
        assert.deepEqual(analyzePage("shop/home", code).capabilities, ["a.b", "c.d", "e.f"]);
        rejects(`${NATIVE}const kind = "x";\nhost.call(\`analytics.\${kind}\`);\n`, /must be a string literal/);
        rejects(`${NATIVE}const name = "a.b";\nhost.call(name);\n`, /must be a string literal/);
        rejects(`${NATIVE}host.call();\n`, /must be a string literal/);
    });

    it("refuses host anywhere but host.call(…), since a call through an alias would go unseen", () => {
        rejects(`${NATIVE}const call = host.call;\ncall("a.b");\n`, /host may only appear as host\.call/);
        rejects(`${NATIVE}function use(h) { return h.call("a.b"); }\nuse(host);\n`, /host may only appear as host\.call/);
        rejects(`${NATIVE}export { host };\n`, /host may only appear as host\.call/);
        // a property or a local named host is not the import
        assert.deepEqual(analyzePage("shop/home", `${NATIVE}const o = { host: 1 };\nvoid o.host;\nhost.call("a.b");\n`).capabilities, ["a.b"]);
    });

    it("resolves component types written as a tag, a literal or a choice between such", () => {
        const code = `${CORE}import { Column } from "tinyui-core";\n${TA}function Local() { return h(ta.Loading, null); }\n` +
            `function Page(p) { return h(Column, null, h(ta.Icon, null), h("ta.Badge", null), h(p.on ? ta.Icon : "ta.Spinner", null), h(Local, null), h(Fragment, null)); }\n`;
        assert.deepEqual(analyzePage("shop/home", code).components, ["ta.Badge", "ta.Icon", "ta.Loading", "ta.Spinner"]);
    });

    it("refuses a component type it would have to guess", () => {
        // an alias through a variable: kept out on purpose to keep the rule simple
        rejects(`${CORE}${TA}const C = ta.Icon;\nfunction P() { return h(C, null); }\n`, /cannot tell which component this is/);
        rejects(`${CORE}function P(props) { return h(props.type, null); }\n`, /cannot tell which component this is/);
        rejects(`${CORE}function P({ Comp }) { return h(Comp, null); }\n`, /cannot tell which component this is/);
        rejects(`${CORE}function P() { return h(Column, null); }\n`, /cannot tell which component this is/);
        rejects(`${CORE}${TA}function P() { return h(ta["Icon"], null); }\n`, /cannot tell which component this is/);
        rejects(`${CORE}${TA}function P() { return h(ta.Nope, null); }\n`, /cannot tell which component this is/);
        rejects(`${CORE}const make = h;\n`, /h may only be called/);
    });

    it("refuses a second declaration of host or h rather than resolving around it", () => {
        // name-based resolution would otherwise skip the real import silently and omit what it needs
        rejects(`${NATIVE}function f(host) { return host; }\nhost.call("a.b");\n`, /host is declared more than once/);
        rejects(`${CORE}${TA}function f() { const h = 1; return h; }\nfunction P() { return h(ta.Icon, null); }\n`, /h is declared more than once/);
    });

    it("follows namespace imports of the runtime modules through their members, and only there", () => {
        const native = `import * as native from "tinyui-native";\n`;
        assert.deepEqual(analyzePage("shop/home", `${native}native.host.call("a.b");\nnative.http.get("x");\n`).capabilities, ["a.b"]);
        rejects(`${native}const n = "x";\nnative.host.call(n);\n`, /must be a string literal/);
        rejects(`${native}const hh = native.host;\n`, /host may only appear as host\.call/);
        rejects(`${native}function use(n) {}\nuse(native);\n`, /use the tinyui-native namespace only as native\.<export>/);
        rejects(`${native}native["host"].call("a.b");\n`, /use the tinyui-native namespace only as native\.<export>/);
        const core = `import * as core from "tinyui-core";\n`;
        assert.deepEqual(analyzePage("shop/home", `${core}${TA}function P() { return core.h(core.Column, null, core.h(ta.Icon, null)); }\n`).components, ["ta.Icon"]);
        rejects(`${core}const make = core.h;\n`, /h may only be called/);
    });

    it("reads an object's members at their initial value only while nothing can change them", () => {
        const render = `function P() { return h(ta.Icon, null); }\n`;
        rejects(`${CORE}${TA}ta.Icon = "ta.New";\n${render}`, /cannot tell which component this is/);
        rejects(`${CORE}${TA}ta.Icon += "x";\n${render}`, /cannot tell which component this is/);
        rejects(`${CORE}${TA}delete ta.Icon;\n${render}`, /cannot tell which component this is/);
        rejects(`${CORE}${TA}Object.assign(ta, { Icon: "ta.New" });\n${render}`, /cannot tell which component this is/);
        rejects(`${CORE}var ta = { Icon: "ta.Icon", set() { this.Icon = "ta.New"; } };\nta.set();\n${render}`, /cannot tell which component this is/);
        rejects(`${CORE}${TA}({ Icon: ta.Icon } = { Icon: "ta.New" });\n${render}`, /cannot tell which component this is/);
        rejects(`${CORE}${TA}[ta.Icon] = ["ta.New"];\n${render}`, /cannot tell which component this is/);
        rejects(`${CORE}${TA}for (ta.Icon of ["ta.New"]) {}\n${render}`, /cannot tell which component this is/);
        rejects(`${CORE}${TA}for (ta.Icon in { x: 1 }) {}\n${render}`, /cannot tell which component this is/);
        // reads in every position a page plausibly uses keep the object trusted
        const reads = `const pick = (on) => on ? ta.Icon : ta.Loading;\nconst label = \`\${ta.Icon}\`;\nconst all = [ta.Icon, { k: ta.Loading }];\n`;
        assert.deepEqual(analyzePage("shop/home", `${CORE}${TA}${reads}${render}`).components, ["ta.Icon"]);
    });

    it("reports every problem of a page at once, each under the module it comes from", () => {
        const code = `${CORE}${NATIVE}// src/host/index.ts\nconst n = "x";\nhost.call(n);\n// src/pages/home.tsx\nfunction P(p) { return h(p.c, null); }\n`;
        assert.throws(() => analyzePage("shop/home", code), (e: unknown) => {
            const message = (e as Error).message;
            assert.match(message, /src\/host\/index\.ts: host\.call\(n\)/);
            assert.match(message, /src\/pages\/home\.tsx: p\.c/);
            return true;
        });
    });
});

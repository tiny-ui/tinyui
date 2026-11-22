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
import type { Schema } from "../src/contracts.ts";
import { routesTypes, schemaType } from "../src/routes.ts";

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

    it("types the host's native routes from its snapshot", async () => {
        const pages = await discoverPages("shop", join(root, "src/pages"));
        const routes = new Map<string, Schema>([
            ["settings", { type: "object", properties: {}, required: [] }],
            ["account/choose", {
                type: "object",
                properties: {
                    token: { type: "string" },
                    user: { type: "object", properties: { id: { type: "string" }, "main-user": { type: "boolean" } }, required: ["id"] },
                    tags: { type: "array", items: { anyOf: [{ type: "null" }, { type: "string" }] } },
                    kind: { enum: ["a", "b"] },
                    labels: { type: "object", additionalProperties: { type: "number" } },
                },
                required: ["token", "user"],
            }],
        ]);
        const text = routesTypes("shop", pages, join(root, "src/generated/routes.ts"), new Set(), routes);
        assert.match(text, /interface HostRoutes \{\n        "account\/choose": \{ token: string; user: \{ id: string; "main-user"\?: boolean \}; tags\?: \(null \| string\)\[\]; kind\?: "a" \| "b"; labels\?: Record<string, number> \};\n        "settings": \{\};\n    \}/);
        assert.equal(schemaType({ format: "date" } as unknown as Schema), "unknown", "a keyword from a newer host");
    });

    it("types native routes from the snapshot of the config's hostVersion, and --check notices a host that changed", async () => {
        const project = await mkdtemp(join(tmpdir(), "tinyui-routes-host-"));
        try {
            const key = generateKeyPair().publicKey;
            await mkdir(join(project, "src/pages"), { recursive: true });
            await writeFile(join(project, "src/pages/home.ts"), `export default function Home() { return 0; }`);
            const out = join(project, "src/generated/routes.ts");
            const body = (hostVersion: string, routes: string) => `hostVersion ${hostVersion}\ntinyui 0.14.0-alpha04\n\ncomponents\n\ncapabilities\n\nchannels\n\nroutes\n${routes}`;

            await writeFile(join(project, "tinyui.config.json"), JSON.stringify({ name: "shop", publicKey: key, hostVersion: "3" }));
            await assert.rejects(tinyui("routes", "--root", project, "--ts", out), /hostVersion 3, but .*tinyui-host\/3\.txt does not exist; run tinyui hosts download/);

            await mkdir(join(project, "tinyui-host"), { recursive: true });
            await writeFile(join(project, "tinyui-host/3.txt"), body("3", `  settings  {"type":"object","properties":{},"required":[]}\n`));
            await tinyui("routes", "--root", project, "--ts", out);
            assert.match(await readFile(out, "utf8"), /"settings": \{\};/);
            await tinyui("routes", "--root", project, "--ts", out, "--check");
            await writeFile(join(project, "tinyui-host/3.txt"), body("3", `  settings  {"type":"object","properties":{"tab":{"type":"string"}},"required":[]}\n`));
            await assert.rejects(tinyui("routes", "--root", project, "--ts", out, "--check"), /out of date; run tinyui routes /);

            // following the host is the one number: the next snapshot is read, and one filed under the wrong number is refused
            await writeFile(join(project, "tinyui.config.json"), JSON.stringify({ name: "shop", publicKey: key, hostVersion: "4" }));
            await writeFile(join(project, "tinyui-host/4.txt"), body("3", ""));
            await assert.rejects(tinyui("routes", "--root", project, "--ts", out), /4\.txt is the snapshot of host version 3/);
            await writeFile(join(project, "tinyui-host/4.txt"), body("4", `  wallet  {"type":"object","properties":{},"required":[]}\n`));
            await tinyui("routes", "--root", project, "--ts", out);
            assert.match(await readFile(out, "utf8"), /"wallet": \{\};/);
        } finally {
            await rm(project, { recursive: true, force: true });
        }
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

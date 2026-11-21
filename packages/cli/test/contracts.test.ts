import assert from "node:assert/strict";
import { access, mkdir, mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { after, before, describe, it } from "node:test";
import { execFile } from "node:child_process";
import { fileURLToPath } from "node:url";
import { promisify } from "node:util";
import { build, discoverPages } from "../src/build.ts";
import { ContractError, pageContracts, type PageContract } from "../src/contracts.ts";
import { generateKeyPair } from "../src/keys.ts";

const bin = fileURLToPath(new URL("../src/bin.ts", import.meta.url));
const tinyui = (...args: string[]) => promisify(execFile)(process.execPath, [bin, ...args]);

// inside the repo, not the system temp directory: the project's own `typescript` is found from its root
describe("tinyui routes --schema", () => {
    let root: string;
    const page = (name: string, source: string) => writeFile(join(root, "src/pages", `${name}.ts`), source);
    const contracts = async (results: string[] = []) => pageContracts(root, await discoverPages("shop", join(root, "src/pages")), new Set(results));

    before(async () => {
        root = await mkdtemp(join(import.meta.dirname, ".contracts-"));
        await writeFile(join(root, "tinyui.config.json"), JSON.stringify({ name: "shop", publicKey: generateKeyPair().publicKey, contracts: "src/generated/routes.json" }));
        await writeFile(join(root, "tsconfig.json"), JSON.stringify({ compilerOptions: { strict: true, noEmit: true, module: "esnext", moduleResolution: "bundler", exactOptionalPropertyTypes: true }, include: ["src"] }));
        await mkdir(join(root, "src/pages"), { recursive: true });
        await writeFile(join(root, "src/types.ts"), `
            export type Method = "sms" | "email";
            export interface Option { method: Method; target: string }
            export type Result = { status: "passed"; token: string } | { status: "expired" };
        `);
    });
    after(() => rm(root, { recursive: true, force: true }));

    it("writes each page's props as a shape, following types across files", async () => {
        await page("home", `export default function Home() { return 0; }`);
        await page("verify", `
            import type { Option, Result } from "../types";
            export type { Result };
            interface Props { token: string; options: Option[]; retries?: number; strict: boolean; note: string | null; extra?: Record<string, string>; kind: "a" | 1 | boolean; tagged: { id: string; ["__proto__"]: number; [other: string]: string | number } }
            export default function Verify(props: Props) { return props.options.length; }
        `);
        const all = await contracts(["shop/verify"]);
        assert.deepEqual(all["shop/home"], { params: { type: "object", properties: {}, required: [] } }, "a page that takes nothing");
        assert.deepEqual(all["shop/verify"]!.params, {
            type: "object",
            properties: {
                token: { type: "string" },
                options: { type: "array", items: { type: "object", properties: { method: { enum: ["email", "sms"] }, target: { type: "string" } }, required: ["method", "target"] } },
                retries: { type: "number" },
                strict: { type: "boolean" },
                note: { anyOf: [{ type: "null" }, { type: "string" }] },
                extra: { type: "object", additionalProperties: { type: "string" } },
                kind: { anyOf: [{ const: "a" }, { const: 1 }, { type: "boolean" }] },
                tagged: {
                    type: "object",
                    properties: Object.defineProperty({ id: { type: "string" } }, "__proto__", { value: { type: "number" }, enumerable: true, writable: true, configurable: true }),
                    required: ["id", "__proto__"],
                    additionalProperties: { anyOf: [{ type: "number" }, { type: "string" }] },
                },
            },
            required: ["token", "options", "strict", "note", "kind", "tagged"],
        } satisfies PageContract["params"]);
        assert.deepEqual(all["shop/verify"]!.result, {
            anyOf: [
                { type: "object", properties: { status: { const: "expired" } }, required: ["status"] },
                { type: "object", properties: { status: { const: "passed" }, token: { type: "string" } }, required: ["status", "token"] },
            ],
        });
    });

    it("refuses what JSON cannot carry or a host could not check, naming the page and the field", async () => {
        const rejects = async (source: string, re: RegExp) => {
            await page("bad", source);
            await assert.rejects(contracts(), (e: unknown) => e instanceof ContractError && re.test(e.message), source);
        };
        await rejects(`export default function Bad(props: { done: () => void }) { return 0; }`, /^shop\/bad: props\.done is a function/m);
        await rejects(`export default function Bad(props: { extra: Record<string, unknown> }) { return 0; }`, /shop\/bad: props\.extra\[key\] is unknown/);
        await rejects(`export default function Bad(props: { at: [number, number] }) { return 0; }`, /props\.at is a tuple/);
        await rejects(`interface Tree { children: Tree[] } export default function Bad(props: { tree: Tree }) { return 0; }`, /props\.tree\.children\[\] refers back to itself/);
        await rejects(`export default function Bad(props: { id: bigint }) { return 0; }`, /props\.id is bigint, which does not cross/);
        await rejects(`export const x = 1;`, /shop\/bad: has no default export/);
        await rm(join(root, "src/pages/bad.ts"));
    });

    it("the command writes the file, --check notices a changed prop, and the build carries it in the manifest", async () => {
        const ts = join(root, "src/generated/routes.ts");
        const schema = join(root, "src/generated/routes.json");
        await tinyui("routes", "--root", root, "--ts", ts, "--schema", schema);
        const written = JSON.parse(await readFile(schema, "utf8"));
        assert.deepEqual(Object.keys(written), ["shop/home", "shop/verify"]);
        assert.deepEqual(written["shop/verify"].result.anyOf.map((s: { properties: { status: unknown } }) => s.properties.status), [{ const: "expired" }, { const: "passed" }], "the Result export was found without being told");
        await tinyui("routes", "--root", root, "--ts", ts, "--schema", schema, "--check");

        const out = join(root, "dist");
        const manifest = JSON.parse(await readFile((await build({ root, out, jsOnly: true })).manifest, "utf8"));
        assert.deepEqual(manifest.contracts, written);

        await page("home", `export default function Home(props: { tab?: string }) { return 0; }`);
        await assert.rejects(tinyui("routes", "--root", root, "--ts", ts, "--schema", schema, "--check"), /routes\.json is out of date/);
        assert.deepEqual(JSON.parse(await readFile(schema, "utf8")), written, "--check writes nothing");
        await page("extra", `export default function Extra() { return 0; }`);
        await assert.rejects(build({ root, out, jsOnly: true }), /contracts: .*out of date \(no shop\/extra\)/);
        await access(join(out, "pages", "home.js")); // refused before the previous output was touched
        await rm(join(root, "src/pages/extra.ts"));
        await writeFile(schema, JSON.stringify({ ...written, "shop/home": { params: { type: "object", properties: { tab: { anyOf: {} } }, required: [] } } }));
        await assert.rejects(build({ root, out, jsOnly: true }), /not what tinyui routes --schema writes \(shop\/home params\.properties\.tab\.anyOf: not a list of shapes\)/);
    });
});

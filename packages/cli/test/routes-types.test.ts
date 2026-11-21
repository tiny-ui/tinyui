import assert from "node:assert/strict";
import { mkdir, mkdtemp, rm, writeFile } from "node:fs/promises";
import { createRequire } from "node:module";
import { dirname, join } from "node:path";
import { after, before, describe, it } from "node:test";
import { execFile } from "node:child_process";
import { fileURLToPath } from "node:url";
import { promisify } from "node:util";
import { generateKeyPair } from "../src/keys.ts";

const run = promisify(execFile);
const bin = fileURLToPath(new URL("../src/bin.ts", import.meta.url));
const tsc = join(dirname(createRequire(import.meta.url).resolve("typescript/package.json")), "bin", "tsc");

/**
 * What `tinyui routes` generates is a module augmentation: it only does anything if `tinyui-native` exports the
 * interfaces it augments. So the generated file is type-checked here together with calls that must and must not
 * compile, against the package's sources.
 */
describe("the generated routes type-check navigation", () => {
    let root: string;
    before(async () => {
        root = await mkdtemp(join(import.meta.dirname, ".contracts-"));
        const packages = join(import.meta.dirname, "..", "..");
        await writeFile(join(root, "tinyui.config.json"), JSON.stringify({ name: "shop", publicKey: generateKeyPair().publicKey }));
        await writeFile(join(root, "tsconfig.json"), JSON.stringify({
            compilerOptions: {
                strict: true, noEmit: true, module: "esnext", moduleResolution: "bundler", allowImportingTsExtensions: true, skipLibCheck: true, types: [], lib: ["ES2025"], target: "ES2025",
                paths: { "tinyui-native": [join(packages, "native/src/index.ts")], "tinyui-core": [join(packages, "core/src/index.ts")] },
            },
            include: ["src"],
        }));
        await mkdir(join(root, "src/pages"), { recursive: true });
        await writeFile(join(root, "src/pages/home.ts"), `export default function Home() { return 0; }`);
        await writeFile(join(root, "src/pages/pay.ts"), `export type Result = { paid: boolean };\nexport default function Pay(props: { id: number }) { return props.id; }`);
        await writeFile(join(root, "host.txt"), `hostVersion 3\ntinyui 0.14.0-alpha04\n\ncomponents\n\ncapabilities\n\nchannels\n\nroutes\n  account/choose  {"type":"object","properties":{"token":{"type":"string"},"user":{"type":"object","properties":{"id":{"type":"string"}},"required":["id"]}},"required":["token","user"]}\n`);
        await run(process.execPath, [bin, "routes", "--root", root, "--ts", join(root, "src/generated/routes.ts"), "--host", join(root, "host.txt")]);
    });
    after(() => rm(root, { recursive: true, force: true }));

    const check = async (usage: string) => {
        await writeFile(join(root, "src/usage.ts"), `import { navigation } from "tinyui-native";\nimport type { Presentation } from "tinyui-native";\n${usage}`);
        return run(process.execPath, [tsc, "-p", join(root, "tsconfig.json")]).then(() => "", (e: { stdout: string }) => e.stdout);
    };

    it("accepts what fits and gives results their type", async () => {
        assert.equal(await check(`
            export const presentation: Presentation = "sheet";
            navigation.push("shop/pay", { id: 1 });
            navigation.replace("account/choose", { token: "t", user: { id: "u" } }, { since: "shop/home" });
            navigation.push("wallet/home", { nested: { ok: [1, "a", null] } });
            navigation.onResult("shop/pay", (result) => { const paid: boolean | undefined = result?.paid; void paid; });
            navigation.onResult("shop/home", (result) => { const none: null = result; void none; });
            navigation.onResult("wallet/home", (result) => { const any: unknown = result; void any; });
        `), "");
    });

    it("refuses a wrong name, wrong params and a result read as something it is not", async () => {
        const errors = await check(`
            navigation.push("shop/pya", { id: 1 });
            navigation.push("shop/pay", { id: "1" });
            navigation.replace("account/choose", { token: "t" });
            navigation.replace("account/choose", { token: "t", user: { id: 1 } });
            navigation.onResult("shop/pay", (result) => { const paid: string = result!.paid; void paid; });
        `);
        // two import lines and the template's leading newline come first
        for (const line of [4, 5, 6, 7, 8]) assert.match(errors, new RegExp(`usage\\.ts\\(${line},`), `line ${line} should not compile:\n${errors}`);
    });
});

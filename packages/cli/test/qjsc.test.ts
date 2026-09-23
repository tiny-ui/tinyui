import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { join } from "node:path";
import { describe, it } from "node:test";

const root = join(import.meta.dirname, "..", "..", "..");

describe("qjsc-kmp", () => {
    // bytecode names the engine it was compiled for; a prebuilt compiler from another quickjs-kmp would build
    // packages every App rejects as an engine mismatch
    it("depends on the qjsc-kmp release of the quickjs-kmp version the Kotlin side uses", async () => {
        const catalog = await readFile(join(root, "gradle", "libs.versions.toml"), "utf8");
        const kotlin = catalog.match(/^quickjsKmp\s*=\s*"([^"]+)"/m)?.[1];
        const cli = JSON.parse(await readFile(join(root, "packages", "cli", "package.json"), "utf8")) as { dependencies: Record<string, string> };
        assert.ok(kotlin, "gradle/libs.versions.toml has no quickjsKmp version");
        assert.equal(cli.dependencies["qjsc-kmp"], kotlin);
    });
});

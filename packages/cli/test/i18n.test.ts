import assert from "node:assert/strict";
import { mkdir, mkdtemp, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { after, before, describe, it } from "node:test";
import { i18nTypes, I18nError, loadI18n } from "../src/i18n.ts";
import type { TinyUIConfig } from "../src/config.ts";

const config = (defaultLocale?: string): TinyUIConfig => ({ name: "shop", publicKey: "", pages: "src/pages", i18n: "i18n", ...(defaultLocale && { defaultLocale }) });

describe("package i18n", () => {
    let root: string;
    before(async () => { root = await mkdtemp(join(tmpdir(), "tinyui-i18n-")); });
    after(() => rm(root, { recursive: true, force: true }));

    async function strings(files: Record<string, unknown>) {
        await rm(join(root, "i18n"), { recursive: true, force: true });
        await mkdir(join(root, "i18n"));
        for (const [name, value] of Object.entries(files)) await writeFile(join(root, "i18n", name), JSON.stringify(value));
    }

    it("loads every language and types the default one's keys and placeholders", async () => {
        await strings({ "en.json": { "sub.title": "Pro", "sub.save": "Save {percent}" }, "zh.json": { "sub.title": "专业版", "sub.save": "省 {percent}" } });
        const i18n = (await loadI18n(root, config("en")))!;
        assert.deepEqual([...i18n.paths], [["en", "i18n/en.json"], ["zh", "i18n/zh.json"]]);
        const types = i18nTypes(i18n);
        assert.match(types, /"sub\.save": "percent";/);
        assert.match(types, /"sub\.title": never;/);
    });

    it("refuses missing and extra keys, mismatched placeholders and a missing default", async () => {
        await strings({ "en.json": { a: "A {x}", b: "B" }, "zh.json": { a: "甲", c: "丙" } });
        await assert.rejects(loadI18n(root, config("en")), (e: unknown) => {
            assert.ok(e instanceof I18nError);
            assert.match(e.message, /zh\.json lacks "b"/);
            assert.match(e.message, /zh\.json has "c"/);
            assert.match(e.message, /"a" has placeholders \{\}, en\.json has \{x\}/);
            return true;
        });
        await assert.rejects(loadI18n(root, config("fr")), /no fr\.json/);
        await assert.rejects(loadI18n(root, config()), /no "defaultLocale"/);
    });

    it("is nothing for a package without strings", async () => {
        await rm(join(root, "i18n"), { recursive: true, force: true });
        assert.equal(await loadI18n(root, config()), null);
    });
});

import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { DeclarationError, pageDeclarations } from "../src/declarations.ts";

const read = (source: string) => pageDeclarations("pages/verify.tsx", source);
const rejects = (source: string, re: RegExp) => assert.throws(() => read(source), (e: unknown) => e instanceof DeclarationError && re.test(e.message));

describe("page declarations", () => {
    it("a page that says nothing is a screen with no result", () => {
        assert.deepEqual(read(`export default function Home() { return <Column /> }`), { presentation: "screen", result: false });
    });

    it("reads the presentation from a string literal, typed or not", () => {
        assert.equal(read(`export const presentation = "sheet";`).presentation, "sheet");
        assert.equal(read(`export const presentation: Presentation = "sheet";`).presentation, "sheet");
        assert.equal(read(`export const presentation = "sheet" as const;`).presentation, "sheet");
        assert.equal(read(`export const presentation = "sheet" satisfies Presentation;`).presentation, "sheet");
        assert.equal(read(`export const presentation = "screen";`).presentation, "screen");
    });

    it("refuses a presentation it would have to run the page to know", () => {
        rejects(`const kind = "sheet"; export const presentation = kind;`, /string literal/);
        rejects("export const presentation = `sheet`;", /string literal/);
        rejects(`export let presentation = "sheet";`, /must be a const/);
        rejects(`const presentation = "sheet"; export { presentation };`, /declared in the page file/);
        rejects(`export function presentation() { return "sheet"; }`, /declared in the page file/);
        rejects(`export class presentation {}`, /declared in the page file/);
        rejects(`export * as presentation from "./kind";`, /declared in the page file/);
        rejects(`export const { presentation } = options;`, /declared in the page file/);
        rejects(`export const [first, { kind: presentation = "sheet" }] = options;`, /declared in the page file/);
        assert.equal(read(`export const { other: renamed } = options; export * from "./more";`).presentation, "screen", "nothing here is named presentation");
        rejects(`export const presentation = "dialog";`, /not one of screen, sheet/);
    });

    it("ignores a presentation that is not exported", () => {
        assert.equal(read(`const presentation = "sheet";`).presentation, "screen");
    });

    it("sees an exported Result type, however it is written", () => {
        assert.equal(read(`export type Result = { ok: true };`).result, true);
        assert.equal(read(`export interface Result { ok: true }`).result, true);
        assert.equal(read(`import type { Result } from "./types"; export type { Result };`).result, true);
        assert.equal(read(`type Result = 1; export { type Result };`).result, true);
        assert.equal(read(`type Result = { ok: true };`).result, false);
        assert.equal(read(`export const Result = 1;`).result, false);
    });

    it("reports the file", () => rejects(`export const presentation = 1;`, /^pages\/verify\.tsx: /));
});

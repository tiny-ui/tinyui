import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { isCompatible } from "../src/version.ts";

describe("isCompatible", () => {
    it("takes a package built against the same or an older tinyui of the same major", () => {
        assert.ok(isCompatible("0.7.0", "0.7.0"));
        assert.ok(isCompatible("0.9.2", "0.7.0"));
        assert.ok(isCompatible("2.3.0", "2.0.1"));
    });

    it("refuses a newer package or another major", () => {
        assert.ok(!isCompatible("0.7.0", "0.7.1"));
        assert.ok(!isCompatible("2.0.0", "1.9.0"), "a new major may have removed what the page uses");
        assert.ok(!isCompatible("1.0.0", "0.9.0"));
    });

    it("orders pre-releases below their release", () => {
        assert.ok(isCompatible("0.7.0", "0.7.0-rc.1"));
        assert.ok(!isCompatible("0.7.0-rc.1", "0.7.0"));
        assert.ok(isCompatible("0.7.0-rc.10", "0.7.0-rc.2"));
    });

    it("rejects what is not a version", () => {
        assert.throws(() => isCompatible("0.7", "0.7.0"), /not a tinyui version/);
    });
});

import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { parseHostSnapshot } from "../src/snapshot.ts";

describe("host snapshot", () => {
    it("reads the header and the names, leaving the rest of each line to people", () => {
        const snapshot = parseHostSnapshot("hostVersion 2\r\ntinyui 0.3.0\r\n\r\ncomponents\r\n  ta.Icon     name: string, size?: dp\r\n  ta.Loading\r\n\r\ncapabilities\r\n  checkout.start\r\n  plain\r\n");
        assert.equal(snapshot.hostVersion, "2");
        assert.equal(snapshot.tinyui, "0.3.0");
        assert.deepEqual([...snapshot.components], ["ta.Icon", "ta.Loading"]);
        assert.deepEqual([...snapshot.capabilities], ["checkout.start", "plain"]);
        assert.equal(parseHostSnapshot("hostVersion 1\ntinyui 0.3.0\n").capabilities.size, 0);
    });

    it("refuses anything it would have to guess at", () => {
        assert.throws(() => parseHostSnapshot("tinyui 0.3.0\nhostVersion 1\n"), /line 1 must be "hostVersion <value>"/);
        assert.throws(() => parseHostSnapshot("hostVersion 1\n"), /line 2 must be "tinyui <value>"/);
        assert.throws(() => parseHostSnapshot("hostVersion 1\ntinyui 0.3.0\n  ta.Icon\n"), /line 3 is neither a section nor an item/);
        assert.throws(() => parseHostSnapshot("hostVersion 1\ntinyui 0.3.0\nwidgets\n  ta.Icon\n"), /line 3 is neither a section nor an item/);
        assert.throws(() => parseHostSnapshot("hostVersion 1\ntinyui 0.3.0\ncapabilities\nplain\n"), /line 4 is neither a section nor an item/);
    });
});

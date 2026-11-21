import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { parseHostSnapshot } from "../src/snapshot.ts";

describe("host snapshot", () => {
    it("reads the header and the names, leaving the rest of each line to people", () => {
        const snapshot = parseHostSnapshot("hostVersion 2\r\ntinyui 0.8.0\r\n\r\ncomponents\r\n  ta.Icon     name: string, size?: dp\r\n  ta.Loading\r\n\r\ncapabilities\r\n  checkout.start\r\n  plain\r\n\r\nchannels\r\n  app\r\n");
        assert.equal(snapshot.hostVersion, "2");
        assert.equal(snapshot.tinyui, "0.8.0");
        assert.deepEqual([...snapshot.channels], ["app"]);
        assert.deepEqual([...snapshot.components], ["ta.Icon", "ta.Loading"]);
        assert.deepEqual([...snapshot.capabilities], ["checkout.start", "plain"]);
        const empty = parseHostSnapshot("hostVersion 1\ntinyui 0.3.0\n\ncomponents\n\ncapabilities\n");
        assert.equal(empty.components.size + empty.capabilities.size + empty.channels.size, 0, "frozen before channels existed: provides none");
        assert.throws(() => parseHostSnapshot("hostVersion 1\ntinyui 0.8.0\n\ncomponents\n\ncapabilities\n"), /no "channels" section/);
    });

    it("reads native routes with the shape of their params; a snapshot frozen before routes existed has none", () => {
        const body = "\n\ncomponents\n\ncapabilities\n\nchannels\n";
        const shape = { type: "object", properties: { token: { type: "string" } }, required: ["token"] };
        const snapshot = parseHostSnapshot(`hostVersion 3\ntinyui 0.14.0-alpha04${body}\nroutes\n  account/choose  ${JSON.stringify(shape)}\n  settings        {"type":"object","properties":{},"required":[]}\n`);
        assert.deepEqual([...snapshot.routes.keys()], ["account/choose", "settings"]);
        assert.deepEqual(snapshot.routes.get("account/choose"), shape);
        assert.equal(parseHostSnapshot(`hostVersion 2\ntinyui 0.14.0-alpha03${body}`).routes.size, 0);
        assert.throws(() => parseHostSnapshot(`hostVersion 3\ntinyui 0.14.0-alpha04${body}`), /no "routes" section/);
        assert.throws(() => parseHostSnapshot(`hostVersion 3\ntinyui 0.14.0-alpha04${body}\nroutes\n  settings\n`), /a route is "<name>  <params shape as JSON>"/);
    });

    it("refuses anything it would have to guess at", () => {
        assert.throws(() => parseHostSnapshot("tinyui 0.3.0\nhostVersion 1\n"), /line 1 must be "hostVersion <value>"/);
        assert.throws(() => parseHostSnapshot("hostVersion 1\n"), /line 2 must be "tinyui <value>"/);
        assert.throws(() => parseHostSnapshot("hostVersion 1\ntinyui 0.3.0\n  ta.Icon\n"), /line 3: expected the "components" section/);
        assert.throws(() => parseHostSnapshot("hostVersion 1\ntinyui 0.3.0\nwidgets\n  ta.Icon\n"), /line 3: expected the "components" section/);
        assert.throws(() => parseHostSnapshot("hostVersion 1\ntinyui 0.3.0\ncomponents\ncapabilities\nplain\n"), /line 5: expected the "channels" section/);
        assert.throws(() => parseHostSnapshot("hostVersion 1\ntinyui 0.8.0\ncomponents\ncapabilities\nchannels\nplain\n"), /line 6: expected the "routes" section or an indented item/);
        // both sections, once each, in order: a missing one would read as "provides nothing"
        assert.throws(() => parseHostSnapshot("hostVersion 1\ntinyui 0.3.0\n"), /no "components" section/);
        assert.throws(() => parseHostSnapshot("hostVersion 1\ntinyui 0.3.0\ncomponents\n  ta.Icon\n"), /no "capabilities" section/);
        assert.throws(() => parseHostSnapshot("hostVersion 1\ntinyui 0.3.0\ncapabilities\ncomponents\n"), /line 3: expected the "components" section/);
        assert.throws(() => parseHostSnapshot("hostVersion 1\ntinyui 0.3.0\ncomponents\ncapabilities\ncomponents\n"), /line 5: expected the "channels" section/);
    });
});

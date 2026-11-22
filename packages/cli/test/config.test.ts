import assert from "node:assert/strict";
import { execFile } from "node:child_process";
import { mkdtemp, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { after, before, describe, it } from "node:test";
import { fileURLToPath } from "node:url";
import { promisify } from "node:util";
import { loadConfig } from "../src/config.ts";
import { generateKeyPair } from "../src/keys.ts";

const bin = fileURLToPath(new URL("../src/bin.ts", import.meta.url));

describe("tinyui.config.json: where a package is published", () => {
    let root: string;
    const publicKey = generateKeyPair().publicKey;
    const write = (extra: Record<string, unknown>) => writeFile(join(root, "tinyui.config.json"), JSON.stringify({ name: "shop", publicKey, ...extra }));
    // the commands take these defaults from the project they run in
    const tinyui = (...args: string[]) =>
        promisify(execFile)(process.execPath, [bin, ...args], { cwd: root, env: { ...process.env, TINYUI_APP: "", TINYUI_UPDATES_URL: "", TINYUI_TOKEN: "t" } });

    before(async () => {
        root = await mkdtemp(join(tmpdir(), "tinyui-config-"));
    });
    after(() => rm(root, { recursive: true, force: true }));

    it("reads app, hostVersion and updatesUrl, all optional", async () => {
        await write({});
        const bare = await loadConfig(root);
        assert.equal(bare.app, undefined);
        assert.equal(bare.hostVersion, undefined);
        await write({ app: "store", hostVersion: "3", updatesUrl: "https://updates.example" });
        const config = await loadConfig(root);
        assert.deepEqual([config.app, config.hostVersion, config.updatesUrl], ["store", "3", "https://updates.example"]);
    });

    it("refuses a host version that is not a positive integer in a string, and an app that is not a name", async () => {
        await write({ hostVersion: "1.2.0" });
        await assert.rejects(loadConfig(root), /"hostVersion" must be a positive integer/);
        await write({ hostVersion: 3 });
        await assert.rejects(loadConfig(root), /"hostVersion" must be a positive integer/);
        await write({ app: "My App" });
        await assert.rejects(loadConfig(root), /"app" must match/);
    });

    it("bundle takes the host version from the config, and says where to put it when it has none", async () => {
        await write({});
        await assert.rejects(tinyui("bundle", "--signing-key", "key.pem"), /--host-version is required, or set "hostVersion" in tinyui\.config\.json/);
        // past the host version: the next thing it misses is the build
        await write({ hostVersion: "3" });
        await assert.rejects(tinyui("bundle", "--signing-key", "key.pem"), (e: { stderr: string }) => !/host-version/.test(e.stderr));
    });

    it("a broken config fails the command that turns to it, with what is wrong", async () => {
        await write({ hostVersion: "v3" });
        await assert.rejects(tinyui("bundle", "--signing-key", "key.pem"), /"hostVersion" must be a positive integer/);
    });

    it("releases take the app, the host version and the instance from the config", async () => {
        // nothing listens there: getting as far as the request shows none of the three was asked for
        await write({ app: "store", hostVersion: "3", updatesUrl: "http://localhost:9" });
        await assert.rejects(tinyui("releases", "list"), (e: { stderr: string }) => !/is required/.test(e.stderr) && /localhost:9|fetch failed|ECONNREFUSED/i.test(e.stderr));
    });
});

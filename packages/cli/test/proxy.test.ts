import assert from "node:assert/strict";
import { createServer, type Server } from "node:http";
import type { AddressInfo } from "node:net";
import { after, before, beforeEach, describe, it } from "node:test";
import { Agent, setGlobalDispatcher } from "undici";
import { useEnvProxy } from "../src/proxy.ts";

describe("useEnvProxy", () => {
    let proxy: Server;
    let origin: Server;
    let proxyUrl: string;
    let originPort: number;
    const seen: string[] = [];

    before(async () => {
        // "origin" answers both requests tunnelled through the proxy and direct loopback requests; `seen` tells them apart
        origin = createServer((req, res) => {
            seen.push(`GET ${req.headers.host}${req.url}`);
            res.end("ok");
        });
        await new Promise<void>((resolve) => origin.listen(0, "::", resolve));
        originPort = (origin.address() as AddressInfo).port;
        // undici tunnels through the proxy with CONNECT even for http targets; answer it, then hand the socket to
        // origin so no real network is touched
        proxy = createServer();
        proxy.on("connect", (req, socket) => {
            seen.push(`CONNECT ${req.url}`);
            socket.write("HTTP/1.1 200 Connection Established\r\n\r\n");
            origin.emit("connection", socket);
        });
        await new Promise<void>((resolve) => proxy.listen(0, "127.0.0.1", resolve));
        proxyUrl = `http://127.0.0.1:${(proxy.address() as AddressInfo).port}`;
    });

    beforeEach(() => {
        seen.length = 0;
        setGlobalDispatcher(new Agent());
    });

    after(async () => {
        setGlobalDispatcher(new Agent());
        // the agents keep their connections alive; close() alone would wait for them forever
        proxy.closeAllConnections();
        origin.closeAllConnections();
        await new Promise((resolve) => proxy.close(resolve));
        await new Promise((resolve) => origin.close(resolve));
    });

    it("leaves fetch alone without proxy variables", async () => {
        assert.equal(await useEnvProxy({}), false);
        assert.equal(await useEnvProxy({ HTTP_PROXY: "", https_proxy: "" }), false);
    });

    it("routes the global fetch through HTTP_PROXY", async () => {
        assert.equal(await useEnvProxy({ HTTP_PROXY: proxyUrl }), true);
        await (await fetch("http://updates.example.invalid/demo/ping")).text();
        assert.deepEqual(seen, ["CONNECT updates.example.invalid:80", "GET updates.example.invalid/demo/ping"]);
    });

    it("falls back to the lowercase variable when the uppercase one is empty", async () => {
        assert.equal(await useEnvProxy({ HTTP_PROXY: "", http_proxy: proxyUrl }), true);
        await (await fetch("http://updates.example.invalid/demo/ping")).text();
        assert.equal(seen[0], "CONNECT updates.example.invalid:80");
    });

    it("keeps loopback direct even when NO_PROXY does not list it", async () => {
        assert.equal(await useEnvProxy({ HTTP_PROXY: proxyUrl, NO_PROXY: "corp.example" }), true);
        for (const host of ["localhost", "127.0.0.1", "[::1]"]) {
            await (await fetch(`http://${host}:${originPort}/local`)).text();
        }
        assert.deepEqual(seen, [
            `GET localhost:${originPort}/local`,
            `GET 127.0.0.1:${originPort}/local`,
            `GET [::1]:${originPort}/local`,
        ]);
    });

    it("skips a proxy setting it cannot use instead of failing the command", async () => {
        assert.equal(await useEnvProxy({ HTTPS_PROXY: "not a url" }), false);
    });
});

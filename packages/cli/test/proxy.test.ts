import assert from "node:assert/strict";
import { createServer, type Server } from "node:http";
import type { AddressInfo } from "node:net";
import { after, before, describe, it } from "node:test";
import { Agent, setGlobalDispatcher } from "undici";
import { useEnvProxy } from "../src/proxy.ts";

describe("useEnvProxy", () => {
    let proxy: Server;
    let origin: Server;
    let proxyUrl: string;
    const seen: string[] = [];

    before(async () => {
        // undici tunnels through the proxy with CONNECT even for http targets; answer it, then serve the tunnelled
        // request from an in-process "origin" so no real network is touched
        origin = createServer((req, res) => {
            seen.push(`GET ${req.headers.host}${req.url}`);
            res.end("via proxy");
        });
        proxy = createServer();
        proxy.on("connect", (req, socket) => {
            seen.push(`CONNECT ${req.url}`);
            socket.write("HTTP/1.1 200 Connection Established\r\n\r\n");
            origin.emit("connection", socket);
        });
        await new Promise<void>((resolve) => proxy.listen(0, "127.0.0.1", resolve));
        proxyUrl = `http://127.0.0.1:${(proxy.address() as AddressInfo).port}`;
    });

    after(async () => {
        setGlobalDispatcher(new Agent());
        // the proxy agent keeps its connection alive; close() alone would wait for it forever
        proxy.closeAllConnections();
        origin.closeAllConnections();
        await new Promise((resolve) => proxy.close(resolve));
    });

    it("leaves fetch alone without proxy variables", async () => {
        assert.equal(await useEnvProxy({}), false);
    });

    it("routes the global fetch through HTTP_PROXY", async () => {
        assert.equal(await useEnvProxy({ HTTP_PROXY: proxyUrl }), true);
        const response = await fetch("http://updates.example.invalid/demo/ping");
        assert.equal(await response.text(), "via proxy");
        assert.deepEqual(seen, ["CONNECT updates.example.invalid:80", "GET updates.example.invalid/demo/ping"]);
    });
});

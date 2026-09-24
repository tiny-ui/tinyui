import { createHash } from "node:crypto";
import { mkdir, writeFile } from "node:fs/promises";
import { createServer, type IncomingMessage, type Server, type ServerResponse } from "node:http";
import { join } from "node:path";
import type { Manifest } from "../src/build.ts";

/** A `tinyui build` output written by hand: bundle only needs the manifest and the `.bin` files it lists. */
export async function fakeDist(dir: string, publicKey: string, edit: (m: Manifest) => void = () => {}): Promise<string> {
    const files = { "fixture/home": "pages/home", "fixture/orders": "pages/orders" };
    const hashes: Record<string, string> = {};
    for (const [module, path] of Object.entries(files)) {
        const bytes = Buffer.concat([Buffer.from("QJKB"), Buffer.alloc(8), Buffer.from("a".repeat(40)), Buffer.from(module)]);
        await mkdir(join(dir, path, ".."), { recursive: true });
        await writeFile(join(dir, path + ".bin"), bytes);
        hashes[module] = createHash("sha256").update(bytes).digest("hex");
    }
    const manifest: Manifest = {
        pages: ["fixture/home", "fixture/orders"],
        files,
        buildIds: Object.fromEntries(Object.keys(files).map((m) => [m, "00000000"])),
        name: "fixture",
        publicKey,
        version: "20260922T090000Z-3f2a1c",
        createdAt: "2026-09-22T09:00:00Z",
        engine: "a".repeat(40),
        tinyui: "0.3.0",
        hashes,
        requires: {
            "fixture/home": { components: ["ta.Icon"], capabilities: ["billing.prices"] },
            "fixture/orders": { components: [], capabilities: [] },
        },
    };
    edit(manifest);
    await writeFile(join(dir, "manifest.json"), JSON.stringify(manifest, null, 2) + "\n");
    return dir;
}

export interface RecordedRequest {
    method: string;
    path: string;
    /** The `Authorization` header verbatim: the CLI must never send a request without one. */
    auth: string | undefined;
    contentType: string | undefined;
    contentLength: string | undefined;
    body: Buffer;
}

export interface FakeServer {
    url: string;
    requests: RecordedRequest[];
    /** Queued `{ status, body }` replies; a path with no reply gets the default for its shape. */
    reply(path: string, response: { status: number; body?: unknown }): void;
    close(): Promise<void>;
}

/**
 * The response shapes of docs/updates.md §6, and nothing else: it deliberately does not verify
 * signatures, hashes or token scope, so it cannot drift from the parts the server's own tests own.
 */
export async function startFakeServer(): Promise<FakeServer> {
    const requests: RecordedRequest[] = [];
    const queued = new Map<string, { status: number; body?: unknown }>();
    const uploads = new Map<string, string>();
    const hosts = new Map<string, Buffer>();

    const server = createServer((req: IncomingMessage, res: ServerResponse) => {
        const chunks: Buffer[] = [];
        req.on("data", (chunk: Buffer) => chunks.push(chunk));
        req.on("end", () => {
            const body = Buffer.concat(chunks);
            const path = req.url ?? "";
            requests.push({
                method: req.method ?? "",
                path,
                auth: req.headers.authorization,
                contentType: req.headers["content-type"],
                contentLength: req.headers["content-length"],
                body,
            });
            const send = (status: number, value: unknown) => {
                if (value === undefined) return res.writeHead(status).end();
                res.writeHead(status, { "content-type": "application/json" }).end(JSON.stringify(value));
            };
            try {
                const forced = queued.get(path);
                if (forced) {
                    queued.delete(path);
                    return send(forced.status, forced.body ?? { error: "forced by the test" });
                }
                if (!/^Bearer .+$/.test(req.headers.authorization ?? "")) return send(401, { error: "a token is required" });
                if (path.split("/")[3] === "hosts") return hostSnapshot(req.method ?? "", path, body, hosts, res);
                return send(...defaultReply(req.method ?? "", path, body, uploads));
            } catch (e) {
                // a hang here would look like a client bug, so every failure has to come back as a response
                return send(500, { error: `fake server: ${(e as Error).message}` });
            }
        });
    });
    await new Promise<void>((done) => server.listen(0, "127.0.0.1", done));
    const address = server.address();
    if (address === null || typeof address === "string") throw new Error("the fake server did not bind a port");
    return {
        url: `http://127.0.0.1:${address.port}`,
        requests,
        reply: (path, response) => queued.set(path, response),
        close: () => closeServer(server),
    };
}

function defaultReply(method: string, path: string, body: Buffer, uploads: Map<string, string>): [number, unknown] {
    const segments = path.split("/").filter((s) => s !== "");
    const last = segments.at(-1);
    const parsed = asJson(body);

    if (method === "PUT" && last === "current.json" && segments.length === 5) {
        const [app, , pkg, hostVersion] = segments;
        const version = parsed["version"] as string;
        // content first, pointer last: the one ordering rule a client can get wrong (§6.1)
        if (!uploads.has(`/${app}/${pkg}/${hostVersion}/${version}/manifest.json`)) return [409, { error: `${version}/manifest.json is not uploaded yet: content first, pointer last` }];
        return [200, { version, rollout: parsed["rollout"] ?? 100, signature: parsed["signature"] }];
    }
    // management routes first, exactly as the server registers them: `/apps/<a>/packages/<p>/publicKey`
    // is five segments and would otherwise be swallowed by the upload route
    if (segments[0] === "apps") {
        if (method === "POST" && path === "/apps") return [201, { ...parsed, createdAt: "2026-09-22T09:00:00Z" }];
        if (method === "POST" && segments.length === 3 && last === "tokens") return [201, { id: "app_1", token: "app-token-shown-once", createdAt: "2026-09-22T09:00:00Z" }];
        if (method === "DELETE" && segments.length === 4 && segments[2] === "tokens") return [204, undefined];
        if (method === "POST" && last === "packages") return [201, { ...parsed, createdAt: "2026-09-22T09:00:00Z" }];
        if (method === "PUT" && last === "publicKey") return [200, { name: segments.at(-2), ...parsed, createdAt: "2026-09-22T09:00:00Z", publicKeyUpdatedAt: "2026-09-22T09:05:00Z" }];
        if (method === "POST" && last === "tokens") return [201, { id: "tok_1", token: "publish-token-shown-once", channels: parsed["channels"], createdAt: "2026-09-22T09:00:00Z" }];
        if (method === "DELETE" && segments.at(-2) === "tokens") return [204, undefined];
        return [404, { error: "not found" }];
    }
    if (method === "PUT" && segments.length >= 5) {
        const sha256 = createHash("sha256").update(body).digest("hex");
        const existing = uploads.get(path);
        if (existing === undefined) {
            uploads.set(path, sha256);
            return [201, { path: segments.slice(4).join("/"), sha256, existing: false }];
        }
        if (existing !== sha256) return [409, { error: `${path} already exists with different content` }];
        return [200, { path: segments.slice(4).join("/"), sha256, existing: true }];
    }
    if (method === "GET" && last === "releases") {
        return [
            200,
            {
                versions: [
                    { version: "20260922T090000Z-3f2a1c", createdAt: "2026-09-22T09:00:00Z", publishedAt: "2026-09-22T09:05:00Z" },
                    { version: "20260921T080000Z-aaaaaa", createdAt: "2026-09-21T08:00:00Z", publishedAt: "2026-09-21T08:05:00Z" },
                ],
                channels: { production: { version: "20260921T080000Z-aaaaaa", rollout: 100 }, staging: { version: "20260922T090000Z-3f2a1c", rollout: 20 } },
            },
        ];
    }
    if (method === "POST" && last === "pointer") return [200, { version: parsed["version"] ?? "20260921T080000Z-aaaaaa", rollout: parsed["rollout"] ?? 100, signature: "recorded-signature" }];
    return [404, { error: "not found" }];
}

/** `/apps/<app>/hosts/<hostVersion>`: written once, read back byte for byte, 404 until written. */
function hostSnapshot(method: string, path: string, body: Buffer, hosts: Map<string, Buffer>, res: ServerResponse) {
    const stored = hosts.get(path);
    if (method === "GET") {
        if (!stored) return res.writeHead(404, { "content-type": "application/json" }).end(JSON.stringify({ error: "has no snapshot" }));
        return res.writeHead(200, { "content-type": "text/plain" }).end(stored);
    }
    const sha256 = createHash("sha256").update(body).digest("hex");
    if (stored && !stored.equals(body)) return res.writeHead(409, { "content-type": "application/json" }).end(JSON.stringify({ error: "a shipped host version is frozen" }));
    if (!stored) hosts.set(path, body);
    res.writeHead(stored ? 200 : 201, { "content-type": "application/json" }).end(JSON.stringify({ hostVersion: path.split("/")[4], sha256, existing: !!stored }));
}

/** Uploads carry bytecode, not JSON; only the pointer and the management bodies are objects. */
function asJson(body: Buffer): Record<string, unknown> {
    try {
        const value: unknown = body.length === 0 ? {} : JSON.parse(body.toString("utf8"));
        return typeof value === "object" && value !== null ? (value as Record<string, unknown>) : {};
    } catch {
        return {};
    }
}

function closeServer(server: Server): Promise<void> {
    return new Promise((done, fail) => {
        server.closeAllConnections();
        server.close((e) => (e ? fail(e) : done()));
    });
}

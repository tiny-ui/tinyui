import type { Pointer } from "./bundle.ts";
import { segments, type UpdatesClient } from "./client.ts";

// One function per endpoint of docs/updates.md §6.2 and §6.3; the CLI adds nothing but argument parsing.

export interface AppRecord {
    id: string;
    name: string;
    org?: string;
    createdAt: string;
}

export interface PackageRecord {
    name: string;
    publicKey: string;
    createdAt: string;
    publicKeyUpdatedAt?: string;
}

/** The only time the token itself is readable; the server keeps its sha256. */
export interface IssuedToken {
    id: string;
    token: string;
    channels: string[];
    createdAt: string;
}

/** An app token: the whole management of one app (docs/updates.md §6); readable only once, like publish tokens. */
export interface IssuedAppToken {
    id: string;
    token: string;
    createdAt: string;
}

/** A host snapshot upload: written once per (app, hostVersion) (docs/updates.md §6.4). */
export interface HostSnapshotResult {
    hostVersion: string;
    sha256: string;
    existing: boolean;
}

export interface Releases {
    versions: { version: string; createdAt: string; publishedAt?: string }[];
    channels: Record<string, { version: string; rollout: number }>;
}

export function createApp(client: UpdatesClient, body: { id: string; name: string; org?: string }): Promise<AppRecord> {
    return client.json<AppRecord>("POST", "/apps", body);
}

export function createPackage(client: UpdatesClient, app: string, body: { name: string; publicKey: string }): Promise<PackageRecord> {
    return client.json<PackageRecord>("POST", segments("apps", app, "packages"), body);
}

export function rotatePublicKey(client: UpdatesClient, app: string, pkg: string, publicKey: string): Promise<PackageRecord> {
    return client.json<PackageRecord>("PUT", `${segments("apps", app, "packages", pkg)}/publicKey`, { publicKey });
}

export function createToken(client: UpdatesClient, app: string, pkg: string, channels: string[]): Promise<IssuedToken> {
    return client.json<IssuedToken>("POST", segments("apps", app, "packages", pkg, "tokens"), { channels });
}

export function revokeToken(client: UpdatesClient, app: string, pkg: string, tokenId: string): Promise<void> {
    return client.empty("DELETE", segments("apps", app, "packages", pkg, "tokens", tokenId));
}

export function createAppToken(client: UpdatesClient, app: string): Promise<IssuedAppToken> {
    return client.json<IssuedAppToken>("POST", segments("apps", app, "tokens"));
}

export function revokeAppToken(client: UpdatesClient, app: string, tokenId: string): Promise<void> {
    return client.empty("DELETE", segments("apps", app, "tokens", tokenId));
}

export function uploadHostSnapshot(client: UpdatesClient, app: string, hostVersion: string, snapshot: Uint8Array): Promise<HostSnapshotResult> {
    return client.putBytes<HostSnapshotResult>(segments("apps", app, "hosts", hostVersion), snapshot);
}

export function readHostSnapshot(client: UpdatesClient, app: string, hostVersion: string): Promise<Uint8Array> {
    return client.bytes(segments("apps", app, "hosts", hostVersion));
}

export function listReleases(client: UpdatesClient, app: string, pkg: string, hostVersion: string): Promise<Releases> {
    return client.json<Releases>("GET", segments(app, pkg, hostVersion, "releases"));
}

/** Rollback, promotion between channels and a rollout change are one endpoint (docs/updates.md §6.2). */
export function movePointer(
    client: UpdatesClient,
    target: { app: string; channel: string; pkg: string; hostVersion: string },
    body: { version?: string; rollout?: number },
): Promise<Pointer> {
    return client.json<Pointer>("POST", segments(target.app, target.channel, target.pkg, target.hostVersion, "pointer"), body);
}

import { compareVersions } from "./version.ts";

/** The tinyui that added the `channels` section; older frozen snapshots lack it. */
const CHANNELS_SINCE = "0.8.0";

/** A host snapshot as `tinyui-host/<hostVersion>.txt` holds it (docs/updates.md §6.4). */
export interface HostSnapshot {
    hostVersion: string;
    /** The oldest tinyui a host of this version runs; later hosts of the same version may run newer ones (docs/updates.md §6.4). */
    tinyui: string;
    components: Set<string>;
    capabilities: Set<string>;
    channels: Set<string>;
}

/**
 * Two header lines, then `components`, `capabilities` and `channels` sections with one two-space-indented item per line.
 * A snapshot frozen before channels existed has no `channels` section and provides none.
 * The first word of an item is its name; the rest is for people and for the host's own check, not for this parser.
 */
export function parseHostSnapshot(text: string): HostSnapshot {
    const lines = text.split(/\r?\n/);
    const header = (index: number, key: string) => {
        const match = new RegExp(`^${key} (\\S+)$`).exec(lines[index] ?? "");
        if (!match) throw new Error(`host snapshot line ${index + 1} must be "${key} <value>", got "${lines[index] ?? ""}"`);
        return match[1]!;
    };
    const snapshot: HostSnapshot = { hostVersion: header(0, "hostVersion"), tinyui: header(1, "tinyui"), components: new Set(), capabilities: new Set(), channels: new Set() };
    // every section, once each, in order: a missing one would read as "provides nothing" and a check would pass on it
    const order = ["components", "capabilities", "channels"] as const;
    let seen = 0;
    let section: Set<string> | undefined;
    lines.slice(2).forEach((line, i) => {
        if (line.trim() === "") return;
        if (line === order[seen]) {
            section = snapshot[line];
            seen++;
        } else if (line.startsWith("  ") && section) {
            section.add(line.trim().split(/\s+/)[0]!);
        } else {
            throw new Error(`host snapshot line ${i + 3}: expected ${seen < order.length ? `the "${order[seen]}" section` : "an item"}${section ? " or an indented item" : ""}, got "${line}"`);
        }
    });
    if (seen === 2 && compareVersions(snapshot.tinyui, CHANNELS_SINCE) < 0) return snapshot;
    if (seen < order.length) throw new Error(`host snapshot has no "${order[seen]}" section; write it even when empty`);
    return snapshot;
}

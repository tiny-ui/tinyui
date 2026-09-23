/** A host snapshot as `tinyui-host/<hostVersion>.txt` holds it (docs/updates.md §6.4). */
export interface HostSnapshot {
    hostVersion: string;
    tinyui: string;
    components: Set<string>;
    capabilities: Set<string>;
}

/**
 * Two header lines, then `components` and `capabilities` sections with one two-space-indented item per line.
 * The first word of an item is its name; the rest is for people and for the host's own check, not for this parser.
 */
export function parseHostSnapshot(text: string): HostSnapshot {
    const lines = text.split(/\r?\n/);
    const header = (index: number, key: string) => {
        const match = new RegExp(`^${key} (\\S+)$`).exec(lines[index] ?? "");
        if (!match) throw new Error(`host snapshot line ${index + 1} must be "${key} <value>", got "${lines[index] ?? ""}"`);
        return match[1]!;
    };
    const snapshot: HostSnapshot = { hostVersion: header(0, "hostVersion"), tinyui: header(1, "tinyui"), components: new Set(), capabilities: new Set() };
    let section: Set<string> | undefined;
    lines.slice(2).forEach((line, i) => {
        if (line.trim() === "") return;
        if (line === "components") section = snapshot.components;
        else if (line === "capabilities") section = snapshot.capabilities;
        else if (line.startsWith("  ") && section && line.trim() !== "") section.add(line.trim().split(/\s+/)[0]!);
        else throw new Error(`host snapshot line ${i + 3} is neither a section nor an item under one: "${line}"`);
    });
    return snapshot;
}

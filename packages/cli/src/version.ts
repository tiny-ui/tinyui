/**
 * Whether pages built against tinyui [pkg] run on a host whose tinyui is [host]: same major, host not older
 * (docs/updates.md §1.1). The Kotlin side has the same rule in `TinyUIVersion.kt`.
 */
export function isCompatible(host: string, pkg: string): boolean {
    const h = parse(host);
    const p = parse(pkg);
    return h.core[0] === p.core[0] && compare(h, p) >= 0;
}

interface Version {
    core: [number, number, number];
    pre: string[];
}

function parse(version: string): Version {
    const m = /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-([0-9A-Za-z.-]+))?$/.exec(version);
    if (!m) throw new Error(`"${version}" is not a tinyui version (major.minor.patch)`);
    return { core: [Number(m[1]), Number(m[2]), Number(m[3])], pre: m[4] ? m[4].split(".") : [] };
}

/** semver precedence: a pre-release sorts before its release, identifiers compare numerically when both are numbers. */
function compare(a: Version, b: Version): number {
    for (let i = 0; i < 3; i++) if (a.core[i] !== b.core[i]) return a.core[i]! - b.core[i]!;
    if (!a.pre.length || !b.pre.length) return b.pre.length - a.pre.length;
    for (let i = 0; i < Math.min(a.pre.length, b.pre.length); i++) {
        const x = a.pre[i]!;
        const y = b.pre[i]!;
        if (x === y) continue;
        const nx = /^\d+$/.test(x);
        const ny = /^\d+$/.test(y);
        if (nx && ny) return Number(x) - Number(y);
        if (nx !== ny) return nx ? -1 : 1;
        return x < y ? -1 : 1;
    }
    return a.pre.length - b.pre.length;
}

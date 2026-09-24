/**
 * Whether pages built against tinyui [pkg] run on a host whose tinyui is [host]: same major, host not older
 * (docs/updates.md §1.1). The Kotlin side has the same rule in `TinyUI.isCompatible`.
 */
export function isCompatible(host: string, pkg: string): boolean {
    const h = parse(host);
    const p = parse(pkg);
    return h.core[0] === p.core[0] && compare(h, p) >= 0;
}

interface Version {
    core: [string, string, string];
    pre: string[];
}

const NUMERIC = /^(0|[1-9]\d*)$/;

function parse(version: string): Version {
    const m = /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?$/.exec(version);
    const pre = m?.[4]?.split(".") ?? [];
    if (!m || pre.some((id) => /^\d+$/.test(id) && !NUMERIC.test(id))) throw new Error(`"${version}" is not a tinyui version (major.minor.patch)`);
    return { core: [m[1]!, m[2]!, m[3]!], pre };
}

/** Digits without leading zeros, compared as text so no identifier is too large for a number type. */
function compareNumeric(a: string, b: string): number {
    return a.length !== b.length ? a.length - b.length : a < b ? -1 : a > b ? 1 : 0;
}

/** semver precedence: a pre-release sorts before its release; numeric identifiers sort below alphanumeric ones. */
function compare(a: Version, b: Version): number {
    for (let i = 0; i < 3; i++) {
        const c = compareNumeric(a.core[i]!, b.core[i]!);
        if (c !== 0) return c;
    }
    if (!a.pre.length || !b.pre.length) return b.pre.length - a.pre.length;
    for (let i = 0; i < Math.min(a.pre.length, b.pre.length); i++) {
        const x = a.pre[i]!;
        const y = b.pre[i]!;
        if (x === y) continue;
        const nx = NUMERIC.test(x);
        const ny = NUMERIC.test(y);
        if (nx && ny) return compareNumeric(x, y);
        if (nx !== ny) return nx ? -1 : 1;
        return x < y ? -1 : 1;
    }
    return a.pre.length - b.pre.length;
}

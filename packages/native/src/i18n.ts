// docs/native-api.md §8: the package's own strings; the framework loads them and follows the host's language.
import { HostError, internal, signal } from "tinyui-core";

/** Augmented by `tinyui i18n --ts`: each key and the names of its `{placeholders}` (never when it has none). */
export interface I18nKeys {}

type Key = [keyof I18nKeys] extends [never] ? string : keyof I18nKeys & string;
type Args<K> = K extends keyof I18nKeys
    ? [I18nKeys[K]] extends [never] ? [] : [args: Record<I18nKeys[K] & string, string | number>]
    : [args?: Record<string, string | number>];

const [current, setCurrent] = signal("");
const [zone, setZone] = signal("");
let subscribed = false;

function follow(): void {
    if (subscribed) return;
    subscribed = true;
    setCurrent(internal.query<string>("i18n.locale"));
    setZone(internal.query<string>("i18n.timeZone"));
    internal.send("i18n.subscribe", {});
    internal.listen("i18n.locale", (p) => setCurrent((p as { locale: string }).locale));
    internal.listen("i18n.timeZone", (p) => setZone((p as { timeZone: string }).timeZone));
}

/** The language strings resolve against (BCP 47); an accessor, bindings re-run when the host changes it. */
export function locale(): string {
    follow();
    return current();
}

/** The string for [key] in the current language, falling back towards the package default; `{name}` filled from args. */
export function t<K extends Key>(key: K, ...rest: Args<K>): string {
    follow();
    current();
    const text = internal.query<string>("i18n.t", { key });
    const args = rest[0] as Record<string, string | number> | undefined;
    return args ? text.replace(/\{([A-Za-z0-9_]+)\}/g, (m, name: string) => (name in args ? String(args[name]) : m)) : text;
}

/** The time zone dates are shown in (IANA, e.g. "Asia/Shanghai"); an accessor, bindings re-run when the host changes it. */
export function timeZone(): string {
    follow();
    return zone();
}

// a field is a whole run of one letter, so "MMM" is rejected rather than read as "MM" + "M"; a quoted run is literal and '' one quote (Unicode date patterns)
const TOKEN = /'((?:[^']|'')*)'|([A-Za-z])\2*/g;
const pad = (n: number, width: number) => String(n).padStart(width, "0");

/**
 * [epochMs] in the current [timeZone] as [pattern]: numeric fields only (`yyyy MM M dd d HH H mm ss SSS`), anything
 * else between letters is kept as is, `'…'` quotes letters. Another letter throws HostError E_INVALID.
 */
export function formatDate(epochMs: number, pattern: string): string {
    follow();
    zone();
    // beyond ±8.64e15 ms Date is invalid and every field would read NaN
    const invalid = () => new HostError("E_INVALID", `i18n.formatDate: ${epochMs} is not a time Date can hold`);
    // isFinite first: Date would coerce null / true / "1" into a time
    if (!Number.isFinite(epochMs) || Number.isNaN(new Date(epochMs).getTime())) throw invalid();
    // the offset at that instant, so daylight saving follows the date rather than today
    const d = new Date(epochMs + internal.query<number>("i18n.zoneOffset", { ms: epochMs }) * 60_000);
    if (Number.isNaN(d.getTime())) throw invalid();
    return pattern.replace(TOKEN, (m, quoted: string | undefined) => {
        if (quoted !== undefined) return quoted === "" ? "'" : quoted.replaceAll("''", "'");
        switch (m) {
            case "yyyy": return pad(d.getUTCFullYear(), 4);
            case "MM": return pad(d.getUTCMonth() + 1, 2);
            case "M": return String(d.getUTCMonth() + 1);
            case "dd": return pad(d.getUTCDate(), 2);
            case "d": return String(d.getUTCDate());
            case "HH": return pad(d.getUTCHours(), 2);
            case "H": return String(d.getUTCHours());
            case "mm": return pad(d.getUTCMinutes(), 2);
            case "ss": return pad(d.getUTCSeconds(), 2);
            case "SSS": return pad(d.getUTCMilliseconds(), 3);
            default: throw new HostError("E_INVALID", `i18n.formatDate: "${m}" in "${pattern}" is not a supported field`);
        }
    });
}

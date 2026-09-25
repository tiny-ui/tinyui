// docs/native-api.md §8: the package's own strings; the framework loads them and follows the host's language.
import { internal, signal } from "tinyui-core";

/** Augmented by `tinyui i18n --ts`: each key and the names of its `{placeholders}` (never when it has none). */
export interface I18nKeys {}

type Key = [keyof I18nKeys] extends [never] ? string : keyof I18nKeys & string;
type Args<K> = K extends keyof I18nKeys
    ? [I18nKeys[K]] extends [never] ? [] : [args: Record<I18nKeys[K] & string, string | number>]
    : [args?: Record<string, string | number>];

const [current, setCurrent] = signal("");
let subscribed = false;

function follow(): void {
    if (subscribed) return;
    subscribed = true;
    setCurrent(internal.query<string>("i18n.locale"));
    internal.send("i18n.subscribe", {});
    internal.listen("i18n.locale", (p) => setCurrent((p as { locale: string }).locale));
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

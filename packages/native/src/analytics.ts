// docs/native-api.md §12: event names and fields belong to the package; the host only forwards them.
import { HostError, internal } from "tinyui-core";

export type Props = Record<string, string | number | boolean | null>;

/** J4, no result. [props] is one flat level of primitives. */
export function track(name: string, props: Props = {}): void {
    for (const [k, v] of Object.entries(props)) {
        if (v !== null && typeof v !== "string" && typeof v !== "number" && typeof v !== "boolean") {
            throw new HostError("E_INVALID", `analytics.track("${name}"): ${k} is not a string, number, boolean or null`);
        }
    }
    internal.send("analytics.track", { name, props });
}

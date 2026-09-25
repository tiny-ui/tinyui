// docs/native-api.md §11
import { internal } from "tinyui-core";

/** Hands [url] to the host's link opener (or the system); resolves once handed over, not when the user returns. */
export function openUrl(url: string): Promise<void> {
    return internal.call("linking.openUrl", { url });
}

// docs/native-api.md §15: write-only, so reading never prompts the user (iOS paste banner) and no page sees what others copied.
import { internal } from "tinyui-core";

/** Puts plain [text] on the system clipboard; resolves once it is there. Tell the user yourself (`ui.toast`) if you want to. */
export function setText(text: string): Promise<void> {
    return internal.call("clipboard.setText", { text });
}

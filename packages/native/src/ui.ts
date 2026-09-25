// docs/native-api.md §10: M3 snackbar and text dialogs drawn by the page itself.
import { internal } from "tinyui-core";

export interface ToastOptions {
    action?: string;
    duration?: "short" | "long";
}

/** Resolves "action" when the action was tapped, "dismissed" otherwise. */
export function toast(message: string, options: ToastOptions = {}): Promise<"action" | "dismissed"> {
    return internal.call("ui.toast", { message, ...options });
}

export interface AlertOptions {
    title?: string;
    message: string;
    /** defaults to the framework's own "OK" in the current language */
    confirm?: string;
}

export function alert(options: AlertOptions): Promise<void> {
    return internal.call("ui.alert", { ...options });
}

export interface ConfirmOptions extends AlertOptions {
    cancel?: string;
}

/** True when confirmed; dismissing counts as cancel. */
export function confirm(options: ConfirmOptions): Promise<boolean> {
    return internal.call("ui.confirm", { ...options });
}

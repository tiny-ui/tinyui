// docs/native-api.md §9: the login session the host provides; no token ever reaches the page.
import { internal, signal } from "tinyui-core";

export interface Session {
    loggedIn: boolean;
    /** Stable and not secret; null when logged out. */
    userId: string | null;
}

const [current, setCurrent] = signal<Session>({ loggedIn: false, userId: null });
let subscribed = false;

/** An accessor kept current by the host; bindings re-run on login and logout. */
export function state(): Session {
    if (!subscribed) {
        subscribed = true;
        setCurrent(internal.query<Session>("session.get"));
        internal.send("session.subscribe", {});
        internal.listen("session", (p) => setCurrent(p as Session));
    }
    return current();
}

/** Starts the host's login flow; settles when the flow ends, read [state] for the outcome. E_UNSUPPORTED without a session. */
export function signIn(source = ""): Promise<void> {
    return internal.call<void>("session.signIn", { source });
}

/** The loopback hosts `requireSecureUrl` accepts over plain http, in the form undici's NO_PROXY matching expects. */
const LOOPBACK = ["localhost", "127.0.0.1", "[::1]"];

/**
 * Node's built-in fetch ignores the proxy environment variables that curl, npm and pnpm honour (unless the process was
 * started with NODE_USE_ENV_PROXY), so behind a proxy every command that talks to the updates server fails with
 * "fetch failed". When one is set, route the global fetch through undici's EnvHttpProxyAgent, which also reads NO_PROXY.
 * undici is imported only then: environments without a proxy, such as reproducible builds, never load it.
 *
 * Loopback always goes direct: a local instance would be unreachable through the proxy, and over plain http the proxy
 * would see the bearer token. A proxy setting the agent rejects is reported and skipped rather than failing the
 * command, so offline commands (build, bundle, schema) keep working.
 */
export async function useEnvProxy(env: NodeJS.ProcessEnv = process.env): Promise<boolean> {
    // `||`, not `??`: an empty uppercase variable must not hide a configured lowercase one
    const httpProxy = env.HTTP_PROXY || env.http_proxy;
    const httpsProxy = env.HTTPS_PROXY || env.https_proxy;
    if (!httpProxy && !httpsProxy) return false;
    const noProxy = [env.NO_PROXY || env.no_proxy, ...LOOPBACK].filter(Boolean).join(",");
    let undici: typeof import("undici") | undefined;
    try {
        undici = await import("undici");
        undici.setGlobalDispatcher(new undici.EnvHttpProxyAgent({
            ...(httpProxy ? { httpProxy } : {}),
            ...(httpsProxy ? { httpsProxy } : {}),
            noProxy,
        }));
        return true;
    } catch (e) {
        // make "directly" true even if an earlier call had installed a proxy
        undici?.setGlobalDispatcher(new undici.Agent());
        process.stderr.write(`tinyui: ignoring the proxy settings (${e instanceof Error ? e.message : String(e)}); connecting directly\n`);
        return false;
    }
}

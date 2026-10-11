const PROXY_ENV = ["HTTPS_PROXY", "https_proxy", "HTTP_PROXY", "http_proxy"] as const;

/**
 * Node's built-in fetch ignores the proxy environment variables that curl, npm and pnpm honour (unless the process was
 * started with NODE_USE_ENV_PROXY), so behind a proxy every command that talks to the updates server fails with
 * "fetch failed". When one is set, route the global fetch through undici's EnvHttpProxyAgent, which also reads NO_PROXY.
 * undici is imported only then: environments without a proxy, such as reproducible builds, never load it.
 */
export async function useEnvProxy(env: NodeJS.ProcessEnv = process.env): Promise<boolean> {
    if (!PROXY_ENV.some((name) => env[name])) return false;
    const { EnvHttpProxyAgent, setGlobalDispatcher } = await import("undici");
    const httpProxy = env.HTTP_PROXY ?? env.http_proxy;
    const httpsProxy = env.HTTPS_PROXY ?? env.https_proxy;
    const noProxy = env.NO_PROXY ?? env.no_proxy;
    setGlobalDispatcher(new EnvHttpProxyAgent({
        ...(httpProxy !== undefined && { httpProxy }),
        ...(httpsProxy !== undefined && { httpsProxy }),
        ...(noProxy !== undefined && { noProxy }),
    }));
    return true;
}

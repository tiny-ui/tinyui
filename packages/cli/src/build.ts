import { build as esbuild, type Plugin } from "esbuild";
import { execFile } from "node:child_process";
import { createHash } from "node:crypto";
import { mkdir, readdir, readFile, rm, writeFile } from "node:fs/promises";
import { createRequire } from "node:module";
import { dirname, isAbsolute, join, relative, resolve, sep } from "node:path";
import { pathToFileURL } from "node:url";
import { promisify } from "node:util";
import { isPathSegment } from "./bundle.ts";
import { loadConfig, type TinyUIConfig } from "./config.ts";
import { compileModule, findQjsc } from "./qjsc.ts";
import { analyzePage, RequiresError, type PageRequires } from "./requires.ts";
import { TransformError, transformJsx } from "./transform.ts";

const execFileAsync = promisify(execFile);

/** Runtime module name as the engine sees it → output file under `runtime/`. */
export const RUNTIME_MODULES = { "tinyui-core": "core", "tinyui-native": "native" } as const;

export interface BuildOptions {
    /** Project root: `tinyui.config.json` lives here, packages resolve from `<root>/node_modules`. */
    root: string;
    /** Output directory; defaults to `<root>/dist`. */
    out?: string;
    /** Path to `qjsc-kmp`; falls back to `TINYUI_QJSC`, the `qjsc-kmp` npm package, then `PATH`. */
    qjsc?: string;
    /** Emit only the ESM sources and skip bytecode; for debugging the transform without an engine build. */
    jsOnly?: boolean;
    /** Package version to record instead of the derived `<createdAt>-<git sha>`. */
    version?: string;
}

export interface BuiltModule {
    /** Module name as the engine sees it: `<pkg>/home`, `tinyui-core`. */
    name: string;
    js: string;
    map: string;
    /** First 8 hex digits of the sha256 of the ESM output: pairs a stack trace with its source map (docs/build-chain.md). */
    buildId: string;
    bin?: string;
}

export interface BuildResult {
    runtime: BuiltModule[];
    pages: BuiltModule[];
    manifest: string;
}

/** `manifest.json` as `tinyui build` writes it (docs/updates.md §1.1). */
export interface Manifest {
    runtime: string[];
    pages: string[];
    /** Module name → output path without extension (`runtime/core`, `pages/home`); hosts locate `.bin` / `.js.map` through it. */
    files: Record<string, string>;
    buildIds: Record<string, string>;
    name: string;
    publicKey: string;
    version: string;
    createdAt: string;
    /** Engine commit the bytecode is bound to; empty when built with `jsOnly`. */
    engine: string;
    protocol: number;
    /** Module name → sha256 hex of its `.bin`; empty when built with `jsOnly`. */
    hashes: Record<string, string>;
    /** Page module name → the host capabilities and host components it uses (docs/updates.md §1.3). */
    requires: Record<string, PageRequires>;
}

export async function build(options: BuildOptions): Promise<BuildResult> {
    const root = resolve(options.root);
    const out = resolve(options.out ?? join(root, "dist"));
    const config = await loadConfig(root);
    if (options.version !== undefined && !isPathSegment(options.version)) throw new Error(`version must be a single path segment, got "${options.version}"`);
    const pagesDir = join(root, config.pages);
    const pageNames = await discoverPages(config.name, pagesDir);
    if (pageNames.size === 0) throw new Error(`no pages found under ${pagesDir}`);

    const qjsc = options.jsOnly ? undefined : await findQjsc(options.qjsc);
    if (!options.jsOnly && !qjsc) {
        throw new Error("qjsc-kmp not found: the qjsc-kmp npm package has no binary for this machine or was installed without optional dependencies; pass --qjsc, set TINYUI_QJSC, or put it on PATH (or use --js-only)");
    }

    // stale outputs would otherwise be packaged along with the current pages
    await rm(join(out, "pages"), { recursive: true, force: true });
    await rm(join(out, "runtime"), { recursive: true, force: true });
    const runtime = await bundleRuntime(root, out);
    const pages = await bundlePages(root, out, config, pagesDir, pageNames);
    const modules = [...runtime, ...pages];
    const requires = await pageRequires(pages);
    for (const m of modules) {
        m.buildId = createHash("sha256").update(await readFile(m.js)).digest("hex").slice(0, 8);
        await rootRelativeSources(root, m.map);
    }
    const hashes: Record<string, string> = {};
    if (qjsc) {
        for (const m of modules) {
            m.bin = m.js.replace(/\.js$/, ".bin");
            await compileModule({ qjsc, input: m.js, output: m.bin, name: m.name });
            hashes[m.name] = createHash("sha256").update(await readFile(m.bin)).digest("hex");
        }
    }

    const createdAt = new Date().toISOString().replace(/\.\d{3}Z$/, "Z");
    const manifest = join(out, "manifest.json");
    const content: Manifest = {
        runtime: runtime.map((m) => m.name),
        pages: pages.map((m) => m.name),
        files: Object.fromEntries(modules.map((m) => [m.name, relative(out, m.js).replace(/\.js$/, "").split(sep).join("/")])),
        buildIds: Object.fromEntries(modules.map((m) => [m.name, m.buildId])),
        name: config.name,
        publicKey: config.publicKey,
        version: options.version ?? `${createdAt.replace(/[-:]/g, "")}-${await gitShortSha(root)}`,
        createdAt,
        engine: runtime[0]?.bin ? await engineCommit(runtime[0].bin) : "",
        protocol: await runtimeProtocol(root),
        hashes,
        requires,
    };
    await writeFile(manifest, JSON.stringify(content, null, 2) + "\n");
    return { runtime, pages, manifest };
}

/** Every page's host usage; all pages are checked before failing, so one build reports every violation. */
async function pageRequires(pages: BuiltModule[]): Promise<Record<string, PageRequires>> {
    const requires: Record<string, PageRequires> = {};
    const failures: string[] = [];
    for (const page of pages) {
        try {
            requires[page.name] = analyzePage(page.name, await readFile(page.js, "utf8"));
        } catch (e) {
            if (!(e instanceof RequiresError)) throw e;
            failures.push(e.message);
        }
    }
    if (failures.length) throw new RequiresError(failures.join("\n"));
    return requires;
}

async function discoverPages(pkg: string, pagesDir: string): Promise<Map<string, string>> {
    const found = new Map<string, string>();
    let entries;
    try {
        entries = await readdir(pagesDir, { recursive: true, withFileTypes: true });
    } catch {
        return found;
    }
    for (const e of entries) {
        if (!e.isFile() || !/\.tsx?$/.test(e.name) || e.name.endsWith(".d.ts")) continue;
        const file = join(e.parentPath, e.name);
        const path = relative(pagesDir, file).replace(/\.tsx?$/, "").split(sep).join("/");
        // the page path reaches devices as URL segments, where it has to survive three implementations of
        // percent-encoding; the charset is the same one versions and package names are held to (docs/updates.md §6.1)
        for (const segment of path.split("/")) {
            if (!isPathSegment(segment)) throw new Error(`page ${file}: "${segment}" must match [A-Za-z0-9._-]+; a page path becomes a URL segment when the package is served`);
        }
        const name = `${pkg}/${path}`;
        const clash = found.get(name);
        if (clash) throw new Error(`page ${name} has two sources: ${clash} and ${file}`);
        found.set(name, file);
    }
    return new Map([...found].sort(([a], [b]) => (a < b ? -1 : 1)));
}

async function bundleRuntime(root: string, out: string): Promise<BuiltModule[]> {
    const built: BuiltModule[] = [];
    for (const [name, file] of Object.entries(RUNTIME_MODULES)) {
        const outfile = join(out, "runtime", file + ".js");
        await mkdir(dirname(outfile), { recursive: true });
        await esbuild({
            ...common(root),
            entryPoints: [name],
            outfile,
            external: Object.keys(RUNTIME_MODULES).filter((m) => m !== name),
        });
        built.push({ name, js: outfile, map: outfile + ".map", buildId: "" });
    }
    return built;
}

async function bundlePages(root: string, out: string, config: TinyUIConfig, pagesDir: string, pages: Map<string, string>): Promise<BuiltModule[]> {
    const outdir = join(out, "pages");
    const prefix = `${config.name}/`;
    await esbuild({
        ...common(root),
        entryPoints: [...pages].map(([name, file]) => ({ in: file, out: name.slice(prefix.length) })),
        outdir,
        outbase: pagesDir,
        // the project's tsconfig says react-jsx for type checking; the output is classic h() regardless
        tsconfigRaw: { compilerOptions: { jsx: "react", jsxFactory: "h", jsxFragmentFactory: "Fragment" } },
        jsx: "transform",
        jsxFactory: "h",
        jsxFragment: "Fragment",
        inject: [JSX_SHIM],
        plugins: [pagePlugin],
    });
    return [...pages.keys()].map((name) => {
        const js = join(outdir, name.slice(prefix.length) + ".js");
        return { name, js, map: js + ".map", buildId: "" };
    });
}

/** esbuild writes `sources` relative to the map; the runtime and offline symbolication want paths from the project root. */
async function rootRelativeSources(root: string, mapFile: string): Promise<void> {
    const map = JSON.parse(await readFile(mapFile, "utf8")) as { sources: string[] };
    map.sources = map.sources.map((s) => {
        // a bare scheme (`tinyui:jsx-shim`) stays; an absolute path (`/x` or `C:\x`) or a map-relative one becomes root-relative
        if (!isAbsolute(s) && /^[a-z][a-z0-9+.-]*:/i.test(s)) return s;
        return relative(root, resolve(dirname(mapFile), s)).split(sep).join("/");
    });
    await writeFile(mapFile, JSON.stringify(map));
}

/** Engine commit from the bytecode file header (quickjs-kmp `native/shim/quickjs_kmp.c`, `kmp_bc_header`). */
async function engineCommit(bin: string): Promise<string> {
    const bytes = await readFile(bin);
    if (bytes.subarray(0, 4).toString("latin1") !== "QJKB" || bytes.length < 52) throw new Error(`${bin} is not quickjs-kmp bytecode`);
    const commit = bytes.subarray(12, 52).toString("latin1");
    if (!/^[0-9a-f]{40}$/.test(commit)) throw new Error(`${bin}: unexpected engine commit in the bytecode header`);
    return commit;
}

/** `PROTOCOL` of the `tinyui-core` the pages resolve to, through its constants-only subpath export. */
async function runtimeProtocol(root: string): Promise<number> {
    const file = createRequire(join(root, "package.json")).resolve("tinyui-core/protocol");
    const { PROTOCOL } = (await import(pathToFileURL(file).href)) as { PROTOCOL: unknown };
    if (typeof PROTOCOL !== "number") throw new Error(`${file} does not export PROTOCOL`);
    return PROTOCOL;
}

async function gitShortSha(root: string): Promise<string> {
    try {
        const { stdout } = await execFileAsync("git", ["rev-parse", "--short", "HEAD"], { cwd: root });
        return stdout.trim() || "nogit";
    } catch {
        return "nogit";
    }
}

const JSX_SHIM = "tinyui:jsx-shim";
const TSX = /\.tsx$/;

/** `inject` wants a module; this serves one in memory so pages get `h` / `Fragment` from the runtime module. */
const pagePlugin: Plugin = {
    name: "tinyui-pages",
    setup(api) {
        api.onResolve({ filter: /^tinyui:jsx-shim$/ }, (args) => ({ path: args.path, namespace: "tinyui" }));
        // Runtime modules stay bare specifiers and are pure, so a page that never uses JSX keeps no import of h
        api.onResolve({ filter: /^tinyui-(core|native)$/ }, (args) => ({ path: args.path, external: true, sideEffects: false }));
        api.onLoad({ filter: /.*/, namespace: "tinyui" }, () => ({
            contents: 'export { h, Fragment, thunk } from "tinyui-core";',
            loader: "js",
        }));
        // docs/jsx-transform.md: wrap dynamic attributes before esbuild turns JSX into h()
        api.onLoad({ filter: TSX }, async (args) => {
            const source = await readFile(args.path, "utf8");
            try {
                // the map's `source` is resolved against the file's directory, so it has to be the absolute path
                const { code, map } = transformJsx(args.path, source);
                const inline = Buffer.from(map).toString("base64");
                return { contents: `${code}\n//# sourceMappingURL=data:application/json;base64,${inline}`, loader: "tsx" };
            } catch (e) {
                if (e instanceof TransformError) {
                    return { errors: [{ text: e.message.slice(e.message.indexOf(": ") + 2), location: { file: args.path, line: e.line, column: e.column - 1 } }] };
                }
                throw e;
            }
        });
    },
};

function common(root: string) {
    return {
        absWorkingDir: root,
        bundle: true,
        format: "esm" as const,
        platform: "neutral" as const,
        target: "esnext",
        sourcemap: true as const,
        logLevel: "silent" as const,
        splitting: false,
        treeShaking: true,
        legalComments: "none" as const,
    };
}

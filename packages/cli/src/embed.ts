// `tinyui embed`: the package built from this source, written as a host's embedded package (docs/updates.md §1.4).
import { readdir, readFile, stat } from "node:fs/promises";
import { dirname, isAbsolute, join, relative, resolve, sep } from "node:path";
import { build, type Manifest } from "./build.ts";
import { payload, replaceDir } from "./payload.ts";

export interface EmbedOptions {
    /** Project root, as for `tinyui build`. */
    root: string;
    /** The host's embedded package directory for this package; replaced as a whole. The same directory `tinyui pull` takes. */
    out: string;
    /** Where the build goes; defaults to `<root>/dist`. */
    dist?: string;
    /** Also write each page's source map, so a failing page shows its JS stack; they carry the page sources. */
    maps?: boolean;
    qjsc?: string;
}

export interface EmbedResult {
    version: string;
    /** What [EmbedOptions.out] now holds, sorted. */
    files: string[];
}

/**
 * Builds the package and makes [EmbedOptions.out] hold it the way a host embeds one: `manifest.json`, each page's
 * bytecode, the strings, and nothing else of the build. `tinyui pull` fills the same directory from a channel; this
 * fills it from the source at hand, for running a change in the host before anything is published.
 */
export async function embed(options: EmbedOptions): Promise<EmbedResult> {
    const out = resolve(options.out);
    const dist = resolve(options.dist ?? join(options.root, "dist"));
    // the build empties and rewrites its directory: pointed at the package (or around it), it would damage what is there before anything is checked
    if (within(dist, out) || within(out, dist)) throw new Error(`--dist ${dist} and --out ${out} overlap: the build needs a directory of its own`);
    await requireEmbeddable(out);
    const held = await heldPackage(out);
    const result = await build({ root: options.root, out: dist, ...(options.qjsc !== undefined && { qjsc: options.qjsc }) });
    const manifestBytes = await readFile(result.manifest);
    const manifest = JSON.parse(manifestBytes.toString("utf8")) as Manifest;
    if (held !== null && held !== manifest.name) throw new Error(`${out} holds package ${held}, not ${manifest.name}`);

    const paths = payload(manifest).map((file) => file.path);
    if (options.maps) for (const module of manifest.pages) paths.push(`${manifest.files[module]}.js.map`);
    const files = new Map<string, Uint8Array>();
    for (const path of paths) files.set(path, await readFile(join(dist, ...path.split("/"))));
    files.set("manifest.json", manifestBytes);
    await replaceDir(out, files);
    return { version: manifest.version, files: [...files.keys()].sort() };
}

/** The package name in [out]'s manifest; null when [out] has no manifest. A manifest that names no package is not one to replace. */
async function heldPackage(out: string): Promise<string | null> {
    const file = join(out, "manifest.json");
    const text = await readFile(file, "utf8").catch(() => null);
    if (text === null) return null;
    let name: unknown;
    try {
        name = (JSON.parse(text) as { name?: unknown }).name;
    } catch {
        name = undefined;
    }
    if (typeof name !== "string" || name === "") throw new Error(`${file} names no package: --out is one package's embedded directory and is replaced as a whole`);
    return name;
}

/** Whether [inner] is [outer] or lies inside it. */
function within(inner: string, outer: string): boolean {
    const rel = relative(outer, inner);
    // a directory may itself be called `..name`: only `..` and `../…` lead out
    return rel === "" || (rel !== ".." && !rel.startsWith(`..${sep}`) && !isAbsolute(rel));
}

/**
 * [out] is replaced as a whole, so it has to be a package directory already, or empty, or not there yet; and it is
 * one directory inside the host's resources, so its parent has to exist: a mistyped path must not grow a tree of its own.
 */
async function requireEmbeddable(out: string): Promise<void> {
    const parent = dirname(out);
    if (!(await stat(parent).then((s) => s.isDirectory(), () => false))) {
        throw new Error(`${parent} does not exist: --out is <the host's embedded packages directory>/<package>`);
    }
    const entries = await readdir(out).catch((e: NodeJS.ErrnoException) => {
        if (e.code === "ENOENT") return [] as string[];
        throw e;
    });
    if (entries.length > 0 && !entries.includes("manifest.json")) {
        throw new Error(`${out} is not empty and holds no manifest.json: --out is one package's embedded directory and is replaced as a whole`);
    }
}

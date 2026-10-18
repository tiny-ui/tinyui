// `pnpm version:set <x.y.z>` / `pnpm version:check [expected]`: the monorepo's one version number (README「发布」).
// It lives in packages/core/package.json and is mirrored where it cannot be read from there at runtime;
// `check` is the gate publish.yml runs before anything goes to Maven Central or npm, and build.yml runs on every PR.
import { readFile, writeFile } from "node:fs/promises";
import { join } from "node:path";

const root = join(import.meta.dirname, "../../..");
const SITES: { file: string; pattern: RegExp }[] = [
    { file: "packages/core/package.json", pattern: /("version":\s*")([^"]+)(")/ },
    { file: "packages/native/package.json", pattern: /("version":\s*")([^"]+)(")/ },
    { file: "packages/cli/package.json", pattern: /("version":\s*")([^"]+)(")/ },
    { file: "packages/core/src/version.ts", pattern: /(export const VERSION = ")([^"]+)(")/ },
];
const VERSION = /^\d+\.\d+\.\d+(-[0-9A-Za-z.-]+)?$/;

const [mode, arg] = process.argv.slice(2);
if (mode === "set" && arg && VERSION.test(arg)) {
    for (const { file, pattern } of SITES) {
        const path = join(root, file);
        const text = await readFile(path, "utf8");
        if (!pattern.test(text)) throw new Error(`${file}: no version found`);
        await writeFile(path, text.replace(pattern, `$1${arg}$3`));
    }
    process.stdout.write(`${arg} written to ${SITES.length} files\n`);
} else if (mode === "check" && (arg === undefined || arg === "")) {
    await check(undefined);
} else if (mode === "check" && VERSION.test(arg)) {
    await check(arg);
} else {
    process.stderr.write("usage: node scripts/version.ts set <x.y.z> | check [expected]\n");
    process.exit(2);
}

async function check(expected: string | undefined) {
    const found = await Promise.all(SITES.map(async ({ file, pattern }) => {
        const m = pattern.exec(await readFile(join(root, file), "utf8"));
        if (!m) throw new Error(`${file}: no version found`);
        return { file, version: m[2] };
    }));
    const want = expected ?? found[0].version;
    const wrong = found.filter((f) => f.version !== want);
    if (wrong.length) {
        const reason = expected ? `the tag is ${expected}` : `packages/core says ${want}`;
        process.stderr.write(`${reason}, but:\n${wrong.map((f) => `  ${f.file} is ${f.version}`).join("\n")}\n`);
        process.exit(1);
    }
    process.stdout.write(`${want} in all ${found.length} files\n`);
}

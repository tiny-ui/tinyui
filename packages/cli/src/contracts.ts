// `tinyui routes --schema`: what each page takes and hands back, as data a host can check against (docs/build-chain.md §10).
import { createRequire } from "node:module";
import { join } from "node:path";
import { pathToFileURL } from "node:url";

/**
 * The shape of a JSON value, in the part of JSON Schema that page props need. An object lists its properties; keys it
 * does not list are allowed, so a newer caller may send more than an older page reads.
 */
export type Schema =
    | { type: "string" | "number" | "boolean" | "null" }
    | { const: string | number | boolean }
    | { enum: (string | number | boolean)[] }
    | { type: "array"; items: Schema }
    | { type: "object"; properties: Record<string, Schema>; required: string[]; additionalProperties?: Schema }
    | { type: "object"; additionalProperties: Schema }
    | { anyOf: Schema[] };

/** One page's contract: the props it mounts with and, when it exports a `Result` type, what it pops with. */
export interface PageContract {
    params: Schema;
    result?: Schema;
}

export class ContractError extends Error {}

// the pieces of TypeScript's API this file touches (`typescript/unstable/sync`, TypeScript 7)
interface TsSymbol { name: string; flags: number; getExports(): ReadonlyMap<string, TsSymbol> }
interface TsType { id: number; flags: number; value?: unknown; getTypes?(): readonly TsType[] | undefined }
interface TsChecker {
    getSymbolAtLocation(node: unknown): TsSymbol | undefined;
    getTypeOfSymbol(symbol: TsSymbol): TsType | undefined;
    getDeclaredTypeOfSymbol(symbol: TsSymbol): TsType;
    getAliasedSymbol(symbol: TsSymbol): TsSymbol;
    getSignaturesOfType(type: TsType, kind: number): readonly unknown[];
    getParameterType(signature: unknown, index: number): TsType | undefined;
    getPropertiesOfType(type: TsType): readonly TsSymbol[];
    getIndexInfosOfType(type: TsType): readonly { keyType: TsType; valueType: TsType }[];
    isArrayType(type: TsType): boolean;
    isTupleType(type: TsType): boolean;
    typeToString(type: TsType): string;
    getTypeArguments(type: TsType): readonly TsType[];
}
interface TsProject { program: { getSourceFile(file: string): unknown }; checker: TsChecker }
interface TsApi { updateSnapshot(params: { openProject: string }): { getProject(config: string): TsProject | undefined }; close(): void }
interface TsModule {
    API: new (options: { cwd: string }) => TsApi;
    TypeFlags: Record<string, number>;
    SymbolFlags: Record<string, number>;
    SignatureKind: Record<string, number>;
}

/**
 * Reads every page's props type, and the `Result` type of the pages in [results], off the project's own type
 * checker: the one in the project's `typescript`, over the project's `tsconfig.json`, so a type means here what it
 * means to `tsc`. A type JSON cannot carry (a function, `unknown`, a class) is an error naming the page and the
 * field, not a hole in the contract.
 */
export async function pageContracts(root: string, pages: Map<string, string>, results: ReadonlySet<string>): Promise<Record<string, PageContract>> {
    const ts = await loadTypeScript(root);
    const config = join(root, "tsconfig.json");
    const api = new ts.API({ cwd: root });
    try {
        const project = api.updateSnapshot({ openProject: config }).getProject(config);
        if (!project) throw new ContractError(`routes --schema: ${config} did not open as a project`);
        const convert = converter(ts, project);
        const contracts: Record<string, PageContract> = {};
        const failures: string[] = [];
        for (const [name, file] of pages) {
            try {
                const source = project.program.getSourceFile(file);
                const module = source ? project.checker.getSymbolAtLocation(source) : undefined;
                if (!module) throw new ContractError(`${file} is not part of ${config}`);
                const exports = module.getExports();
                const contract: PageContract = { params: convert(propsOf(ts, project.checker, exports.get("default")), "props") };
                if (results.has(name)) contract.result = convert(resultOf(ts, project.checker, exports.get("Result")), "Result");
                contracts[name] = contract;
            } catch (e) {
                if (!(e instanceof ContractError)) throw e;
                failures.push(`${name}: ${e.message}`);
            }
        }
        if (failures.length) throw new ContractError(failures.join("\n"));
        return contracts;
    } finally {
        api.close();
    }
}

/** The file `tinyui routes --schema` writes: pages in the order given (sorted by name), one stable layout. */
export function contractsFile(contracts: Record<string, PageContract>): string {
    return JSON.stringify(contracts, null, 2) + "\n";
}

async function loadTypeScript(root: string): Promise<TsModule> {
    let path: string;
    try {
        path = createRequire(join(root, "package.json")).resolve("typescript/unstable/sync");
    } catch {
        throw new ContractError(`routes --schema reads types with the project's own TypeScript and needs typescript 7 or newer installed in ${root} (it has no "typescript/unstable/sync")`);
    }
    return (await import(pathToFileURL(path).href)) as TsModule;
}

/** The first parameter of the page's default export; a page that takes none has no props. */
function propsOf(ts: TsModule, checker: TsChecker, symbol: TsSymbol | undefined): TsType | null {
    if (!symbol) throw new ContractError("has no default export");
    const type = checker.getTypeOfSymbol(target(ts, checker, symbol));
    const signature = type ? checker.getSignaturesOfType(type, ts.SignatureKind["Call"]!)[0] : undefined;
    if (!signature) throw new ContractError("its default export is not a function");
    if ((signature as { parameters: readonly unknown[] }).parameters.length === 0) return null;
    return checker.getParameterType(signature, 0) ?? null;
}

function resultOf(ts: TsModule, checker: TsChecker, symbol: TsSymbol | undefined): TsType {
    if (!symbol) throw new ContractError("exports no type named Result");
    return checker.getDeclaredTypeOfSymbol(target(ts, checker, symbol));
}

/** What a re-export (`export type { Result } from …`) stands for. */
function target(ts: TsModule, checker: TsChecker, symbol: TsSymbol): TsSymbol {
    return symbol.flags & ts.SymbolFlags["Alias"]! ? checker.getAliasedSymbol(symbol) : symbol;
}

function converter(ts: TsModule, project: TsProject): (type: TsType | null, at: string) => Schema {
    const F = ts.TypeFlags as Record<string, number>;
    const { checker } = project;
    const has = (type: TsType, flag: string) => (type.flags & F[flag]!) !== 0;
    const fail = (at: string, why: string): never => { throw new ContractError(`${at} ${why}`); };

    function convert(type: TsType, at: string, open: number[]): Schema {
        if (has(type, "Any") || has(type, "Unknown")) return fail(at, `is ${checker.typeToString(type)}: say what it holds, a contract cannot be checked against "anything"`);
        if (has(type, "Union") && !has(type, "Boolean")) return union(type.getTypes?.() ?? [], at, open);
        if (has(type, "Boolean")) return { type: "boolean" };
        if (has(type, "BooleanLiteral")) return { const: checker.typeToString(type) === "true" };
        if (has(type, "StringLiteral") || has(type, "NumberLiteral")) return { const: type.value as string | number };
        if (has(type, "String") || has(type, "TemplateLiteral") || has(type, "StringMapping")) return { type: "string" };
        if (has(type, "Number")) return { type: "number" };
        if (has(type, "Null")) return { type: "null" };
        if (has(type, "Undefined") || has(type, "Void")) return fail(at, "is undefined: JSON has no undefined, make the field optional instead");
        if (has(type, "Object") || has(type, "Intersection")) return object(type, at, open);
        return fail(at, `is ${checker.typeToString(type)}, which does not cross to another page as JSON`);
    }

    function union(members: readonly TsType[], at: string, open: number[]): Schema {
        const present = members.filter((m) => !has(m, "Undefined") && !has(m, "Void"));
        // `boolean` inside a wider union arrives as its two literals
        const booleans = present.filter((m) => has(m, "BooleanLiteral"));
        const rest = booleans.length === 2 ? present.filter((m) => !has(m, "BooleanLiteral")) : present;
        const schemas: Schema[] = rest.map((m) => convert(m, at, open));
        if (booleans.length === 2) schemas.push({ type: "boolean" });
        if (schemas.length === 0) return fail(at, "is undefined: JSON has no undefined, make the field optional instead");
        if (schemas.length === 1) return schemas[0]!;
        // the checker lists a union's members in an order of its own making; sorted, the file is the same on every run
        const text = (v: unknown) => JSON.stringify(v);
        if (schemas.every((s) => "const" in s)) return { enum: schemas.map((s) => (s as { const: string | number | boolean }).const).sort((a, b) => (text(a) < text(b) ? -1 : 1)) };
        return { anyOf: schemas.sort((a, b) => (text(a) < text(b) ? -1 : 1)) };
    }

    function object(type: TsType, at: string, open: number[]): Schema {
        if (open.includes(type.id)) return fail(at, `refers back to itself (${checker.typeToString(type)}): a contract is a finite shape`);
        const inside = [...open, type.id];
        if (checker.isTupleType(type)) return fail(at, "is a tuple: use an object with named fields");
        if (checker.isArrayType(type)) return { type: "array", items: convert(checker.getTypeArguments(type)[0]!, `${at}[]`, inside) };
        if (checker.getSignaturesOfType(type, ts.SignatureKind["Call"]!).length > 0) return fail(at, "is a function: functions do not cross to another page");
        const properties = checker.getPropertiesOfType(type);
        const index = checker.getIndexInfosOfType(type).find((i) => has(i.keyType, "String"));
        // keys the type does not name are held to its index signature; without one they are free
        const others = index ? convert(index.valueType, `${at}[key]`, inside) : undefined;
        if (properties.length === 0 && others) return { type: "object", additionalProperties: others };
        const shape: Record<string, Schema> = {};
        const required: string[] = [];
        for (const property of properties) {
            const propertyType = checker.getTypeOfSymbol(property);
            if (!propertyType) return fail(`${at}.${property.name}`, "has no type");
            // defined, not assigned: a field named `__proto__` is a field like any other
            Object.defineProperty(shape, property.name, { value: convert(propertyType, `${at}.${property.name}`, inside), enumerable: true, writable: true, configurable: true });
            // a field that may be undefined cannot be told from a missing one once it is JSON
            const maybeUndefined = has(propertyType, "Union") && (propertyType.getTypes?.() ?? []).some((m) => has(m, "Undefined"));
            if (!(property.flags & ts.SymbolFlags["Optional"]!) && !maybeUndefined) required.push(property.name);
        }
        return { type: "object", properties: shape, required, ...(others && { additionalProperties: others }) };
    }

    return (type, at) => (type === null ? { type: "object", properties: {}, required: [] } : convert(type, at, []));
}

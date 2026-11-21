// What a page says about itself besides its default export (docs/build-chain.md §11): read from the source, never run.
import { parseSync } from "oxc-parser";

/** How the host shows a page; `screen` is the default and is not written anywhere. */
export const PRESENTATIONS = ["screen", "sheet"] as const;
export type Presentation = (typeof PRESENTATIONS)[number];

export interface PageDeclarations {
    presentation: Presentation;
    /** The page exports a type named `Result`: what it pops with (docs/native-api.md §3). */
    result: boolean;
}

export class DeclarationError extends Error {}

type AnyNode = { type: string; start: number; end: number; [k: string]: unknown };

/**
 * Reads `export const presentation = "sheet"` and whether there is an `export type Result` / `export interface Result`.
 * The presentation goes into the manifest for a host that never runs the page first, so it has to be a string literal
 * in the page file itself: anything computed, re-exported or declared with `let` is an error rather than a guess.
 */
export function pageDeclarations(file: string, source: string): PageDeclarations {
    const { program, errors } = parseSync(file, source, { lang: file.endsWith(".tsx") ? "tsx" : "ts" });
    if (errors.length > 0) throw new DeclarationError(`${file}: ${errors[0]!.message}`);
    let presentation: Presentation = "screen";
    let result = false;
    const fail = (message: string): never => { throw new DeclarationError(`${file}: ${message}`); };
    for (const node of (program as unknown as { body: AnyNode[] }).body) {
        const misplaced = () => fail('`presentation` must be a string constant declared in the page file: export const presentation = "sheet"');
        if (node.type === "ExportAllDeclaration") {
            // `export * as presentation from …`; a bare `export *` cannot be followed without resolving the module and is left alone
            const exported = node["exported"] as AnyNode | null;
            if (exported && (exported["name"] ?? exported["value"]) === "presentation") misplaced();
            continue;
        }
        if (node.type !== "ExportNamedDeclaration") continue;
        const declaration = node["declaration"] as AnyNode | null;
        if (!declaration) {
            for (const spec of node["specifiers"] as AnyNode[]) {
                const exported = spec["exported"] as AnyNode;
                const name = (exported["name"] ?? exported["value"]) as string;
                if (name === "presentation") misplaced();
                // `export type { Result }` and `export { type Result }` name a type declared or imported above
                if (name === "Result" && (node["exportKind"] === "type" || spec["exportKind"] === "type")) result = true;
            }
            continue;
        }
        if (declaration.type === "TSTypeAliasDeclaration" || declaration.type === "TSInterfaceDeclaration") {
            if ((declaration["id"] as AnyNode)["name"] === "Result") result = true;
            continue;
        }
        if (declaration.type !== "VariableDeclaration") {
            // a function, class or enum of that name
            if ((declaration["id"] as AnyNode | null)?.["name"] === "presentation") misplaced();
            continue;
        }
        for (const d of declaration["declarations"] as AnyNode[]) {
            const id = d["id"] as AnyNode;
            // a destructuring that binds the name: `export const { presentation } = options`
            if (id.type !== "Identifier") { if (bindsName(id, "presentation")) misplaced(); continue; }
            if (id["name"] !== "presentation") continue;
            if (declaration["kind"] !== "const") fail("`presentation` must be a const");
            const value = literal(d["init"] as AnyNode | null);
            if (value === undefined) fail('`presentation` must be a string literal, e.g. export const presentation = "sheet"');
            if (!(PRESENTATIONS as readonly string[]).includes(value as string)) fail(`presentation "${value}" is not one of ${PRESENTATIONS.join(", ")}`);
            presentation = value as Presentation;
        }
    }
    return { presentation, result };
}

/** Whether the pattern [node] declares [name] somewhere inside. */
function bindsName(node: unknown, name: string): boolean {
    if (typeof node !== "object" || node === null) return false;
    const n = node as AnyNode;
    if (n.type === "Identifier") return n["name"] === name;
    // only what a pattern binds: the value of a property, an element, a rest target, the left of a default
    if (n.type === "Property") return bindsName(n["value"], name);
    if (n.type === "AssignmentPattern") return bindsName(n["left"], name);
    if (n.type === "RestElement") return bindsName(n["argument"], name);
    if (n.type === "ObjectPattern") return (n["properties"] as unknown[]).some((p) => bindsName(p, name));
    if (n.type === "ArrayPattern") return (n["elements"] as unknown[]).some((e) => bindsName(e, name));
    return false;
}

/** The string a literal holds, through `as const`, `satisfies T` and parentheses; undefined for anything else. */
function literal(node: AnyNode | null): string | undefined {
    if (!node) return undefined;
    if (node.type === "TSAsExpression" || node.type === "TSSatisfiesExpression" || node.type === "ParenthesizedExpression") return literal(node["expression"] as AnyNode);
    if (node.type === "Literal" && typeof node["value"] === "string") return node["value"];
    return undefined;
}

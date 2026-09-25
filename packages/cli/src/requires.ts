import { parseSync } from "oxc-parser";

/** What one page needs from its host beyond the built-ins, as `tinyui build` writes it into the manifest (docs/updates.md §1.3). */
export interface PageRequires {
    /** Host component types, always dotted (`ta.Icon`); built-ins follow the TinyUI version and are not listed. */
    components: string[];
    /** `host.call` names, and `session.signIn` when the page calls it. */
    capabilities: string[];
    /** `http.client` channel names; the built-in `default` is not listed. */
    channels: string[];
}

export class RequiresError extends Error {}

type AnyNode = { type: string; start: number; end: number; [k: string]: unknown };
type Kind = "core" | "native" | "component" | "object" | "other";

interface Binding {
    kind: Kind;
    /** The imported name for an import, the initializer for a constant object. */
    imported?: string | undefined;
    init?: AnyNode;
}

/**
 * Finds the host capabilities and host components a page uses, from its bundled ESM output (one self-contained
 * file per page, local imports merged). Anything it cannot resolve statically is an error rather than a guess:
 * a missed name would let a package reach a host that lacks it (docs/build-chain.md §5.1).
 */
export function analyzePage(page: string, code: string): PageRequires {
    const { program, errors } = parseSync(`${page}.js`, code, { lang: "js", sourceType: "module" });
    if (errors.length) throw new RequiresError(`${page}: cannot parse the bundled page: ${errors[0]!.message}`);
    const bindings = collectBindings(program as unknown as AnyNode);
    const trusted = constantObjects(program as unknown as AnyNode, bindings);
    const problems: string[] = [];
    const shadowed = new Set<string>();
    const components = new Set<string>();
    const capabilities = new Set<string>();
    const channels = new Set<string>();
    const problem = (node: AnyNode, message: string) => problems.push(`  ${origin(code, node.start)}: ${snippet(code, node)}\n    ${message}`);

    const useHost = (ref: AnyNode) => {
        const call = hostCall(ref, (ref as AnyNode & { parentNode?: AnyNode })["parentNode"] ?? null);
        if (!call) return problem(ref, "host may only appear as host.call(\"<name>\", …) (docs/build-chain.md §5.1)");
        const target = literal((call["arguments"] as AnyNode[])[0]);
        if (target === undefined) return problem(call, "the first argument of host.call must be a string literal (docs/build-chain.md §5.1)");
        capabilities.add(target);
    };
    const parentOf = (n: AnyNode) => (n as AnyNode & { parentNode?: AnyNode })["parentNode"] ?? null;
    /** [ref] is `http` (or `native.http`): only its members, and `client` only with a literal channel name. */
    const useHttp = (ref: AnyNode) => {
        const member = memberOf(ref, parentOf(ref));
        if (!member) return problem(ref, "http may only be used as http.<method>(…) or http.client(\"<name>\") (docs/build-chain.md §5.1)");
        if (member.name !== "client") return;
        const call = parentOf(member.node);
        if (call?.type !== "CallExpression" || call["callee"] !== member.node) return problem(member.node, "http.client may only be called, not passed around (docs/build-chain.md §5.1)");
        const name = literal((call["arguments"] as AnyNode[])[0]);
        if (name === undefined) return problem(call, "the argument of http.client must be a string literal (docs/build-chain.md §5.1)");
        // the built-in channel is every host's, nothing to check it against
        if (name !== "default") channels.add(name);
    };
    /** [ref] is `session` (or `native.session`): only its members; reaching signIn is a use of the session. */
    const useSession = (ref: AnyNode) => {
        const member = memberOf(ref, parentOf(ref));
        if (!member) return problem(ref, "session may only be used as session.<member> (docs/build-chain.md §5.1)");
        if (member.name === "signIn") capabilities.add("session.signIn");
    };
    const useH = (ref: AnyNode) => {
        const call = (ref as AnyNode & { parentNode?: AnyNode })["parentNode"];
        if (call?.type !== "CallExpression" || call["callee"] !== ref) return problem(ref, "h may only be called, not passed around (docs/build-chain.md §5.1)");
        const type = (call["arguments"] as AnyNode[])[0];
        if (!type) return problem(call, "h() needs a component type");
        for (const resolved of componentTypes(type, bindings, trusted)) {
            if (resolved === null) problem(type, "cannot tell which component this is: write the tag directly (<ta.Icon />, <Column />), a string literal, or a choice between such (docs/build-chain.md §5.1)");
            else if (resolved.includes(".")) components.add(resolved);
        }
    };

    walk(program as unknown as AnyNode, null, (node, parent) => {
        if (node.type !== "Identifier" || !isReference(node, parent)) return;
        const name = node["name"] as string;
        const list = bindings.get(name) ?? [];
        // resolution goes by name, not scope: a second declaration of a name the runtime modules export makes every use ambiguous
        if (list.length > 1 && list.some(isRuntimeImport)) {
            if (!shadowed.has(name)) problem(node, `${name} is declared more than once in this page; rename the local one so every use of the import can be followed (docs/build-chain.md §5.1)`);
            shadowed.add(name);
            return;
        }
        const binding = only(list);
        if (binding?.kind === "native" && binding.imported === "host") return useHost(node);
        if (binding?.kind === "native" && binding.imported === "http") return useHttp(node);
        if (binding?.kind === "native" && binding.imported === "session") return useSession(node);
        if (binding?.kind === "core" && binding.imported === "h") return useH(node);
        if ((binding?.kind === "native" || binding?.kind === "core") && binding.imported === "*") {
            // a namespace import is followed through its members, and only there: passed whole it could reach host or h unseen
            if (parent?.type !== "MemberExpression" || parent["object"] !== node || parent["computed"]) {
                return problem(node, `use the ${binding.kind === "native" ? "tinyui-native" : "tinyui-core"} namespace only as ${name}.<export> (docs/build-chain.md §5.1)`);
            }
            const member = (parent["property"] as AnyNode)["name"];
            if (binding.kind === "native" && member === "host") return useHost(parent);
            if (binding.kind === "native" && member === "http") return useHttp(parent);
            if (binding.kind === "native" && member === "session") return useSession(parent);
            if (binding.kind === "core" && member === "h") return useH(parent);
        }
    });
    if (problems.length) throw new RequiresError(`${page} uses its host in a way tinyui build cannot follow:\n${problems.join("\n")}`);
    return { components: [...components].sort(), capabilities: [...capabilities].sort(), channels: [...channels].sort() };
}

function isRuntimeImport(binding: Binding): boolean {
    return (binding.kind === "core" || binding.kind === "native") && binding.imported !== "default";
}

/**
 * The object constants whose members can be read at their initial value: every use is `obj.key` in a position that
 * only reads it. Positions are allowed, not forbidden: JS has too many ways to write through a member (assignment,
 * destructuring, for-in/of heads, a call through it) for a list of writes to stay complete.
 */
function constantObjects(program: AnyNode, bindings: Map<string, Binding[]>): Set<string> {
    const candidates = new Set([...bindings].filter(([, list]) => list.length === 1 && list[0]!.kind === "object").map(([name]) => name));
    const spoiled = new Set<string>();
    walk(program, null, (node, parent) => {
        if (node.type !== "Identifier" || !candidates.has(node["name"] as string) || !isReference(node, parent)) return;
        const member = parent?.type === "MemberExpression" && parent["object"] === node && !parent["computed"] ? parent : undefined;
        if (!member || !readOnly(member, (member as AnyNode & { parentNode?: AnyNode })["parentNode"] ?? null)) spoiled.add(node["name"] as string);
    });
    return new Set([...candidates].filter((name) => !spoiled.has(name)));
}

function readOnly(member: AnyNode, use: AnyNode | null): boolean {
    // parentheses decide nothing: `delete (ta.Icon)` writes as much as `delete ta.Icon`
    if (use?.type === "ParenthesizedExpression") return readOnly(use, (use as AnyNode & { parentNode?: AnyNode })["parentNode"] ?? null);
    switch (use?.type) {
        case "CallExpression":
        case "NewExpression": return use["callee"] !== member;
        case "ConditionalExpression":
        case "LogicalExpression":
        case "BinaryExpression":
        case "ReturnStatement":
        case "TemplateLiteral":
        case "ArrayExpression":
        case "SpreadElement":
        case "ExpressionStatement":
        case "AwaitExpression": return true;
        case "VariableDeclarator": return use["init"] === member;
        case "Property": return use["value"] === member && (use as AnyNode & { parentNode?: AnyNode })["parentNode"]?.type === "ObjectExpression";
        case "UnaryExpression": return use["operator"] !== "delete";
        default: return false;
    }
}

/** The type names [node] can evaluate to; `null` for one that cannot be known statically. */
function componentTypes(node: AnyNode, bindings: Map<string, Binding[]>, trusted: Set<string>): (string | null)[] {
    const text = literal(node);
    if (text !== undefined) return [text];
    if (node.type === "ConditionalExpression") return [...componentTypes(node["consequent"] as AnyNode, bindings, trusted), ...componentTypes(node["alternate"] as AnyNode, bindings, trusted)];
    if (node.type === "ParenthesizedExpression") return componentTypes(node["expression"] as AnyNode, bindings, trusted);
    if (node.type === "Identifier") {
        const binding = only(bindings.get(node["name"] as string));
        // built-ins resolve to their own undotted names; local function components render through h calls analysed on their own
        if (binding?.kind === "core") return [binding.imported ?? ""];
        if (binding?.kind === "component") return [];
        return [null];
    }
    if (node.type === "MemberExpression" && !node["computed"]) {
        const object = node["object"] as AnyNode;
        const key = (node["property"] as AnyNode)["name"] as string;
        const binding = object.type === "Identifier" ? only(bindings.get(object["name"] as string)) : undefined;
        if (binding?.kind === "core" && binding.imported === "*") return [key];
        if (binding?.kind !== "object" || !binding.init || !trusted.has(object["name"] as string)) return [null];
        for (const property of binding.init["properties"] as AnyNode[]) {
            if (property.type !== "Property" || property["computed"]) continue;
            const name = propertyName(property["key"] as AnyNode);
            if (name === key) {
                const value = literal(property["value"] as AnyNode);
                return [value ?? null];
            }
        }
        return [null];
    }
    return [null];
}

/** Every name the module declares, with what it can be; a name declared more than once is ambiguous and never resolved. */
function collectBindings(program: AnyNode): Map<string, Binding[]> {
    const bindings = new Map<string, Binding[]>();
    const add = (name: string, binding: Binding) => bindings.set(name, [...(bindings.get(name) ?? []), binding]);
    const addPattern = (pattern: AnyNode | null | undefined) => {
        if (pattern?.type === "Identifier") return add(pattern["name"] as string, { kind: "other" });
        if (pattern) walk(pattern, null, (n, parent) => { if (n.type === "Identifier" && isBindingIdentifier(n, parent)) add(n["name"] as string, { kind: "other" }); });
    };
    walk(program, null, (node) => {
        switch (node.type) {
            case "ImportDeclaration": {
                const source = (node["source"] as AnyNode)["value"];
                const kind: Kind = source === "tinyui-core" ? "core" : source === "tinyui-native" ? "native" : "other";
                for (const s of node["specifiers"] as AnyNode[]) {
                    const local = (s["local"] as AnyNode)["name"] as string;
                    const imported = s.type === "ImportSpecifier" ? propertyName(s["imported"] as AnyNode) : s.type === "ImportNamespaceSpecifier" ? "*" : "default";
                    add(local, { kind, imported });
                }
                break;
            }
            case "FunctionDeclaration":
            case "ClassDeclaration":
                if (node["id"]) add((node["id"] as AnyNode)["name"] as string, { kind: "component" });
                break;
            case "VariableDeclarator": {
                const id = node["id"] as AnyNode;
                const init = node["init"] as AnyNode | null;
                if (id.type !== "Identifier") { addPattern(id); break; }
                const kind: Kind = init && (init.type === "ArrowFunctionExpression" || init.type === "FunctionExpression") ? "component"
                    : init?.type === "ObjectExpression" ? "object" : "other";
                add(id["name"] as string, { kind, ...(init && kind === "object" && { init }) });
                break;
            }
        }
        if (node.type === "FunctionDeclaration" || node.type === "FunctionExpression" || node.type === "ArrowFunctionExpression") {
            for (const param of node["params"] as AnyNode[]) addPattern(param);
        }
        if (node.type === "CatchClause") addPattern(node["param"] as AnyNode | null);
    });
    return bindings;
}

function only(list: Binding[] | undefined): Binding | undefined {
    return list?.length === 1 ? list[0] : undefined;
}

/** The non-computed member [id] is the object of, e.g. `client` in `http.client`; undefined otherwise. */
function memberOf(id: AnyNode, parent: AnyNode | null): { node: AnyNode; name: string } | undefined {
    if (parent?.type !== "MemberExpression" || parent["object"] !== id) return undefined;
    const property = parent["property"] as AnyNode;
    const name = parent["computed"] ? literal(property) : (property["name"] as string);
    return name === undefined ? undefined : { node: parent, name };
}

/** `host.call(…)` when [id] is its `host`; otherwise undefined. */
function hostCall(id: AnyNode, parent: AnyNode | null): AnyNode | undefined {
    if (parent?.type !== "MemberExpression" || parent["object"] !== id) return undefined;
    const property = parent["property"] as AnyNode;
    const isCall = parent["computed"] ? literal(property) === "call" : property["name"] === "call";
    const call = (parent as AnyNode & { parentNode?: AnyNode })["parentNode"] as AnyNode | undefined;
    return isCall && call?.type === "CallExpression" && call["callee"] === parent ? call : undefined;
}

function literal(node: AnyNode | undefined): string | undefined {
    if (!node) return undefined;
    if (node.type === "Literal" && typeof node["value"] === "string") return node["value"] as string;
    if (node.type === "TemplateLiteral" && (node["expressions"] as unknown[]).length === 0) {
        return ((node["quasis"] as AnyNode[])[0]!["value"] as { cooked: string }).cooked;
    }
    return undefined;
}

function propertyName(key: AnyNode): string | undefined {
    return key.type === "Identifier" ? (key["name"] as string) : literal(key);
}

/** Whether an Identifier reads a variable, as opposed to naming a property, a declaration or an import. */
function isReference(node: AnyNode, parent: AnyNode | null): boolean {
    if (!parent) return true;
    switch (parent.type) {
        case "MemberExpression": return parent["object"] === node || Boolean(parent["computed"]);
        case "Property": return parent["value"] === node || Boolean(parent["computed"]);
        case "MethodDefinition":
        case "PropertyDefinition": return parent["key"] !== node || Boolean(parent["computed"]);
        case "ExportSpecifier": return parent["local"] === node;
        case "ImportSpecifier":
        case "ImportDefaultSpecifier":
        case "ImportNamespaceSpecifier":
        case "LabeledStatement":
        case "BreakStatement":
        case "ContinueStatement": return false;
        default: return !isBindingIdentifier(node, parent);
    }
}

function isBindingIdentifier(node: AnyNode, parent: AnyNode | null): boolean {
    if (!parent) return false;
    if (parent.type === "VariableDeclarator") return parent["id"] === node;
    if (parent.type === "FunctionDeclaration" || parent.type === "FunctionExpression" || parent.type === "ClassDeclaration" || parent.type === "ClassExpression") return parent["id"] === node || (parent["params"] as AnyNode[] | undefined)?.includes(node) === true;
    if (parent.type === "ArrowFunctionExpression") return (parent["params"] as AnyNode[]).includes(node);
    if (parent.type === "CatchClause") return parent["param"] === node;
    if (parent.type === "AssignmentPattern") return parent["left"] === node;
    if (parent.type === "ArrayPattern" || parent.type === "RestElement") return true;
    if (parent.type === "Property") return parent["value"] === node && (parent as AnyNode & { parentNode?: AnyNode })["parentNode"]?.type === "ObjectPattern";
    return false;
}

/** Depth-first walk that also records each node's parent as `parentNode`, which `hostCall` reads. */
function walk(node: AnyNode, parent: AnyNode | null, visit: (n: AnyNode, parent: AnyNode | null) => void) {
    Object.defineProperty(node, "parentNode", { value: parent, enumerable: false, configurable: true });
    visit(node, parent);
    for (const value of Object.values(node)) {
        if (Array.isArray(value)) {
            for (const item of value) if (isNode(item)) walk(item, node, visit);
        } else if (isNode(value)) {
            walk(value, node, visit);
        }
    }
}

function isNode(v: unknown): v is AnyNode {
    return typeof v === "object" && v !== null && typeof (v as AnyNode).type === "string" && typeof (v as AnyNode).start === "number";
}

/** The source file esbuild's `// <path>` banner names for the code at [offset]; esbuild writes one per merged module. */
function origin(code: string, offset: number): string {
    const banners = [...code.slice(0, offset).matchAll(/^\/\/ ([^\n]+\.(?:[cm]?[jt]sx?))$/gm)];
    return banners.at(-1)?.[1] ?? "<bundle>";
}

function snippet(code: string, node: AnyNode): string {
    const text = code.slice(node.start, node.end).replace(/\s+/g, " ");
    return text.length > 80 ? `${text.slice(0, 77)}...` : text;
}

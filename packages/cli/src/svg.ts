// The `.svg` loader of `tinyui build`: an SVG file becomes the icon string `Icon` takes (docs/components.md §3).

export class SvgError extends Error {}

/** `"<viewBox>|<d>"` for a filled icon, `"<viewBox>|<d>|<strokeWidth>"` for an outlined one (fill none, stroke set on the root). */
export function svgToIcon(source: string): string {
    const text = source.replace(/<!--[\s\S]*?-->/g, "").replace(/<\?xml[\s\S]*?\?>/g, "").replace(/<!DOCTYPE[\s\S]*?>/gi, "");
    const tags = [...text.matchAll(/<(\/?)([A-Za-z][\w:-]*)([^>]*?)(\/?)>/g)];
    const root = tags.find((t) => t[2] === "svg" && t[1] === "");
    if (!root) throw new SvgError("no <svg> element");
    const rootAttrs = attributes(root[3]!);
    const viewBox = rootAttrs["viewBox"] ?? (rootAttrs["width"] && rootAttrs["height"] ? `0 0 ${num(rootAttrs["width"])} ${num(rootAttrs["height"])}` : undefined);
    const box = viewBox?.trim().split(/[\s,]+/).map(Number);
    if (!box || box.length !== 4 || !box.every(Number.isFinite) || box[2]! <= 0 || box[3]! <= 0) throw new SvgError("the <svg> needs a viewBox of four numbers with a positive width and height");
    for (const bad of ["transform", "style", "opacity", "fill-opacity", "clip-path", "mask", "filter"]) {
        if (rootAttrs[bad] !== undefined) throw new SvgError(`<svg ${bad}="…">: only plain single-colour shapes can be an icon`);
    }
    const hasStroke = rootAttrs["stroke"] !== undefined && rootAttrs["stroke"] !== "none";
    // outlined: fill="none" + stroke (Lucide). bold: a filled icon whose root also sets a stroke — the shapes are filled and then
    // stroked in the same tint, which thickens a design-system glyph by a constant in viewBox units (docs/components.md §3)
    const stroked = rootAttrs["fill"] === "none" && hasStroke;
    const bold = rootAttrs["fill"] !== "none" && hasStroke;
    if (rootAttrs["fill"] === "none" && !stroked) throw new SvgError(`<svg fill="none"> without a stroke draws nothing; an outlined icon sets both`);
    if (rootAttrs["fill"] !== undefined && rootAttrs["fill"] !== "none" && rootAttrs["fill"] !== "currentColor") throw new SvgError(`<svg fill="${rootAttrs["fill"]}">: an icon takes one colour from its tint; remove the fill`);
    const strokeWidth = hasStroke ? num(rootAttrs["stroke-width"] ?? "1") : undefined;

    const parts: string[] = [];
    let depth = 0;
    for (const [, closing, name, rest] of tags) {
        const tag = name!;
        if (tag === "svg" || tag === "title" || tag === "desc") continue;
        if (closing) { if (tag === "g") depth--; continue; }
        const a = attributes(rest!);
        for (const bad of ["transform", "clip-path", "mask", "filter", "style", "opacity", "fill-opacity", "fill-rule"]) {
            if (a[bad] !== undefined && !(bad === "fill-rule" && a[bad] === "nonzero")) throw new SvgError(`<${tag} ${bad}="…">: only plain single-colour shapes can be an icon`);
        }
        if (a["fill"] !== undefined && a["fill"] !== "currentColor" && a["fill"] !== "none" && !stroked) throw new SvgError(`<${tag} fill="${a["fill"]}">: an icon takes one colour from its tint; remove the fill`);
        if (!hasStroke && a["stroke"] !== undefined && a["stroke"] !== "none") throw new SvgError(`<${tag} stroke="…"> in a filled icon; a stroke belongs on the <svg> (outlined: with fill="none"; bold: without)`);
        // outlined and bold alike: the shapes inherit the one stroke and may not change its colour, width, or opt out of it
        if (hasStroke && ((a["stroke"] !== undefined && a["stroke"] !== "currentColor") || (a["stroke-width"] !== undefined && num(a["stroke-width"]) !== strokeWidth))) {
            throw new SvgError(`<${tag}> overrides the stroke; an icon has one stroke, set on the <svg>`);
        }
        if (!stroked && a["fill"] === "none") throw new SvgError(`<${tag} fill="none"> in a filled icon; outlined icons set fill="none" and a stroke on the <svg>`);
        switch (tag) {
            case "g": if (!(rest ?? "").trimEnd().endsWith("/")) depth++; break;
            case "path": parts.push(absoluteFirstMove(required(a, "d", tag))); break;
            case "circle": parts.push(ellipse(n(a, "cx"), n(a, "cy"), n(a, "r", true), n(a, "r", true))); break;
            case "ellipse": parts.push(ellipse(n(a, "cx"), n(a, "cy"), n(a, "rx", true), n(a, "ry", true))); break;
            case "rect": parts.push(rect(n(a, "x"), n(a, "y"), n(a, "width", true), n(a, "height", true), a["rx"] ?? a["ry"], a["ry"] ?? a["rx"])); break;
            case "line": parts.push(`M${n(a, "x1")} ${n(a, "y1")}L${n(a, "x2")} ${n(a, "y2")}`); break;
            case "polyline":
            case "polygon": {
                const points = required(a, "points", tag).trim().split(/[\s,]+/).map(Number);
                if (points.length < 4 || points.length % 2 || points.some(Number.isNaN)) throw new SvgError(`<${tag}> points must be pairs of numbers`);
                const pairs = [];
                for (let i = 0; i < points.length; i += 2) pairs.push(`${points[i]} ${points[i + 1]}`);
                parts.push(`M${pairs.join("L")}${tag === "polygon" ? "Z" : ""}`);
                break;
            }
            default: throw new SvgError(`<${tag}> cannot be part of an icon (gradients, images, text, masks and references are not supported)`);
        }
    }
    if (depth !== 0) throw new SvgError("unbalanced <g>");
    if (!parts.length) throw new SvgError("no shapes");
    const boxText = box.join(" ");
    const d = `${boxText}|${parts.join("")}`;
    return strokeWidth === undefined ? d : bold ? `${d}|+${strokeWidth}` : `${d}|${strokeWidth}`;
}

function attributes(text: string): Record<string, string> {
    const out: Record<string, string> = {};
    for (const m of text.matchAll(/([\w:-]+)\s*=\s*("([^"]*)"|'([^']*)')/g)) out[m[1]!] = m[3] ?? m[4] ?? "";
    return out;
}

function required(a: Record<string, string>, key: string, tag: string): string {
    const v = a[key];
    if (v === undefined || v.trim() === "") throw new SvgError(`<${tag}> needs ${key}`);
    return v.trim();
}

function num(v: string): number {
    const x = Number.parseFloat(v);
    if (Number.isNaN(x)) throw new SvgError(`"${v}" is not a number`);
    return x;
}

function n(a: Record<string, string>, key: string, needed = false): number {
    const v = a[key];
    if (v === undefined) {
        if (needed) throw new SvgError(`missing ${key}`);
        return 0;
    }
    return num(v);
}

function ellipse(cx: number, cy: number, rx: number, ry: number): string {
    return `M${cx - rx} ${cy}a${rx} ${ry} 0 1 0 ${2 * rx} 0a${rx} ${ry} 0 1 0 ${-2 * rx} 0Z`;
}

function rect(x: number, y: number, w: number, h: number, rxText?: string, ryText?: string): string {
    const rx = Math.min(rxText === undefined ? 0 : num(rxText), w / 2);
    const ry = Math.min(ryText === undefined ? 0 : num(ryText), h / 2);
    if (rx === 0 || ry === 0) return `M${x} ${y}h${w}v${h}h${-w}Z`;
    return `M${x + rx} ${y}h${w - 2 * rx}a${rx} ${ry} 0 0 1 ${rx} ${ry}v${h - 2 * ry}a${rx} ${ry} 0 0 1 ${-rx} ${ry}h${-(w - 2 * rx)}a${rx} ${ry} 0 0 1 ${-rx} ${-ry}v${-(h - 2 * ry)}a${rx} ${ry} 0 0 1 ${rx} ${-ry}Z`;
}

/**
 * The first move of a path is absolute even when written as `m` (SVG 1.1 §8.3.3), but once the shapes are joined into one `d`
 * it would become relative to the previous shape's end and the glyph drifts off canvas. Rewrite it as `M`, keeping the pairs
 * that follow the move as the relative linetos they implicitly are.
 */
function absoluteFirstMove(d: string): string {
    // the move's arguments run until the next command letter; a number may carry an exponent (`2e3`), so scan by number
    const m = /^m((?:[\s,]|[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?)*)/.exec(d);
    if (!m) return d;
    const numbers = m[1]!.match(/[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?/g) ?? [];
    if (numbers.length < 2) return d;
    const rest = numbers.slice(2);
    return `M${numbers[0]} ${numbers[1]}${rest.length ? ` l${rest.join(" ")}` : ""}${d.slice(m[0].length)}`;
}

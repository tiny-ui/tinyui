import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { SvgError, svgToIcon } from "../src/svg.ts";

describe("svg loader", () => {
    it("turns a filled Material Symbols file into viewBox|d", () => {
        const svg = `<svg xmlns="http://www.w3.org/2000/svg" height="24" viewBox="0 -960 960 960" width="24"><path d="M480-80 L80-480Z"/></svg>`;
        assert.equal(svgToIcon(svg), "0 -960 960 960|M480-80 L80-480Z");
    });

    it("merges shapes into one path and keeps the stroke width of an outlined icon", () => {
        const svg = `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><rect x="2" y="2" width="4" height="4" rx="1"/><polyline points="1,1 2,2"/></svg>`;
        const icon = svgToIcon(svg);
        const [box, d, width] = icon.split("|");
        assert.equal(box, "0 0 24 24");
        assert.equal(width, "2");
        assert.match(d!, /^M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0Z/);
        assert.match(d!, /M12 8L12 12/);
        assert.match(d!, /M1 1L2 2$/);
    });

    it("refuses what one tint cannot draw", () => {
        const wrap = (inner: string) => `<svg viewBox="0 0 24 24">${inner}</svg>`;
        assert.throws(() => svgToIcon(wrap(`<path fill="#ff0000" d="M0 0Z"/>`)), SvgError);
        assert.throws(() => svgToIcon(wrap(`<linearGradient id="g"/><path d="M0 0Z"/>`)), /cannot be part of an icon/);
        assert.throws(() => svgToIcon(wrap(`<path transform="scale(2)" d="M0 0Z"/>`)), /only plain single-colour shapes/);
        assert.throws(() => svgToIcon(`<svg><path d="M0 0Z"/></svg>`), /viewBox/);
        assert.throws(() => svgToIcon(`<svg viewBox="0 0 24 nope"><path d="M0 0Z"/></svg>`), /viewBox/);
        assert.throws(() => svgToIcon(`<svg viewBox="0 0 0 24"><path d="M0 0Z"/></svg>`), /positive width/);
        assert.throws(() => svgToIcon(wrap("")), /no shapes/);
        assert.throws(() => svgToIcon(`<svg viewBox="0 0 24 24" fill="none"><path d="M0 0Z"/></svg>`), /without a stroke/);
        assert.throws(() => svgToIcon(`<svg viewBox="0 0 24 24" stroke="currentColor"><line x1="0" y1="0" x2="1" y2="1"/></svg>`), /either filled or outlined/);
        assert.throws(() => svgToIcon(wrap(`<path stroke="currentColor" d="M0 0Z"/>`)), /stroke="…"> in a filled icon/);
        const outlined = (inner: string) => `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">${inner}</svg>`;
        assert.throws(() => svgToIcon(outlined(`<path stroke="none" d="M0 0Z"/>`)), /overrides the stroke/);
        assert.throws(() => svgToIcon(outlined(`<path stroke-width="3" d="M0 0Z"/>`)), /overrides the stroke/);
        assert.doesNotThrow(() => svgToIcon(outlined(`<path stroke-width="2" d="M0 0Z"/>`)));
        assert.throws(() => svgToIcon(`<svg viewBox="0 0 24 24" transform="scale(2)"><path d="M0 0Z"/></svg>`), /only plain single-colour shapes/);
    });
});

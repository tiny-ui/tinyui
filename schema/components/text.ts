import { color, defineComponent, enumOf, field, number, sp, string, TEXT_STYLES } from "tinyui-cli/schema";

export default defineComponent("Text", {
    props: {
        text: string({ required: true, doc: "`**bold**` spans render bold; with onLinkClick set, `[label](id)` spans render as links. Flat, no nesting; stray marks stay literal" }),
        style: enumOf(TEXT_STYLES, { doc: "Material 3 text style; fontSize / fontWeight override its fields" }),
        color: color(),
        fontSize: sp(),
        fontWeight: enumOf(["normal", "medium", "bold"]),
        maxLines: number({ doc: "0 = unlimited", default: 0 }),
        align: enumOf(["start", "center", "end"], { default: "start" }),
    },
    events: {
        onClick: {},
        onLinkClick: { id: field.string("the `(id)` of the tapped link") },
    },
});

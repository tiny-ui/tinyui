import { boolean, color, defineComponent, enumOf, field, number, sp, string, TEXT_STYLES } from "tinyui-cli/schema";

export default defineComponent("Text", {
    props: {
        text: string({ required: true, doc: "shown exactly as given unless `markup` is set" }),
        markup: boolean({ default: false, doc: "true reads the inline marks in `text`: `**bold**` spans render bold, `[label](id)` spans render as links that report onLinkClick. Flat, no nesting; stray marks stay literal. Leave it off for text that is data (a name, a masked address), where `**` is content" }),
        style: enumOf(TEXT_STYLES, { doc: "Material 3 text style; fontSize / fontWeight override its fields" }),
        color: color(),
        fontSize: sp(),
        fontWeight: enumOf(["normal", "medium", "bold"]),
        maxLines: number({ doc: "0 = unlimited", default: 0 }),
        align: enumOf(["start", "center", "end"], { default: "start" }),
    },
    events: {
        onClick: {},
        onLinkClick: { id: field.string("the `(id)` of the tapped link; links exist only with `markup` set") },
    },
});

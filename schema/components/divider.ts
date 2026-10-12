import { color, defineComponent, dp } from "tinyui-cli/schema";

export default defineComponent("Divider", {
    doc: "M3 horizontal divider: a full-width line. Inset it with paddingHorizontal.",
    props: {
        thickness: dp({ default: 1 }),
        color: color({ default: "outlineVariant" }),
    },
});

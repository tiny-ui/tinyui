import { boolean, defineComponent, dp, enumOf } from "tinyui-cli/schema";

export default defineComponent("Column", {
    doc: "Vertical stack.",
    props: {
        gap: dp({ default: 0, doc: "space between children" }),
        align: enumOf(["start", "center", "end", "stretch"], { default: "start", doc: "cross axis; stretch gives every child without its own width the width of the widest one" }),
        justify: enumOf(["start", "center", "end", "spaceBetween"], { default: "start", doc: "main axis" }),
        scroll: boolean({ default: false, doc: "scrolls along the main axis; padding stays outside the scrolling content" }),
    },
    events: { onClick: {} },
    children: true,
});

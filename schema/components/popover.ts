import { boolean, defineComponent, enumOf } from "tinyui-cli/schema";

export default defineComponent("Popover", {
    doc: "Card floating next to its parent, which is the anchor; children are its content. Shown while `open`; it never takes focus, so the keyboard stays up. `onDismiss` reports a tap outside, the page decides whether to close.",
    props: {
        open: boolean({ required: true }),
        placement: enumOf(["above", "below"], { default: "below", doc: "preferred side of the anchor; flips when that side has no room" }),
        matchAnchorWidth: boolean({ default: false, doc: "as wide as the anchor instead of its content" }),
    },
    events: { onDismiss: {} },
    children: true,
    // floats next to the parent, not laid out in it
    layout: false,
});

import { boolean, defineComponent, field } from "tinyui-cli/schema";

export default defineComponent("Menu", {
    doc: "M3 dropdown menu next to its parent, which is the anchor; the items are its `Option` children. Shown while `open`; a pick (`onSelect`) or a tap outside (`onDismiss`) has already closed it when reported, and it stays closed until `open` goes false and true again.",
    props: {
        open: boolean({ required: true }),
    },
    events: { onSelect: { value: field.string() }, onDismiss: {} },
    children: true,
    // floats next to the parent, not laid out in it
    layout: false,
});

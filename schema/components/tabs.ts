import { defineComponent, field, string } from "tinyui-cli/schema";

export default defineComponent("Tabs", {
    doc: "M3 primary tab row; the tabs are its `Option` children, sharing the width equally. The selection is the page's: `onSelect` reports the tap, `value` shows it.",
    props: {
        value: string({ doc: "value of the selected Option; unset or unmatched selects none (not `selected`, the common selectable-container prop)" }),
    },
    events: { onSelect: { value: field.string() } },
    children: true,
});

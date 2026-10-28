import { boolean, defineComponent, field, string } from "tinyui-cli/schema";

export default defineComponent("Select", {
    doc: "M3 exposed dropdown; the choices are its `Option` children. The selection is the page's: `onSelect` reports the pick, `value` shows it.",
    props: {
        value: string({ doc: "value of the chosen Option; unset or unmatched shows the placeholder (not `selected`, the common selectable-container prop)" }),
        placeholder: string({ default: "" }),
        label: string(),
        enabled: boolean({ default: true }),
    },
    events: { onSelect: { value: field.string() } },
    children: true,
});

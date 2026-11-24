import { boolean, defineComponent, enumOf, field, string } from "tinyui-cli/schema";

export default defineComponent("Select", {
    doc: "M3 exposed dropdown; the choices are its `Option` children. The selection is the page's: `onSelect` reports the pick, `value` shows it. With `onClick` a tap is the page's too: the menu does not open, the page shows the choices its own way (a sheet) and sets `value`.",
    props: {
        value: string({ doc: "value of the chosen Option; unset or unmatched shows the placeholder (not `selected`, the common selectable-container prop)" }),
        placeholder: string({ default: "" }),
        label: string(),
        enabled: boolean({ default: true }),
        size: enumOf(["small"], { doc: "the host's smaller field (TinyUIDefaults.smallTextField: a search field shorter than the form fields); unset, the host's field (TinyUIDefaults.textField). Height and padding go together, the page writes no number" }),
    },
    events: { onSelect: { value: field.string() }, onClick: {} },
    children: true,
});

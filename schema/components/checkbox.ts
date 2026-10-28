import { boolean, defineComponent } from "tinyui-cli/schema";

export default defineComponent("Checkbox", {
    doc: "Same model as RadioButton: `onClick` reports the tap, `checked` is a plain prop the page owns.",
    props: {
        checked: boolean({ required: true }),
        enabled: boolean({ default: true }),
    },
    events: { onClick: {} },
});

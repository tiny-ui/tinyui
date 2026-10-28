import { boolean, defineComponent, enumOf, field, number } from "tinyui-cli/schema";

export default defineComponent("PinField", {
    doc: "A row of digit cells over one input; text, focus and the IME stay on the Kotlin side like TextField (docs/adr-004 §3.3).",
    props: {
        length: number({ default: 6, doc: "number of cells; input stops there" }),
        secure: boolean({ default: false, doc: "shows a dot instead of each digit" }),
        enabled: boolean({ default: true }),
        error: boolean({ default: false, doc: "error colour on the cell outlines" }),
        autofill: enumOf(["none", "oneTimeCode"], { default: "none", doc: "oneTimeCode lets the system offer a code it just received by SMS; none keeps it from offering anything, saved passwords included" }),
    },
    events: {
        onChange: { text: field.string() },
        onComplete: { text: field.string("when the last cell is filled") },
    },
    commands: {
        setText: { text: field.string() },
        focus: {},
        clear: {},
    },
});

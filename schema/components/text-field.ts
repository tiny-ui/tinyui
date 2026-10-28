import { boolean, defineComponent, enumOf, field, icon, number, string } from "tinyui-cli/schema";

export default defineComponent("TextField", {
    doc: "Text and cursor live on the Kotlin side; JS gets notified and issues commands (docs/adr-004 §3.3).",
    props: {
        initialText: string({ initial: true, default: "" }),
        placeholder: string({ default: "" }),
        singleLine: boolean({ default: true }),
        keyboard: enumOf(["text", "number", "email", "phone", "password"], { default: "text" }),
        secure: boolean({ doc: "masks the text; unset follows keyboard=\"password\", so a visibility toggle just flips it" }),
        error: boolean({ default: false, doc: "error colours on the outline, label and supportingText" }),
        supportingText: string({ doc: "one line under the field: a hint, or the error message while error is true" }),
        prefixText: string({ doc: "fixed text before the input, such as a calling code" }),
        trailingIcon: icon({ doc: "icon at the end, such as show / hide or clear; tappable when onTrailingClick is set" }),
        imeAction: enumOf(["done", "next"], { default: "done", doc: "next moves focus to the following field" }),
        maxLength: number({ default: 0, doc: "0 = unlimited; longer input is cut, not rejected" }),
    },
    events: {
        onChange: { text: field.string() },
        onCommit: { text: field.string("on IME action or focus loss") },
        onFocusChange: { focused: field.boolean() },
        onTrailingClick: {},
        onPrefixClick: {},
    },
    commands: {
        setText: { text: field.string() },
        focus: {},
        blur: {},
    },
});

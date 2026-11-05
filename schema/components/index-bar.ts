import { defineComponent, field, string } from "tinyui-cli/schema";

export default defineComponent("IndexBar", {
    doc: "A vertical strip of short labels beside a long list (the A–Z of a contact list); the labels are its `Option` children. A tap or a slide over it reports each label the finger reaches; scrolling the list is the page's.",
    props: {
        value: string({ doc: "value of the highlighted Option, usually the section the list is at; unset or unmatched highlights none" }),
    },
    events: { onSelect: { value: field.string("the Option the finger reached: once on touch down, then each time it moves onto another") } },
    children: true,
});

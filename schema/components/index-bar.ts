import { defineComponent, string } from "tinyui-cli/schema";

export default defineComponent("IndexBar", {
    doc: "The strip of short labels beside a long list (the A–Z of a contact list). Put it in a LazyColumn, as a direct child: it floats over the list's end edge and is not one of its rows. A tap or a slide over it takes the list to the SectionHeader whose `index` is that label; the highlighted label is the group the list is at. The labels are its `Option` children; without any, the `index` of each SectionHeader in order.",
    props: {
        label: string({ doc: "what a screen reader calls it" }),
    },
    children: true,
    // floats over the list it is in, not laid out as one of its rows
    layout: false,
});

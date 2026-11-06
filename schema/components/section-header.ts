import { boolean, defineComponent, enumOf, string } from "tinyui-cli/schema";

export default defineComponent("SectionHeader", {
    doc: "The heading of a group of rows in a LazyColumn: a Box the list keeps at its top while the group scrolls under it. Anywhere else, or with `sticky` off, it is a plain Box.",
    props: {
        index: string({ doc: "this group's label on the list's IndexBar: the value of one of its Options. Unset keeps the group out of the index" }),
        sticky: boolean({ default: true, doc: "stays at the top of the list while its rows scroll under it; give it an opaque background" }),
        align: enumOf(["topStart", "topCenter", "topEnd", "centerStart", "center", "centerEnd", "bottomStart", "bottomCenter", "bottomEnd"], { default: "topStart" }),
    },
    events: { onClick: {} },
    children: true,
});

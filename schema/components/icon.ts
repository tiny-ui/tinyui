import { color, defineComponent, dp, icon, string } from "tinyui-cli/schema";

export default defineComponent("Icon", {
    doc: "A single-colour icon whose data comes with the package (`import bolt from \"….svg\"`); tinted, never ships an icon set.",
    props: {
        icon: icon({ required: true }),
        size: dp({ default: 24 }),
        tint: color({ doc: "defaults to the content colour" }),
        label: string({ doc: "what a screen reader says; leave unset for a decorative icon next to its text" }),
    },
    events: { onClick: {} },
});

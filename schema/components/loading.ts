import { color, defineComponent, dp } from "tinyui-cli/schema";

export default defineComponent("Loading", {
    doc: "Indeterminate M3 loading indicator.",
    props: {
        size: dp({ default: 24 }),
        color: color({ default: "primary" }),
    },
});

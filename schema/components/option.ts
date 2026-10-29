import { defineComponent, string } from "tinyui-cli/schema";

export default defineComponent("Option", {
    doc: "One choice of its parent Select or Menu, which reads these props; renders nothing anywhere else.",
    props: {
        value: string({ required: true }),
        text: string({ required: true }),
    },
    layout: false,
});

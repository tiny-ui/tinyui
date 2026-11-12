import { boolean, defineComponent } from "tinyui-cli/schema";

export default defineComponent("BottomSheet", {
    doc: "The node behind `BottomSheet`: an M3 modal bottom sheet over the page, shown while `open`. Pages use the `BottomSheet` component of tinyui-core, which builds the content anew at every opening.",
    props: {
        open: boolean({ required: true, doc: "true slides it in, false animates it out" }),
    },
    events: { onDismiss: {} },
    children: true,
    // floats over the page, not laid out in its parent
    layout: false,
});

import { boolean, defineComponent } from "tinyui-cli/schema";

export default defineComponent("BottomSheet", {
    doc: "M3 modal bottom sheet over the page; children are its content. Shown while `open`; set it to false in `onDismiss`, which fires after the user swiped, tapped the scrim or pressed back.",
    props: {
        open: boolean({ required: true, doc: "true slides it in, false animates it out" }),
    },
    events: { onDismiss: {} },
    children: true,
    // floats over the page, not laid out in its parent
    layout: false,
});

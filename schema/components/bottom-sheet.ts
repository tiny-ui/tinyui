import { boolean, defineComponent, number } from "tinyui-cli/schema";

export default defineComponent("BottomSheet", {
    doc: "The node behind `BottomSheet`: an M3 modal bottom sheet over the page, shown while `open`. Pages use the `BottomSheet` component of tinyui-core, which builds the content anew at every opening.",
    props: {
        open: boolean({ required: true, doc: "true slides it in, false animates it out" }),
        maxHeightFraction: number({ doc: "cap on the sheet's height as a share of the window's, in (0, 1]: content that fills stops there. A cap, not a height: a shorter sheet keeps its content's height. Unset, the host's (`TinyUIDefaults.bottomSheet`), else none (M3: up to the top of the screen)" }),
    },
    events: { onDismiss: {} },
    children: true,
    // floats over the page, not laid out in its parent
    layout: false,
});

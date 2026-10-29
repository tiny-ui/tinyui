import { boolean, defineComponent } from "tinyui-cli/schema";

export default defineComponent("BackHandler", {
    doc: "While `enabled`, system back and the `TopAppBar` back button on this page fire `onBack` instead of closing the page; draws nothing. Declared up front because the host cannot ask JS at the moment of the gesture. Of several enabled ones, the last mounted wins.",
    props: {
        enabled: boolean({ default: true }),
    },
    events: { onBack: {} },
    // draws nothing
    layout: false,
});

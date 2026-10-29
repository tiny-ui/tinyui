import { boolean, defineComponent } from "tinyui-cli/schema";

export default defineComponent("BackHandler", {
    doc: "While `enabled`, system back (the `TopAppBar` back button sends one too) fires `onBack` instead of reaching the host's navigation; draws nothing. Declared up front because the host cannot ask JS at the moment of the gesture. Of several enabled ones, the last composed wins. Inert in a host without a back dispatcher.",
    props: {
        enabled: boolean({ default: true }),
    },
    events: { onBack: {} },
    // draws nothing
    layout: false,
});

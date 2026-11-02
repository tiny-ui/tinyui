import { defineComponent, dp, icon, string } from "tinyui-cli/schema";

export default defineComponent("TopAppBar", {
    doc: "M3 top app bar at the top of the page; children go in its trailing actions slot. It pads itself below the status bar; `height` is the bar's own height, the status bar not included. Its back button sends a system back: an enabled `BackHandler` or the host's navigation takes it; in a host without a back dispatcher it does nothing.",
    props: {
        title: string(),
        backIcon: icon({ doc: "shows a back button with this icon; unset, no back button (the first page of a flow)" }),
        backLabel: string({ doc: "what a screen reader says for the back button" }),
        backIconSize: dp({ doc: "size of the back icon; unset, the icon's own size (24dp for a packaged icon). The button stays a 48dp touch target" }),
    },
    children: true,
});

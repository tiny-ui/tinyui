import { defineComponent, icon, string } from "tinyui-cli/schema";

export default defineComponent("TopAppBar", {
    doc: "M3 top app bar at the top of the page; children go in its trailing actions slot. It pads itself below the status bar; `height` is the bar's own height, the status bar not included. Its back button sends a system back: an enabled `BackHandler` or the host's navigation takes it; without a back dispatcher in the host it closes the page.",
    props: {
        title: string(),
        backIcon: icon({ doc: "shows a back button with this icon; unset, no back button (the first page of a flow)" }),
        backLabel: string({ doc: "what a screen reader says for the back button" }),
    },
    children: true,
});

import { defineComponent, icon, string } from "tinyui-cli/schema";

export default defineComponent("TopAppBar", {
    doc: "M3 top app bar at the top of the page; children go in its trailing actions slot. It pads itself below the status bar; `height` is the bar's own height, the status bar not included. Its back button does what system back does on this page: the last enabled `BackHandler` gets it, otherwise the page closes.",
    props: {
        title: string(),
        backIcon: icon({ doc: "shows a back button with this icon; unset, no back button (the first page of a flow)" }),
        backLabel: string({ doc: "what a screen reader says for the back button" }),
    },
    children: true,
});

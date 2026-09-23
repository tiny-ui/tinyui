import { Column, h, Text } from "tinyui-core";
import { ta } from "../generated/components.ts";
import { billing, checkout } from "../host/index.ts";

// a hand-written h() choosing between two host components
function Status(props: { busy: boolean }) {
    return h(props.busy ? ta.Loading : "ta.Icon", null);
}

function PlanCard(props: { title: string }) {
    return <Column><ta.Badge /><Text text={props.title} /></Column>;
}

export default function Shop({ busy }: { busy: boolean }) {
    void billing.prices();
    return (
        <Column>
            <ta.Icon />
            <Status busy={busy} />
            <PlanCard title="annual" />
            <Text text="buy" onClick={() => checkout.start("annual")} />
        </Column>
    );
}

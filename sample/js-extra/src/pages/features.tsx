import { BottomSheet, Box, Button, Column, Divider, Icon, Loading, observable, RadioButton, Row, Show, signal, Text } from "tinyui-core";
import { analytics, cached, clipboard, http, i18n, linking, session, storage, ui } from "tinyui-native";
import bolt from "@material-symbols/svg-400/outlined/bolt.svg";
import heart from "../assets/heart.svg";

type Plan = "annual" | "monthly";

/** One page per framework capability of ADR-007, with no host capability of its own (docs/native-api.md). */
export default function Features() {
    const t = i18n.t;
    const [count, setCount] = signal(storage.get<number>("count") ?? 0);
    const state = observable({ plan: "annual" as Plan });
    const [sheet, setSheet] = signal(false);
    const openedAt = Date.now();
    const time = () => i18n.formatDate(openedAt, "yyyy-MM-dd HH:mm:ss");
    const [zen] = cached("zen", () => http.get<string>("https://api.github.com/zen").then((r) => r.body));

    function tap() {
        setCount(count() + 1);
        storage.set("count", count());
        analytics.track("features_tap", { count: count() });
    }

    async function toast() {
        if ((await ui.toast(t("features.toast.message"), { action: t("features.toast.undo") })) === "action") setCount(Math.max(0, count() - 1));
    }

    async function copy() {
        await clipboard.setText(time());
        void ui.toast(t("features.copied"));
    }

    async function clear() {
        if (!(await ui.confirm({ message: t("features.confirm.message") }))) return;
        setCount(0);
        storage.remove("count");
    }

    return (
        <Column width="fill" height="fill" scroll padding={16} gap={12}>
            <Row align="center" gap={8}>
                <Icon icon={bolt} tint="primary" />
                <Text text={t("features.title")} style="titleLarge" />
            </Row>
            <Text text={t("features.locale", { locale: i18n.locale() })} style="bodyMedium" />
            <Row align="center" gap={8}>
                <Icon icon={heart} tint="error" size={20} />
                <Text text={t("features.count", { count: count() })} />
            </Row>
            <Row gap={8}>
                <Button text={t("features.tap")} onClick={tap} />
                <Button text={t("features.toast")} variant="outlined" onClick={() => void toast()} />
            </Row>
            <Row gap={8}>
                <Button text={t("features.confirm")} variant="outlined" onClick={() => void clear()} />
                <Button text={t("features.open")} variant="text" onClick={() => void linking.openUrl("https://tinyui.app")} />
            </Row>
            <PlanRow label={t("features.plan.annual")} selected={state.plan === "annual"} onClick={() => (state.plan = "annual")} />
            <PlanRow label={t("features.plan.monthly")} selected={state.plan === "monthly"} onClick={() => (state.plan = "monthly")} />
            <Row align="center" gap={8}>
                <Text text={session.state().loggedIn ? t("features.session.in", { user: session.state().userId ?? "" }) : t("features.session.out")} />
                <Show when={!session.state().loggedIn}>{() => <Button text={t("features.signin")} variant="text" onClick={() => void session.signIn("features")} />}</Show>
            </Row>
            <Divider />
            <Text text={t("features.time", { zone: i18n.timeZone(), time: time() })} />
            <Row gap={8}>
                <Button text={t("features.copy")} variant="outlined" onClick={() => void copy()} />
                <Button text={t("features.sheet")} variant="outlined" onClick={() => setSheet(true)} />
            </Row>
            <BottomSheet open={sheet()} onDismiss={() => setSheet(false)}>
                <Column width="fill" paddingHorizontal={24} paddingVertical={16} gap={12}>
                    <Text text={t("features.sheet.title")} style="titleMedium" />
                    <Text text={t("features.sheet.body")} style="bodyMedium" />
                    <Button text={t("features.sheet.close")} onClick={() => setSheet(false)} />
                </Column>
            </BottomSheet>
            <Show when={zen() !== undefined} fallback={() => <Box padding={8}><Loading /></Box>}>
                {() => <Text text={t("features.zen", { text: zen() ?? "" })} style="bodySmall" color="onSurfaceVariant" />}
            </Show>
        </Column>
    );
}

function PlanRow(p: { label: string; selected: boolean; onClick: () => void }) {
    return (
        <Row width="fill" align="center" gap={8} padding={8} cornerRadius={12} role="radio" selected={p.selected}
            background={p.selected ? "secondaryContainer" : "surfaceContainer"} onClick={() => p.onClick()}>
            <RadioButton selected={p.selected} />
            <Text text={p.label} />
        </Row>
    );
}

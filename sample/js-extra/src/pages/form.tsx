import { BackHandler, Box, Button, Checkbox, CodeField, Column, Icon, Menu, Option, Popover, ref, Row, Select, signal, Text, TextField, TopAppBar, type CodeFieldCommands } from "tinyui-core";
import { crypto, navigation, ui } from "tinyui-native";
import visibility from "@material-symbols/svg-400/outlined/visibility.svg";
import visibilityOff from "@material-symbols/svg-400/outlined/visibility_off.svg";
import check from "@material-symbols/svg-400/outlined/check.svg";
import chevronLeft from "@material-symbols/svg-400/outlined/chevron_left.svg";
import language from "@material-symbols/svg-400/outlined/language.svg";
import arrowDown from "@material-symbols/svg-400/outlined/keyboard_arrow_down.svg";

// a throwaway demo key; a real page fetches its server's key over its channel
const DEMO_PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAu1h+fpBuEFQRG4A5eKK2zLAc9a2+gKiLeKfbQxYAe8/ty4avmzchM9s5C7wbCGpvPu6ptU4IXNKWv9tjDAZdAW79uYhaLBIXARR4xUBbUTBicFIVCUTEoByaTDuqXnavkg4OoE5iSGqqq0klRMBtloKQ42V70epl89PTwlYEYp6MHGLAwNx2G95SufWoXca6AWZmxjWwIvImwpIN51mMjTXv8JLv5apwf6SitQwAwp4xnibb4Rlt4SpgbKFDBylCkfxuNXJ4TBExYC17/90+j6w8FMYs/IFfBPVS0MyAnV/jd5IcLmPMGBDHLl/ODhvn4lqO1XJ0fVikqR9Rj2skzwIDAQAB";

/** The form pieces a sign-up flow needs: top bar with back and a menu, back guard, checkbox, field adornments, rule popover, code cells, dropdown, inline links, RSA. */
export default function Form() {
    const [email, setEmail] = signal("");
    const [password, setPassword] = signal("");
    const [shown, setShown] = signal(false);
    const [focused, setFocused] = signal(false);
    const [agreed, setAgreed] = signal(false);
    const [voice, setVoice] = signal("");
    const [pinError, setPinError] = signal(false);
    const [lang, setLang] = signal("English");
    const [menuOpen, setMenuOpen] = signal(false);
    const [guarded, setGuarded] = signal(false);
    const pin = ref<CodeFieldCommands>();
    async function encrypt() {
        // the plain password never leaves this page: only the ciphertext would go on to the next step
        const cipher = await crypto.rsaEncrypt(password(), { publicKey: DEMO_PUBLIC_KEY });
        void ui.toast(`${cipher.length} base64 chars: ${cipher.slice(0, 16)}…`);
    }
    const emailError = () => email() !== "" && !email().includes("@");

    async function leave() {
        if (await ui.confirm({ message: "Leave this page?", confirm: "Leave", cancel: "Stay" })) navigation.pop();
    }

    return (
        <Column width="fill" height="fill">
            {/* back goes where system back goes: the guard below when it is on, otherwise the page closes */}
            <TopAppBar backIcon={chevronLeft} backLabel="Back" height={48}>
                <Button variant="text" text="Help" onClick={() => void ui.toast("open help")} />
                <Row role="button" onClick={() => setMenuOpen(true)} align="center" gap={4} paddingHorizontal={8}>
                    <Icon icon={language} size={20} />
                    <Text text={lang()} style="labelLarge" />
                    <Icon icon={arrowDown} size={20} />
                    <Menu open={menuOpen()} onDismiss={() => setMenuOpen(false)} onSelect={(e) => { setMenuOpen(false); setLang(e.value); }}>
                        <Option value="English" text="English" />
                        <Option value="中文" text="中文" />
                    </Menu>
                </Row>
            </TopAppBar>
            <BackHandler enabled={guarded()} onBack={() => void leave()} />
            <Column width="fill" weight={1} padding={16} gap={12} scroll>
                <Row align="center" gap={4} role="checkbox" selected={guarded()} onClick={() => setGuarded(!guarded())}>
                    <Checkbox checked={guarded()} />
                    <Text text="Ask before leaving" />
                </Row>
                <TextField
                    width="fill" placeholder="Email" keyboard="email" imeAction="next"
                    error={emailError()} supportingText={emailError() ? "Not an email address" : ""}
                    onChange={(e) => setEmail(e.text)}
                />
                <Box width="fill">
                    <TextField
                        width="fill" placeholder="Password" keyboard="password" secure={!shown()}
                        trailingIcon={shown() ? visibilityOff : visibility} onTrailingClick={() => setShown(!shown())}
                        onFocusChange={(e) => setFocused(e.focused)} onChange={(e) => setPassword(e.text)}
                    />
                    {/* the rules float over whatever is above the field while it has focus; typing ticks them off */}
                    <Popover open={focused()} placement="above" matchAnchorWidth>
                        <Column width="fill" padding={16} gap={8}>
                            <Text text="Password must have" style="titleSmall" />
                            <Rule ok={password().length >= 8} text="At least 8 characters" />
                            <Rule ok={/[A-Za-z]/.test(password()) && /\d/.test(password())} text="Letters and digits" />
                        </Column>
                    </Popover>
                </Box>
                <Button text="Encrypt password" variant="outlined" onClick={() => void encrypt()} />
                <TextField width="fill" placeholder="Phone" keyboard="phone" prefixText="+86" onPrefixClick={() => void ui.toast("pick a calling code")} />
                <Row align="center" gap={4} role="checkbox" selected={agreed()} onClick={() => setAgreed(!agreed())}>
                    <Checkbox checked={agreed()} />
                    <Text weight={1} text="I agree to the [Privacy Policy](privacy) and the [Terms of Service](terms)" onLinkClick={(e) => void ui.toast(`open ${e.id}`)} />
                </Row>
                <Select width="fill" label="Voice language" placeholder="Choose" value={voice()} onSelect={(e) => setVoice(e.value)}>
                    <Option value="en" text="English" />
                    <Option value="zh" text="中文" />
                </Select>
                <Text text="SMS code" style="titleSmall" />
                <CodeField width="fill" autofill="oneTimeCode" onComplete={(e) => void ui.toast(`code ${e.text}`)} />
                <Text text="Transaction PIN (1234 is wrong)" style="titleSmall" />
                <CodeField
                    width="fill" secure error={pinError()} ref={pin}
                    onChange={(e) => { if (e.text) setPinError(false) }}
                    onComplete={(e) => { if (e.text.startsWith("1234")) { setPinError(true); pin.cmd("clear", {}); } else void ui.toast("PIN set") }}
                />
            </Column>
        </Column>
    );
}

// read p.ok inside the JSX: the function body runs once, only attribute expressions follow changes
function Rule(p: { ok: boolean; text: string }) {
    return (
        <Row align="center" gap={8}>
            <Icon icon={check} tint={p.ok ? "primary" : "onSurfaceVariant"} size={18} />
            <Text text={p.text} style="bodyMedium" color={p.ok ? "primary" : "onSurfaceVariant"} />
        </Row>
    );
}

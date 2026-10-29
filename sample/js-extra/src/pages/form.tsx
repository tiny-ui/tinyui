import { Box, Button, Checkbox, CodeField, Column, Icon, Option, Popover, ref, Row, Select, signal, Text, TextField, type CodeFieldCommands } from "tinyui-core";
import { crypto, ui } from "tinyui-native";
import visibility from "@material-symbols/svg-400/outlined/visibility.svg";
import visibilityOff from "@material-symbols/svg-400/outlined/visibility_off.svg";
import check from "@material-symbols/svg-400/outlined/check.svg";

// a throwaway demo key; a real page fetches its server's key over its channel
const DEMO_PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAu1h+fpBuEFQRG4A5eKK2zLAc9a2+gKiLeKfbQxYAe8/ty4avmzchM9s5C7wbCGpvPu6ptU4IXNKWv9tjDAZdAW79uYhaLBIXARR4xUBbUTBicFIVCUTEoByaTDuqXnavkg4OoE5iSGqqq0klRMBtloKQ42V70epl89PTwlYEYp6MHGLAwNx2G95SufWoXca6AWZmxjWwIvImwpIN51mMjTXv8JLv5apwf6SitQwAwp4xnibb4Rlt4SpgbKFDBylCkfxuNXJ4TBExYC17/90+j6w8FMYs/IFfBPVS0MyAnV/jd5IcLmPMGBDHLl/ODhvn4lqO1XJ0fVikqR9Rj2skzwIDAQAB";

/** The form pieces a sign-up flow needs: checkbox, field adornments, rule popover, code cells, dropdown, inline links, RSA. */
export default function Form() {
    const [email, setEmail] = signal("");
    const [password, setPassword] = signal("");
    const [shown, setShown] = signal(false);
    const [focused, setFocused] = signal(false);
    const [agreed, setAgreed] = signal(false);
    const [voice, setVoice] = signal("");
    const [pinError, setPinError] = signal(false);
    const pin = ref<CodeFieldCommands>();
    async function encrypt() {
        // the plain password never leaves this page: only the ciphertext would go on to the next step
        const cipher = await crypto.rsaEncrypt(password(), { publicKey: DEMO_PUBLIC_KEY });
        void ui.toast(`${cipher.length} base64 chars: ${cipher.slice(0, 16)}…`);
    }
    const emailError = () => email() !== "" && !email().includes("@");

    return (
        <Column width="fill" padding={16} gap={12} scroll>
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

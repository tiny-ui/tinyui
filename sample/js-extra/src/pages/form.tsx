import { Checkbox, Column, Option, PinField, ref, Row, Select, signal, Text, TextField, type PinFieldCommands } from "tinyui-core";
import { ui } from "tinyui-native";
import visibility from "@material-symbols/svg-400/outlined/visibility.svg";
import visibilityOff from "@material-symbols/svg-400/outlined/visibility_off.svg";

/** The form pieces a sign-up flow needs: checkbox, field adornments, PIN cells, dropdown, inline links. */
export default function Form() {
    const [email, setEmail] = signal("");
    const [shown, setShown] = signal(false);
    const [focused, setFocused] = signal(false);
    const [agreed, setAgreed] = signal(false);
    const [voice, setVoice] = signal("");
    const [pinError, setPinError] = signal(false);
    const pin = ref<PinFieldCommands>();
    const emailError = () => email() !== "" && !email().includes("@");

    return (
        <Column width="fill" padding={16} gap={12} scroll>
            <TextField
                width="fill" placeholder="Email" keyboard="email" imeAction="next"
                error={emailError()} supportingText={emailError() ? "Not an email address" : ""}
                onChange={(e) => setEmail(e.text)}
            />
            <TextField
                width="fill" placeholder="Password" keyboard="password" secure={!shown()}
                trailingIcon={shown() ? visibilityOff : visibility} onTrailingClick={() => setShown(!shown())}
                supportingText={focused() ? "8+ characters, letters and digits" : ""}
                onFocusChange={(e) => setFocused(e.focused)}
            />
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
            <PinField width="fill" autofill="oneTimeCode" onComplete={(e) => void ui.toast(`code ${e.text}`)} />
            <Text text="Transaction PIN (1234 is wrong)" style="titleSmall" />
            <PinField
                width="fill" secure error={pinError()} ref={pin}
                onChange={(e) => { if (e.text) setPinError(false) }}
                onComplete={(e) => { if (e.text.startsWith("1234")) { setPinError(true); pin.cmd("clear", {}); } else void ui.toast("PIN set") }}
            />
        </Column>
    );
}

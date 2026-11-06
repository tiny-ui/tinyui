package app.tinyui.components.internal

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.tinyui.internal.oneTimeCodeImeOptions
import app.tinyui.schema.Commands
import app.tinyui.schema.NodeScope
import app.tinyui.textFieldStyle

/**
 * Digit cells drawn over one invisible input, so focus and the IME never hop between fields across the bridge
 * (docs/adr-004 §3.3); `onComplete` fires each time the last cell gets filled.
 */
@Composable
internal fun CodeFieldComponent(scope: NodeScope) {
    fun length() = (scope.get<Double>("length")?.toInt() ?: 6).coerceIn(1, MAX_CODE_LENGTH)
    val length = length()
    var value by retain { mutableStateOf(TextFieldValue("")) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    // [force]: commands report onChange even when nothing changed (TextField.setText does not, since 2026-11-03)
    fun update(raw: String, force: Boolean = false) {
        // the live prop, like TextField.clip: a command may run before this composition saw a new length
        val max = length()
        val t = raw.filter { it in '0'..'9' }.take(max)
        val changed = t != value.text
        value = TextFieldValue(t, selection = TextRange(t.length))
        if (!changed && !force) return
        if (scope.has("onChange")) scope.dispatch("onChange", payload("text", t))
        if (changed && t.length == max && scope.has("onComplete")) scope.dispatch("onComplete", payload("text", t))
    }
    // fewer cells drop the digits that no longer fit at once, rather than keeping them hidden until the next edit
    LaunchedEffect(length) { if (value.text.length > length) update(value.text) }
    scope.Commands { command ->
        when (command.name) {
            "setText" -> update(command.args["text"] as? String ?: "", force = true)
            "clear" -> update("", force = true)
            "focus" -> focusRequester.requestFocus()
        }
    }
    val secure = scope.get<Boolean>("secure") == true
    val error = scope.get<Boolean>("error") == true
    val oneTimeCode = scope.get<String>("autofill") == "oneTimeCode"
    // the cells follow the host's text input colours like TextField does (TinyUIDefaults)
    val colors = textFieldStyle().colors
    BasicTextField(
        value = value,
        onValueChange = { update(it.text) },
        enabled = scope.get<Boolean>("enabled") ?: true,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done, platformImeOptions = oneTimeCodeImeOptions(oneTimeCode)),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        // invisible, but its semantics are what a screen reader reads: masked like the cells
        visualTransformation = if (secure) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = scope.modifier(clickable = false).focusRequester(focusRequester).onFocusChanged { focused = it.isFocused }
            .then(if (oneTimeCode) Modifier.semantics { contentType = ContentType.SmsOtpCode } else Modifier),
        decorationBox = { inner ->
            Box {
                // the field below is the one accessible node; the cells would read the code a second time
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.clearAndSetSemantics {}) {
                    for (i in 0 until length) {
                        val digit = value.text.getOrNull(i)
                        val current = focused && i == minOf(value.text.length, length - 1)
                        val outline = when { error -> colors.errorIndicatorColor; current -> colors.focusedIndicatorColor; else -> colors.unfocusedIndicatorColor }
                        Box(
                            contentAlignment = Alignment.Center,
                            // the minimum keeps cells usable where weight has no space to share (a scrolling Row)
                            modifier = Modifier.weight(1f).widthIn(min = 40.dp).height(56.dp).border(if (current || error) 2.dp else 1.dp, outline, RoundedCornerShape(8.dp)),
                        ) {
                            if (digit != null) Text(if (secure) "\u2022" else digit.toString(), style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                }
                // the real input stays composed (iOS keeps its text session on it) but never shows
                Box(Modifier.matchParentSize().alpha(0f)) { inner() }
            }
        },
    )
}

private const val MAX_CODE_LENGTH = 12

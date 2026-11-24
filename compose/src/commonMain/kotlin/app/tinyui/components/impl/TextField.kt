package app.tinyui.components.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.LayoutModifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.tinyui.components.Commands
import app.tinyui.components.IconValue
import app.tinyui.components.NodeScope
import app.tinyui.components.ResolvedTextFieldStyle
import app.tinyui.components.textFieldStyle
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Cuts an edit down to the page's `maxLength` ([clipTo]): it cuts, it does not reject. [max] is read at each edit, so an
 * edit between a flush that lowers the limit and the recomposition that would install a new transformation still
 * clips to the new limit.
 */
private class ClipTransformation(private val max: () -> Int?) : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        val clipped = clipTo(asCharSequence().toString(), max())
        if (clipped.length < length) replace(clipped.length, length, "")
    }
}

/**
 * Text and cursor stay here; JS gets `onChange` / `onCommit` / `onFocusChange` and sends `setText` / `focus` / `blur`
 * (docs/adr-004 §3.3). Error, supporting text, prefix and trailing icon are looks the page drives through props.
 *
 * M3's `OutlinedTextField` on a `TextFieldState`: the state owns text, cursor and composition, so a page reloading or
 * a sheet recomposing never resets the cursor. The host's density (`TinyUIDefaults.textField`) goes in through M3's own
 * `contentPadding`, and `heightIn` stands in for the 56dp minimum M3 would otherwise apply — nothing is drawn by hand.
 * A field that can be masked (`secure` set, or `keyboard="password"`) is M3's secure field; flipping `secure` only changes
 * how it obscures, so focus and the keyboard stay.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TextFieldComponent(scope: NodeScope) {
    // what outlives a composition (the observer, the transformation, a command) reads the scope of the latest one:
    // a command runs before the recomposition that would refresh a captured value
    val current by rememberUpdatedState(scope)
    fun limit() = current.get<Double>("maxLength")?.toInt()?.takeIf { it > 0 }
    fun clip(t: String) = clipTo(t, limit())
    val state = retain { TextFieldState(clip(scope.get<String>("initialText") ?: "")) }
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    AutoFocus(scope, focusRequester)
    // the last text the page heard of: a change is reported once, by whoever notices it first, so `setText` and a commit
    // right after an edit report in order; the same text again is not a change
    val reported = remember { mutableStateOf(state.text.toString()) }
    fun changed() {
        val text = state.text.toString()
        if (text == reported.value) return
        reported.value = text
        if (current.has("onChange")) current.dispatch("onChange", payload("text", text))
    }
    val commit = { changed(); if (current.has("onCommit")) current.dispatch("onCommit", payload("text", state.text.toString())) }
    // the user's edits, observed; edits inside one dispatch arrive as the last of them
    LaunchedEffect(state) { snapshotFlow { state.text.toString() }.collect { changed() } }
    // a lowered limit applies to what is already there
    LaunchedEffect(limit()) { clip(state.text.toString()).let { if (it != state.text.toString()) { state.setTextAndPlaceCursorAtEnd(it); changed() } } }
    scope.Commands { command ->
        when (command.name) {
            "setText" -> { state.setTextAndPlaceCursorAtEnd(clip(command.args["text"] as? String ?: "")); changed() }
            "focus" -> focusRequester.requestFocus()
            "blur" -> focusManager.clearFocus()
        }
    }
    val keyboard = when (scope.get<String>("keyboard")) {
        "number" -> KeyboardType.Number; "email" -> KeyboardType.Email; "phone" -> KeyboardType.Phone; "password" -> KeyboardType.Password
        else -> KeyboardType.Text
    }
    val secure = scope.get<Boolean>("secure")
    val next = scope.get<String>("imeAction") == "next"
    // unset leaves the hint to the system; the options carry it on iOS, the semantics on Android (same as CodeField)
    val oneTimeCode = scope.get<String>("autofill") == "oneTimeCode"
    val prefix = scope.get<String>("prefixText")?.takeIf { it.isNotEmpty() }
    val trailing = scope.get<IconValue>("trailingIcon")
    val style = textFieldStyle()
    val colors = style.colors
    val error = scope.get<Boolean>("error") == true
    val placeholder: (@Composable () -> Unit)? = scope.get<String>("placeholder")?.takeIf { it.isNotEmpty() }?.let { { Text(it) } }
    // the leading slot rather than M3 `prefix`: that one hides while the field is empty and unfocused, and cannot take a tap
    val shape = scope.fieldShape()
    val leadingIcon: (@Composable () -> Unit)? = prefix?.let { p -> { Prefix(scope, p, colors.prefixColor(enabled = true, isError = error, focused = focused), style.minHeight, shape) } }
    // the page sizes the glyph (a 20dp eye in a design system), never the slot: the IconButton's 48dp tap target stays;
    // zero or less is "unset", like gap / borderWidth
    val trailingSize = scope.get<Dp>("trailingIconSize")?.takeIf { it > 0.dp }
    val trailingIcon: (@Composable () -> Unit)? = trailing?.let { icon ->
        {
            val glyph = @Composable { Icon(icon.vector, contentDescription = null, modifier = if (trailingSize != null) Modifier.size(trailingSize) else Modifier) }
            if (scope.has("onTrailingClick")) IconButton(onClick = { scope.dispatch("onTrailingClick") }) { glyph() } else glyph()
        }
    }
    val supportingText: (@Composable () -> Unit)? = scope.get<String>("supportingText")?.takeIf { it.isNotEmpty() }?.let { { Text(it) } }
    val keyboardOptions = KeyboardOptions(
        keyboardType = keyboard, imeAction = if (next) ImeAction.Next else ImeAction.Done,
        platformImeOptions = if (oneTimeCode) oneTimeCodeImeOptions(true) else null,
    )
    // moving focus away commits through onFocusChanged; commit here only when there was nowhere to go
    val onKeyboardAction = KeyboardActionHandler { if (next) { if (!focusManager.moveFocus(FocusDirection.Next)) commit() } else commit() }
    val inputTransformation = remember { ClipTransformation { limit() } }
    val masked = secure != null || keyboard == KeyboardType.Password
    val singleLine = masked || (scope.get<Boolean>("singleLine") ?: true)
    val modifier = scope.modifier(clickable = false, clip = false).fieldMinHeight(style, singleLine).focusRequester(focusRequester)
        .then(if (oneTimeCode) Modifier.semantics { contentType = ContentType.SmsOtpCode } else Modifier)
        .onFocusChanged {
            // the first callback reports the initial unfocused state: not a change, not a focus loss
            if (it.isFocused == focused) return@onFocusChanged
            focused = it.isFocused
            if (scope.has("onFocusChange")) scope.dispatch("onFocusChange", buildJsonObject { put("focused", focused) }.toString())
            if (!focused) commit()
        }
    // M3's secure field is single-line; `singleLine=false` has no meaning for a masked field (docs/components.md §3)
    if (masked) {
        OutlinedSecureTextField(
            state = state,
            modifier = modifier,
            placeholder = placeholder,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            supportingText = supportingText,
            isError = error,
            inputTransformation = inputTransformation,
            // M3's default while masked: the character just typed shows briefly, as the platforms' own password fields do
            textObfuscationMode = if (secure ?: true) TextObfuscationMode.RevealLastTyped else TextObfuscationMode.Visible,
            keyboardOptions = keyboardOptions,
            onKeyboardAction = onKeyboardAction,
            colors = colors,
            contentPadding = style.contentPadding,
            shape = shape,
        )
    } else {
        OutlinedTextField(
            state = state,
            modifier = modifier,
            placeholder = placeholder,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            supportingText = supportingText,
            isError = error,
            inputTransformation = inputTransformation,
            keyboardOptions = keyboardOptions,
            onKeyboardAction = onKeyboardAction,
            lineLimits = if (singleLine) TextFieldLineLimits.SingleLine else TextFieldLineLimits.Default,
            colors = colors,
            contentPadding = style.contentPadding,
            shape = shape,
        )
    }
}

/**
 * The field's minimum height: the host's ([ResolvedTextFieldStyle.minHeight]) in place of the 56dp M3 applies by itself.
 *
 * Measuring is plain `heightIn(min)`: a minimum from outside switches M3's `defaultMinSize` off. An intrinsic
 * measurement (what `align="stretch"` asks) has no constraints to do that with, so M3 answers with its own 56dp
 * floor whatever the host set, and a row sized from that answer comes out taller than the field in it. For a
 * single-line field the box is one line of text and the padding, a height known here; when that is under M3's floor,
 * the floor is what M3 answered for the box, and the answer is corrected by the difference. A multi-line field grows
 * with its text and a labelled one is taller by its label; pass `singleLine = false` for those and M3's answer is left
 * alone.
 */
@Composable
internal fun Modifier.fieldMinHeight(style: ResolvedTextFieldStyle, singleLine: Boolean): Modifier {
    val m3 = OutlinedTextFieldDefaults.MinHeight
    val lineHeight = LocalTextStyle.current.lineHeight
    if (!singleLine || style.minHeight >= m3 || !lineHeight.isSp) return heightIn(min = style.minHeight)
    val line = with(LocalDensity.current) { lineHeight.toDp() }
    val box = maxOf(style.minHeight, line + style.contentPadding.calculateTopPadding() + style.contentPadding.calculateBottomPadding())
    return if (box >= m3) heightIn(min = style.minHeight) else this.then(FieldMinHeight(style.minHeight, m3 - box))
}

/** `heightIn(min)` whose intrinsic heights are M3's less [excess], what M3's own floor adds over the real box. */
private data class FieldMinHeight(val min: Dp, val excess: Dp) : LayoutModifier {
    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val least = min.roundToPx().coerceIn(constraints.minHeight, constraints.maxHeight)
        val placeable = measurable.measure(constraints.copy(minHeight = least))
        return layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
    }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int): Int =
        (measurable.minIntrinsicHeight(width) - excess.roundToPx()).coerceAtLeast(min.roundToPx())

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int): Int =
        (measurable.maxIntrinsicHeight(width) - excess.roundToPx()).coerceAtLeast(min.roundToPx())
}

/**
 * The text before the input, drawn in M3's leading slot rather than its `prefix` (that one hides while the field is
 * empty and unfocused, and cannot take a tap), but coloured with the prefix colours: it is a prefix that only borrows the
 * slot. With `prefixBackground` it becomes a block inset [PrefixInset] in the field, its corners concentric with the
 * field's. M3 already leaves 4dp between the leading slot and the input (16dp padding less the 12dp a 24dp icon gets
 * inside its 48dp slot), so the 8dp end padding makes 12dp from block to input.
 */
@Composable
private fun Prefix(scope: NodeScope, text: String, color: Color, minHeight: Dp, outer: Shape) {
    val tap = if (scope.has("onPrefixClick")) Modifier.clickable { scope.dispatch("onPrefixClick") } else Modifier
    val icon = scope.get<IconValue>("prefixIcon")
    val iconSize = scope.get<Dp>("prefixIconSize")
    val content: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text, color = color)
            if (icon != null) Icon(icon.vector, contentDescription = null, tint = color, modifier = if (iconSize != null) Modifier.size(iconSize) else Modifier)
        }
    }
    val fill = scope.color("prefixBackground")
    if (fill == null) {
        Box(tap.padding(start = 16.dp, end = 8.dp)) { content() }
        return
    }
    val shape = remember(outer) { if (outer is CornerBasedShape) ConcentricInsetShape(outer, PrefixInset) else outer }
    Box(
        Modifier.padding(start = PrefixInset, end = 8.dp)
            // the field's minimum height (M3's 56dp, or the host's) less the inset above and below; the leading slot is
            // centred, so a field taller than its minimum (a large line height or font scale) keeps the block centred
            .height(minHeight - PrefixInset * 2)
            .clip(shape).background(fill).then(tap).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

private val PrefixInset = 4.dp

/**
 * The outline of a text input: `cornerRadius` where the page sets it (a pill-shaped search field), else M3's. It is the
 * field's own shape rather than a clip, which would cut the supporting text below the field too.
 */
@Composable
internal fun NodeScope.fieldShape(): Shape = get<Dp>("cornerRadius")?.let { RoundedCornerShape(it) } ?: OutlinedTextFieldDefaults.shape

/**
 * [outer]'s corners shrunk by [inset], for a block that sits [inset] inside a shape and should follow it. `copy` keeps the
 * kind of corner the theme chose (rounded, cut…); shrinking by the inset is exactly concentric for rounded corners and
 * close enough for cut ones.
 */
internal class ConcentricInsetShape(private val outer: CornerBasedShape, private val inset: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val d = with(density) { inset.toPx() }
        // corners in percent are taken against the block grown by the inset, which is close enough for a block inside a field
        val outerSize = Size(size.width + 2 * d, size.height + 2 * d)
        fun inner(c: CornerSize) = CornerSize((c.toPx(outerSize, density) - d).coerceAtLeast(0f))
        return outer.copy(inner(outer.topStart), inner(outer.topEnd), inner(outer.bottomEnd), inner(outer.bottomStart))
            .createOutline(size, layoutDirection, density)
    }
}

/** At most [max] UTF-16 units, never ending inside a surrogate pair (an emoji keeps both halves or neither). */
internal fun clipTo(text: String, max: Int?): String {
    if (max == null || text.length <= max) return text
    val end = if (text[max - 1].isHighSurrogate()) max - 1 else max
    return text.substring(0, end)
}

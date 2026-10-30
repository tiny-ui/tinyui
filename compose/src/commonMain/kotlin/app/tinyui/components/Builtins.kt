package app.tinyui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LoadingIndicatorDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import app.tinyui.components.generated.BuiltinSchemas
import app.tinyui.oneTimeCodeImeOptions
import app.tinyui.node.UINode
import app.tinyui.schema.Commands
import app.tinyui.schema.IconValue
import app.tinyui.schema.ComponentRegistry
import app.tinyui.schema.NodeScope
import app.tinyui.schema.SizeValue
import app.tinyui.schema.Theme

/** First batch of built-ins (docs/components.md §3); schemas come from schema/ via `tinyui schema`. */
fun ComponentRegistry.registerBuiltins(): ComponentRegistry = apply {
    register(BuiltinSchemas.Column) { scope ->
        val scroll = scope.get<Boolean>("scroll") == true
        Column(
            modifier = scope.modifier().then(if (scroll) Modifier.verticalScroll(retainScrollState()) else Modifier),
            verticalArrangement = verticalArrangement(scope["justify"], scope["gap"]),
            horizontalAlignment = when (scope.get<String>("align")) { "center" -> Alignment.CenterHorizontally; "end" -> Alignment.End; else -> Alignment.Start },
        ) { scope.Children { child -> if (scroll) Modifier else weightOf(child)?.let { Modifier.weight(it) } ?: Modifier } }
    }
    register(BuiltinSchemas.Row) { scope ->
        val scroll = scope.get<Boolean>("scroll") == true
        Row(
            modifier = scope.modifier().then(if (scroll) Modifier.horizontalScroll(retainScrollState()) else Modifier),
            horizontalArrangement = horizontalArrangement(scope["justify"], scope["gap"]),
            verticalAlignment = when (scope.get<String>("align")) { "center" -> Alignment.CenterVertically; "end" -> Alignment.Bottom; else -> Alignment.Top },
        ) { scope.Children { child -> if (scroll) Modifier else weightOf(child)?.let { Modifier.weight(it) } ?: Modifier } }
    }
    register(BuiltinSchemas.Box) { scope ->
        Box(modifier = scope.modifier(), contentAlignment = boxAlignment(scope["align"])) { scope.Children() }
    }
    register(BuiltinSchemas.Text) { scope ->
        val maxLines = scope.get<Double>("maxLines")?.toInt()?.takeIf { it > 0 } ?: Int.MAX_VALUE
        val raw = scope.get<String>("text") ?: ""
        val primary = MaterialTheme.colorScheme.primary
        // without a handler the brackets are just text: a page that never asked for links never gets them
        val text = if (scope.has("onLinkClick")) remember(raw, primary) { linkedText(raw, primary) { id -> scope.dispatch("onLinkClick", payload("id", id)) } } else AnnotatedString(raw)
        Text(
            text = text,
            style = scope.get<String>("style")?.let { Theme.textStyle(MaterialTheme.typography, it) } ?: LocalTextStyle.current,
            color = scope.color("color") ?: Color.Unspecified,
            fontSize = scope["fontSize"] ?: TextUnit.Unspecified,
            fontWeight = when (scope.get<String>("fontWeight")) { "normal" -> FontWeight.Normal; "medium" -> FontWeight.Medium; "bold" -> FontWeight.Bold; else -> null },
            maxLines = maxLines,
            overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis,
            textAlign = when (scope.get<String>("align")) { "center" -> TextAlign.Center; "end" -> TextAlign.End; else -> TextAlign.Start },
            modifier = scope.modifier(),
        )
    }
    register(BuiltinSchemas.Button) { scope ->
        val onClick = { if (scope.has("onClick")) scope.dispatch("onClick") }
        val enabled = scope.get<Boolean>("enabled") ?: true
        val label: @Composable () -> Unit = { if (scope.node.children.isEmpty()) Text(scope["text"] ?: "") else scope.Children() }
        val modifier = scope.modifier(clickable = false) // the button owns the click and its enabled state
        when (scope.get<String>("variant")) {
            "outlined" -> OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier, content = { label() })
            "text" -> TextButton(onClick = onClick, enabled = enabled, modifier = modifier, content = { label() })
            else -> Button(onClick = onClick, enabled = enabled, modifier = modifier, content = { label() })
        }
    }
    register(BuiltinSchemas.RadioButton) { scope ->
        RadioButton(
            selected = scope.get<Boolean>("selected") == true,
            onClick = if (scope.has("onClick")) ({ scope.dispatch("onClick") }) else null,
            enabled = scope.get<Boolean>("enabled") ?: true,
            modifier = scope.modifier(clickable = false),
        )
    }
    register(BuiltinSchemas.Checkbox) { scope ->
        Checkbox(
            checked = scope.get<Boolean>("checked") == true,
            onCheckedChange = if (scope.has("onClick")) ({ scope.dispatch("onClick") }) else null,
            enabled = scope.get<Boolean>("enabled") ?: true,
            modifier = scope.modifier(clickable = false),
        )
    }
    register(BuiltinSchemas.TextField) { scope -> TextFieldComponent(scope) }
    register(BuiltinSchemas.CodeField) { scope -> CodeFieldComponent(scope) }
    register(BuiltinSchemas.Select) { scope -> SelectComponent(scope) }
    // data for its Select, never drawn
    register(BuiltinSchemas.Option) { }
    register(BuiltinSchemas.LazyColumn) { scope -> LazyColumnComponent(scope) }
    register(BuiltinSchemas.Spacer) { scope -> Spacer(scope.modifier()) }
    register(BuiltinSchemas.Icon) { scope ->
        val icon = scope.get<IconValue>("icon") ?: return@register
        Icon(icon.vector, contentDescription = scope.get<String>("label"), tint = scope.color("tint") ?: LocalContentColor.current, modifier = scope.modifier().size(scope.get<Dp>("size") ?: 24.dp))
    }
    register(BuiltinSchemas.Loading) { scope -> LoadingComponent(scope) }
    register(BuiltinSchemas.BottomSheet) { scope -> BottomSheetComponent(scope) }
    register(BuiltinSchemas.Popover) { scope -> PopoverComponent(scope) }
    register(BuiltinSchemas.TopAppBar) { scope -> TopAppBarComponent(scope) }
    register(BuiltinSchemas.BackHandler) { scope -> BackHandlerComponent(scope) }
    register(BuiltinSchemas.Menu) { scope -> MenuComponent(scope) }
    register(BuiltinSchemas.Divider) { scope ->
        HorizontalDivider(
            modifier = scope.modifier(),
            thickness = scope.get<Dp>("thickness") ?: DividerDefaults.Thickness,
            color = scope.color("color") ?: DividerDefaults.color,
        )
    }
}

/**
 * What a user did to a component that a retained page must come back with (docs/app-model.md §5): typed text, code
 * digits and scroll positions are `retain`ed, so they live as long as the page; focus and an open menu are
 * `remember`ed and start over. Under the default store `retain` is `remember`.
 */
@Composable
private fun retainScrollState(): ScrollState = retain { ScrollState(0) }

// a scrolling axis is unbounded, so there is no remaining space to share: weight is ignored there rather than collapsing the child to 0
private fun weightOf(child: UINode): Float? = (child.props["weight"] as? Double)?.toFloat()?.takeIf { it > 0f }

private fun verticalArrangement(justify: String?, gap: Dp?): Arrangement.Vertical {
    val spaced = gap != null && gap > 0.dp
    return when (justify) {
        "center" -> if (spaced) Arrangement.spacedBy(gap, Alignment.CenterVertically) else Arrangement.Center
        "end" -> if (spaced) Arrangement.spacedBy(gap, Alignment.Bottom) else Arrangement.Bottom
        "spaceBetween" -> Arrangement.SpaceBetween
        else -> if (spaced) Arrangement.spacedBy(gap) else Arrangement.Top
    }
}

private fun horizontalArrangement(justify: String?, gap: Dp?): Arrangement.Horizontal {
    val spaced = gap != null && gap > 0.dp
    return when (justify) {
        "center" -> if (spaced) Arrangement.spacedBy(gap, Alignment.CenterHorizontally) else Arrangement.Center
        "end" -> if (spaced) Arrangement.spacedBy(gap, Alignment.End) else Arrangement.End
        "spaceBetween" -> Arrangement.SpaceBetween
        else -> if (spaced) Arrangement.spacedBy(gap) else Arrangement.Start
    }
}

private fun boxAlignment(name: String?): Alignment = when (name) {
    "topCenter" -> Alignment.TopCenter; "topEnd" -> Alignment.TopEnd
    "centerStart" -> Alignment.CenterStart; "center" -> Alignment.Center; "centerEnd" -> Alignment.CenterEnd
    "bottomStart" -> Alignment.BottomStart; "bottomCenter" -> Alignment.BottomCenter; "bottomEnd" -> Alignment.BottomEnd
    else -> Alignment.TopStart
}

/**
 * Text and cursor stay here; JS gets `onChange` / `onCommit` / `onFocusChange` and sends `setText` / `focus` / `blur`
 * (docs/adr-004 §3.3). Error, supporting text, prefix and trailing icon are looks the page drives through props.
 */
@Composable
private fun TextFieldComponent(scope: NodeScope) {
    // read when used, not captured: a command runs before the recomposition that would refresh a captured value
    fun clip(t: String) = clipTo(t, scope.get<Double>("maxLength")?.toInt()?.takeIf { it > 0 })
    val maxLength = scope.get<Double>("maxLength")
    var text by retain { mutableStateOf(clip(scope.get<String>("initialText") ?: "")) }
    // the cursor and composing region live here; when `text` changes from outside the cursor goes to the end
    var field by remember { mutableStateOf(TextFieldValue(text, TextRange(text.length))) }
    val value = if (field.text == text) field else TextFieldValue(text, TextRange(text.length))
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val interactionSource = remember { MutableInteractionSource() }
    val commit = { if (scope.has("onCommit")) scope.dispatch("onCommit", payload("text", text)) }
    val change = { t: String -> if (t != text) { text = t; if (scope.has("onChange")) scope.dispatch("onChange", payload("text", t)) } }
    // a lowered limit applies to what is already there, reported like any other change
    LaunchedEffect(maxLength) { change(clip(text)) }
    scope.Commands { command ->
        when (command.name) {
            "setText" -> { text = clip(command.args["text"] as? String ?: ""); if (scope.has("onChange")) scope.dispatch("onChange", payload("text", text)) }
            "focus" -> focusRequester.requestFocus()
            "blur" -> focusManager.clearFocus()
        }
    }
    val keyboard = when (scope.get<String>("keyboard")) {
        "number" -> KeyboardType.Number; "email" -> KeyboardType.Email; "phone" -> KeyboardType.Phone; "password" -> KeyboardType.Password
        else -> KeyboardType.Text
    }
    val secure = scope.get<Boolean>("secure") ?: (keyboard == KeyboardType.Password)
    val next = scope.get<String>("imeAction") == "next"
    val prefix = scope.get<String>("prefixText")?.takeIf { it.isNotEmpty() }
    val trailing = scope.get<IconValue>("trailingIcon")
    val isError = scope.get<Boolean>("error") == true
    val singleLine = scope.get<Boolean>("singleLine") ?: true
    val visualTransformation = if (secure) PasswordVisualTransformation() else VisualTransformation.None
    val colors = OutlinedTextFieldDefaults.colors()
    val textStyle = LocalTextStyle.current.merge(color = MaterialTheme.colorScheme.onSurface)
    // `height` is the box, not the component: the supporting text hangs below it (docs/components.md §3). The
    // vertical padding shrinks to fit a line into it; M3's 16dp on each side makes the default 56dp with a 24sp line.
    val boxHeight = (scope.get<SizeValue>("height") as? SizeValue.Fixed)?.dp
    val vertical = if (boxHeight == null) OutlinedTextFieldDefaults.contentPadding().calculateTopPadding() else {
        // a style without a line height (none of M3's) gets the usual 1.5× of its size
        val line = with(LocalDensity.current) { if (textStyle.lineHeight.isSpecified) textStyle.lineHeight.toDp() else textStyle.fontSize.toDp() * 1.5f }
        ((boxHeight - line) / 2).coerceIn(0.dp, OutlinedTextFieldDefaults.contentPadding().calculateTopPadding())
    }
    val supporting = scope.get<String>("supportingText")?.takeIf { it.isNotEmpty() }
    CompositionLocalProvider(LocalTextSelectionColors provides colors.textSelectionColors) { BasicTextField(
        value = value,
        onValueChange = { v ->
            val t = clip(v.text)
            field = if (t == v.text) v else v.copy(text = t, selection = TextRange(t.length))
            change(t)
        },
        singleLine = singleLine,
        textStyle = textStyle,
        cursorBrush = SolidColor(if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = if (next) ImeAction.Next else ImeAction.Done),
        // moving focus away commits through onFocusChanged; commit here only when there was nowhere to go
        keyboardActions = KeyboardActions(onDone = { commit() }, onNext = { if (!focusManager.moveFocus(FocusDirection.Next)) commit() }),
        visualTransformation = visualTransformation,
        interactionSource = interactionSource,
        modifier = scope.modifier(clickable = false, height = false)
            // what M3's OutlinedTextField announces: the error state, here with the page's own text
            .semantics { if (isError) error(supporting ?: "Invalid input") }
            .defaultMinSize(minWidth = OutlinedTextFieldDefaults.MinWidth, minHeight = boxHeight ?: OutlinedTextFieldDefaults.MinHeight)
            .focusRequester(focusRequester)
            .onFocusChanged {
                // the first callback reports the initial unfocused state: not a change, not a focus loss
                if (it.isFocused == focused) return@onFocusChanged
                focused = it.isFocused
                if (scope.has("onFocusChange")) scope.dispatch("onFocusChange", buildJsonObject { put("focused", focused) }.toString())
                if (!focused) commit()
            },
        decorationBox = { inner ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = text,
                innerTextField = inner,
                enabled = true,
                singleLine = singleLine,
                visualTransformation = visualTransformation,
                interactionSource = interactionSource,
                isError = isError,
                placeholder = scope.get<String>("placeholder")?.takeIf { it.isNotEmpty() }?.let { { Text(it) } },
                // the leading slot rather than M3 `prefix`: that one hides while the field is empty and unfocused, and cannot take a tap
                leadingIcon = prefix?.let { p ->
                    {
                        val tap = if (scope.has("onPrefixClick")) Modifier.clickable { scope.dispatch("onPrefixClick") } else Modifier
                        Text(p, modifier = tap.padding(start = 16.dp, end = 8.dp))
                    }
                },
                trailingIcon = trailing?.let { icon ->
                    {
                        if (scope.has("onTrailingClick")) IconButton(onClick = { scope.dispatch("onTrailingClick") }) { Icon(icon.vector, contentDescription = null) }
                        else Icon(icon.vector, contentDescription = null)
                    }
                },
                supportingText = supporting?.let { { Text(it) } },
                colors = colors,
                contentPadding = OutlinedTextFieldDefaults.contentPadding(top = vertical, bottom = vertical),
            )
        },
    ) }
}

/**
 * Digit cells drawn over one invisible input, so focus and the IME never hop between fields across the bridge
 * (docs/adr-004 §3.3); `onComplete` fires each time the last cell gets filled.
 */
@Composable
private fun CodeFieldComponent(scope: NodeScope) {
    fun length() = (scope.get<Double>("length")?.toInt() ?: 6).coerceIn(1, MAX_CODE_LENGTH)
    val length = length()
    var value by retain { mutableStateOf(TextFieldValue("")) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    // [force]: commands report onChange even when nothing changed, like TextField.setText
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
    val colors = MaterialTheme.colorScheme
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
                        val outline = when { error -> colors.error; current -> colors.primary; else -> colors.outline }
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

/** Choices are the `Option` children, read as data; the selection is the page's prop. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectComponent(scope: NodeScope) {
    val options = scope.node.children.filter { it.type == "Option" }.map { (it.props["value"] as? String ?: "") to (it.props["text"] as? String ?: "") }
    val selected = scope.get<String>("value")
    val enabled = scope.get<Boolean>("enabled") ?: true
    var expanded by remember { mutableStateOf(false) }
    // disabled while open: the menu goes, so nothing can be picked from it any more
    if (!enabled && expanded) expanded = false
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (enabled) expanded = it }, modifier = scope.modifier(clickable = false)) {
        OutlinedTextField(
            value = options.firstOrNull { it.first == selected }?.second ?: "",
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            label = scope.get<String>("label")?.takeIf { it.isNotEmpty() }?.let { { Text(it) } },
            placeholder = scope.get<String>("placeholder")?.takeIf { it.isNotEmpty() }?.let { { Text(it) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for ((value, text) in options) {
                DropdownMenuItem(text = { Text(text) }, enabled = enabled, onClick = { expanded = false; if (scope.has("onSelect")) scope.dispatch("onSelect", payload("value", value)) })
            }
        }
    }
}

private const val MAX_CODE_LENGTH = 12

/** At most [max] UTF-16 units, never ending inside a surrogate pair (an emoji keeps both halves or neither). */
internal fun clipTo(text: String, max: Int?): String {
    if (max == null || text.length <= max) return text
    val end = if (text[max - 1].isHighSurrogate()) max - 1 else max
    return text.substring(0, end)
}

private val LINK = Regex("""\[([^\]]+)]\(([^)\s]+)\)""")

/** The `[label](id)` spans of [text] as links in [color]; everything else, stray brackets included, stays literal. */
internal fun linkedText(text: String, color: Color, onClick: (String) -> Unit): AnnotatedString = buildAnnotatedString {
    var at = 0
    for (m in LINK.findAll(text)) {
        append(text.substring(at, m.range.first))
        val id = m.groupValues[2]
        pushLink(LinkAnnotation.Clickable(tag = id, styles = TextLinkStyles(SpanStyle(color = color))) { onClick(id) })
        append(m.groupValues[1])
        pop()
        at = m.range.last + 1
    }
    append(text.substring(at))
}

/** Rows are the node's children; virtualisation only in composition (docs/adr-003 §3.6). */
@Composable
private fun LazyColumnComponent(scope: NodeScope) {
    val state = retain { LazyListState() }
    val node = scope.node
    scope.Commands { command ->
        if (command.name == "scrollTo") state.animateScrollToItem(((command.args["index"] as? Double) ?: 0.0).toInt().coerceIn(0, maxOf(0, node.children.size - 1)))
    }
    // with the scroll position: a list coming back still at its end has already reported that length
    val reachedEnd = retain { ReachedEnd() }
    if (scope.has("onReachEnd")) {
        // fires on each arrival at the last row and when the list grows while there; re-arms once the user scrolls away
        LaunchedEffect(state, node) {
            snapshotFlow { (state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) to node.children.size }
                .distinctUntilChanged()
                .collect { (last, size) ->
                    val atEnd = size > 0 && last >= size - 1
                    if (!atEnd) reachedEnd.size = -1
                    else if (reachedEnd.size != size) { reachedEnd.size = size; scope.dispatch("onReachEnd") }
                }
        }
    }
    if (scope.has("onScrollEnd")) {
        LaunchedEffect(state) {
            var scrolling = false
            snapshotFlow { state.isScrollInProgress }.distinctUntilChanged().collect { now ->
                if (scrolling && !now) scope.dispatch("onScrollEnd", buildJsonObject { put("index", state.firstVisibleItemIndex) }.toString())
                scrolling = now
            }
        }
    }
    val gap = scope.get<Dp>("gap") ?: 0.dp
    LazyColumn(state = state, modifier = scope.modifier(), verticalArrangement = if (gap > 0.dp) Arrangement.spacedBy(gap) else Arrangement.Top) {
        items(node.children, key = { it.id }) { child -> key(child.id) { scope.RenderChild(child) } }
    }
}

/** The list length `onReachEnd` last fired for; -1 once the user is away from the end. */
private class ReachedEnd(var size: Int = -1)

private fun payload(key: String, value: String) = buildJsonObject { put(key, value) }.toString()

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoadingComponent(scope: NodeScope) {
    LoadingIndicator(
        modifier = scope.modifier().size(scope.get<Dp>("size") ?: 24.dp),
        color = scope.color("color") ?: LoadingIndicatorDefaults.indicatorColor,
    )
}

/**
 * `open` is the page's; going false animates the sheet out before it leaves composition. A user dismissal (swipe, scrim,
 * back) has already hidden it when `onDismiss` fires: it stays down until `open` goes false and true again.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BottomSheetComponent(scope: NodeScope) {
    val open = scope.get<Boolean>("open") == true
    var showing by remember { mutableStateOf(false) }
    // content-sized, never parked half way
    val state = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    LaunchedEffect(open) {
        when {
            // reopened while the hide was still animating: still composed, so bring it back up
            open && showing -> state.show()
            open -> showing = true
            showing -> { state.hide(); showing = false }
        }
    }
    if (showing) {
        ModalBottomSheet(
            onDismissRequest = { showing = false; if (scope.has("onDismiss")) scope.dispatch("onDismiss") },
            sheetState = state,
        ) { scope.Children() }
    }
}

/**
 * A card next to the parent node, which is the anchor: Compose's `Popup` anchors to the layout it is called in, and
 * built-ins render their children without wrappers. Visibility follows `open` (faded); a tap outside is only reported,
 * since the field the user just tapped is "outside" too. Never focusable: a hint under a focused field must not take
 * the keyboard away.
 */
@Composable
private fun PopoverComponent(scope: NodeScope) {
    val visible = remember { MutableTransitionState(false) }
    visible.targetState = scope.get<Boolean>("open") == true
    // stays composed while fading out
    if (!visible.currentState && !visible.targetState) return
    val matchWidth = scope.get<Boolean>("matchAnchorWidth") == true
    var anchorWidth by remember { mutableStateOf<Int?>(null) }
    if (matchWidth) {
        // the same zero-size probe Popup itself uses to find its parent
        Layout(content = {}, modifier = Modifier.onGloballyPositioned { anchorWidth = it.parentLayoutCoordinates?.size?.width }) { _, _ -> layout(0, 0) {} }
        // not drawn until the width is known, so it never shows at the wrong width first
        if (anchorWidth == null) return
    }
    val gap = with(LocalDensity.current) { POPOVER_GAP.roundToPx() }
    val above = scope.get<String>("placement") == "above"
    val position = remember(above, gap) {
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize) =
                popoverOffset(anchorBounds, windowSize, popupContentSize, above, gap, layoutDirection == LayoutDirection.Rtl)
        }
    }
    val width = anchorWidth?.takeIf { matchWidth }?.let { with(LocalDensity.current) { it.toDp() } }
    Popup(
        popupPositionProvider = position,
        onDismissRequest = { if (scope.has("onDismiss")) scope.dispatch("onDismiss") },
        properties = PopupProperties(focusable = false),
    ) {
        AnimatedVisibility(visibleState = visible, enter = fadeIn(), exit = fadeOut()) {
            Surface(
                modifier = if (width != null) Modifier.width(width) else Modifier,
                shape = MaterialTheme.shapes.medium,
                // explicit: contentColorFor would guess from the container and can land on a muted colour
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shadowElevation = 3.dp,
            ) { scope.Children() }
        }
    }
}

private val POPOVER_GAP = 8.dp

/**
 * M3 small top app bar under the status bar; the children are the actions. The status bar padding sits outside the
 * common modifier, so `height` sizes the bar itself; inset padding consumed by the host already (a Scaffold) is not
 * added again. The back button injects a back into the system dispatcher, as a toolbar calls
 * `onBackPressedDispatcher.onBackPressed()`: whatever would take system back takes it (a `BackHandler`, the host's
 * navigation). Without a dispatcher there is no system back, so it does nothing, like a `BackHandler`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopAppBarComponent(scope: NodeScope) {
    val container = scope.color("background") ?: MaterialTheme.colorScheme.surface
    val backIcon = scope.get<IconValue>("backIcon")
    val dispatcher = LocalNavigationEventDispatcherOwner.current?.navigationEventDispatcher
    val input = remember { DirectNavigationEventInput() }
    if (dispatcher != null) {
        DisposableEffect(dispatcher, input) {
            dispatcher.addInput(input)
            onDispose { dispatcher.removeInput(input) }
        }
    }
    Box(Modifier.background(container).windowInsetsPadding(TopAppBarDefaults.windowInsets)) {
        TopAppBar(
            title = { scope.get<String>("title")?.takeIf { it.isNotEmpty() }?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
            navigationIcon = {
                if (backIcon != null) {
                    IconButton(onClick = { if (dispatcher != null) input.backCompleted() }) {
                        Icon(backIcon.vector, contentDescription = scope.get<String>("backLabel"))
                    }
                }
            },
            actions = { scope.Children() },
            // the insets are padded above, outside the height the page gives
            windowInsets = WindowInsets(0),
            expandedHeight = (scope.get<SizeValue>("height") as? SizeValue.Fixed)?.dp ?: TopAppBarDefaults.TopAppBarExpandedHeight,
            colors = TopAppBarDefaults.topAppBarColors(containerColor = container),
            modifier = scope.modifier(),
        )
    }
}

/** Takes back from the system dispatcher while enabled, which is also where the `TopAppBar` button sends it. */
@Composable
private fun BackHandlerComponent(scope: NodeScope) {
    // without a dispatcher there is no system back to take, from the gesture or the top bar button
    if (LocalNavigationEventDispatcherOwner.current == null) return
    val enabled = (scope.get<Boolean>("enabled") ?: true) && scope.has("onBack")
    NavigationBackHandler(state = rememberNavigationEventState(NavigationEventInfo.None), isBackEnabled = enabled, onBackCompleted = { scope.dispatch("onBack") })
}

/**
 * M3 dropdown menu anchored to the parent node, like [PopoverComponent]; the items are the `Option` children, read as
 * data. `open` is the page's, but a pick or a tap outside closes it here at once and is then reported, as
 * [BottomSheetComponent] does: it stays closed until `open` goes false and true again.
 */
@Composable
private fun MenuComponent(scope: NodeScope) {
    val open = scope.get<Boolean>("open") == true
    var showing by remember { mutableStateOf(false) }
    LaunchedEffect(open) { showing = open }
    val options = scope.node.children.filter { it.type == "Option" }.map { (it.props["value"] as? String ?: "") to (it.props["text"] as? String ?: "") }
    DropdownMenu(expanded = showing, onDismissRequest = { showing = false; if (scope.has("onDismiss")) scope.dispatch("onDismiss") }) {
        for ((value, text) in options) {
            DropdownMenuItem(text = { Text(text) }, onClick = { showing = false; if (scope.has("onSelect")) scope.dispatch("onSelect", payload("value", value)) })
        }
    }
}

/**
 * Where a popover of [content] size goes: on the [above] / below side of [anchor] with [gap] px between, start edges
 * aligned. The preferred side wins when it fits, else the other side when that fits, else the roomier side; the result
 * is kept inside [window] on both axes.
 */
internal fun popoverOffset(anchor: IntRect, window: IntSize, content: IntSize, above: Boolean, gap: Int, rtl: Boolean): IntOffset {
    val topY = anchor.top - gap - content.height
    val bottomY = anchor.bottom + gap
    val fitsTop = topY >= 0
    val fitsBottom = bottomY + content.height <= window.height
    val roomierTop = anchor.top > window.height - anchor.bottom
    val onTop = if (above) fitsTop || !fitsBottom && roomierTop else !fitsBottom && (fitsTop || roomierTop)
    val x = if (rtl) anchor.right - content.width else anchor.left
    return IntOffset(
        x.coerceIn(0, (window.width - content.width).coerceAtLeast(0)),
        (if (onTop) topY else bottomY).coerceIn(0, (window.height - content.height).coerceAtLeast(0)),
    )
}

package app.tinyui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import kotlinx.coroutines.CoroutineScope
import kotlin.math.roundToInt
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
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
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import app.tinyui.components.generated.BuiltinSchemas
import app.tinyui.LocalTinyUIDefaults
import app.tinyui.buttonStyle
import app.tinyui.checkboxStyle
import app.tinyui.oneTimeCodeImeOptions
import app.tinyui.node.UINode
import app.tinyui.schema.Commands
import app.tinyui.schema.IconValue
import app.tinyui.schema.ComponentRegistry
import app.tinyui.schema.NodeScope
import app.tinyui.schema.SizeValue
import app.tinyui.schema.Theme
import app.tinyui.textFieldStyle

/** First batch of built-ins (docs/components.md §3); schemas come from schema/ via `tinyui schema`. */
fun ComponentRegistry.registerBuiltins(): ComponentRegistry = apply {
    register(BuiltinSchemas.Column) { scope ->
        val scroll = scope.get<Boolean>("scroll") == true
        val gap = gapBefore(scope)
        val stretch = stretchOf(scope, cross = "width")
        Column(
            modifier = scope.modifier().then(if (scroll) Modifier.verticalScroll(retainScrollState()) else Modifier)
                .then(if (stretch == Stretch.Intrinsic) Modifier.width(IntrinsicSize.Max) else Modifier),
            verticalArrangement = verticalArrangement(scope["justify"]),
            horizontalAlignment = when (scope.get<String>("align")) { "center" -> Alignment.CenterHorizontally; "end" -> Alignment.End; else -> Alignment.Start },
        ) {
            scope.Children { child ->
                val weight = if (scroll) Modifier else weightOf(child)?.let { Modifier.weight(it) } ?: Modifier
                val stretched = if (stretch != Stretch.None && stretches(child, "width")) weight.fillMaxWidth() else weight
                gap(child)?.let { stretched.padding(top = it) } ?: stretched
            }
        }
    }
    register(BuiltinSchemas.Row) { scope ->
        val scroll = scope.get<Boolean>("scroll") == true
        val gap = gapBefore(scope)
        val stretch = stretchOf(scope, cross = "height")
        Row(
            modifier = scope.modifier().then(if (scroll) Modifier.horizontalScroll(retainScrollState()) else Modifier)
                .then(if (stretch == Stretch.Intrinsic) Modifier.height(IntrinsicSize.Min) else Modifier),
            horizontalArrangement = horizontalArrangement(scope["justify"]),
            verticalAlignment = when (scope.get<String>("align")) { "center" -> Alignment.CenterVertically; "end" -> Alignment.Bottom; else -> Alignment.Top },
        ) {
            scope.Children { child ->
                val weight = if (scroll) Modifier else weightOf(child)?.let { Modifier.weight(it) } ?: Modifier
                val stretched = if (stretch != Stretch.None && stretches(child, "height")) weight.fillMaxHeight() else weight
                gap(child)?.let { stretched.padding(start = it) } ?: stretched
            }
        }
    }
    register(BuiltinSchemas.Box) { scope ->
        Box(modifier = scope.modifier(), contentAlignment = boxAlignment(scope["align"])) { scope.Children() }
    }
    register(BuiltinSchemas.Text) { scope ->
        val maxLines = scope.get<Double>("maxLines")?.toInt()?.takeIf { it > 0 } ?: Int.MAX_VALUE
        val raw = scope.get<String>("text") ?: ""
        // the host's link look, else M3's: the primary colour, the text's own weight
        val links = LocalTinyUIDefaults.current.textLinks ?: TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary))
        // without a handler the brackets are just text: a page that never asked for links never gets them; bold needs no one
        val linked = scope.has("onLinkClick")
        val text = remember(raw, links, linked) { markedText(raw, links, if (linked) ({ id -> scope.dispatch("onLinkClick", payload("id", id)) }) else null) }
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
        val variant = scope.get<String>("variant")
        // the host's look for this variant where it set one, else M3's (TinyUIDefaults)
        val style = buttonStyle(variant, enabled)
        when (variant) {
            "outlined" -> OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier, shape = style.shape, colors = style.colors, border = style.border, contentPadding = style.contentPadding, content = { label() })
            "text" -> TextButton(onClick = onClick, enabled = enabled, modifier = modifier, shape = style.shape, colors = style.colors, border = style.border, contentPadding = style.contentPadding, content = { label() })
            else -> Button(onClick = onClick, enabled = enabled, modifier = modifier, shape = style.shape, colors = style.colors, border = style.border, contentPadding = style.contentPadding, content = { label() })
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
        // the stroke overload with M3's own strokes draws exactly what the plain one does; the host may replace either
        val style = checkboxStyle()
        Checkbox(
            checked = scope.get<Boolean>("checked") == true,
            onCheckedChange = if (scope.has("onClick")) ({ scope.dispatch("onClick") }) else null,
            checkmarkStroke = style.checkmarkStroke,
            outlineStroke = style.outlineStroke,
            enabled = scope.get<Boolean>("enabled") ?: true,
            colors = style.colors,
            modifier = scope.modifier(clickable = false),
        )
    }
    register(BuiltinSchemas.TextField) { scope -> TextFieldComponent(scope) }
    register(BuiltinSchemas.CodeField) { scope -> CodeFieldComponent(scope) }
    register(BuiltinSchemas.Select) { scope -> SelectComponent(scope) }
    // data for its Select / Menu / Tabs / IndexBar, never drawn
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
    register(BuiltinSchemas.Tabs) { scope -> TabsComponent(scope) }
    // in a LazyColumn the list pins it (see LazyColumnComponent); on its own it is a Box
    register(BuiltinSchemas.SectionHeader) { scope ->
        Box(modifier = scope.modifier(), contentAlignment = boxAlignment(scope["align"])) { scope.Children() }
    }
    // drawn by the LazyColumn it is a direct child of, never through here: anywhere else it has no list to index
    register(BuiltinSchemas.IndexBar) { scope ->
        LaunchedEffect(Unit) { scope.reportProblem("IndexBar only works as a direct child of LazyColumn") }
    }
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

/**
 * How `align="stretch"` plays out (docs/components.md §3), as in CSS: every child that takes room and sets no size of
 * its own on the cross axis fills the container's. With a fixed or `fill` cross size that is just a fill; otherwise the
 * container first sizes itself from its children (an intrinsic measurement: a Row as tall as its tallest child, a
 * Column as wide as its widest), which asks the whole subtree. A subtree with a component that cannot answer
 * ([ComponentSchema.intrinsics] false, e.g. LazyColumn) would crash the page, so the stretch is dropped and reported.
 */
private enum class Stretch { None, Fill, Intrinsic }

@Composable
private fun ComponentRegistry.stretchOf(scope: NodeScope, cross: String): Stretch {
    if (scope.get<String>("align") != "stretch") return Stretch.None
    val own = scope.get<SizeValue>(cross)
    if (own is SizeValue.Fixed || own == SizeValue.Fill) return Stretch.Fill
    // walked in composition, not remembered: reading every child list subscribes this container to changes at any depth
    val blocker = firstWithoutIntrinsics(scope.node) ?: return Stretch.Intrinsic
    LaunchedEffect(blocker.id) {
        scope.reportProblem("align=\"stretch\" sizes this ${scope.node.type} from its children, which ${blocker.type} (node ${blocker.id}) cannot answer; give the ${scope.node.type} a fixed $cross or take the ${blocker.type} out. Not stretched.")
    }
    return Stretch.None
}

/**
 * Only nodes that take room are measured with their parent; a popup or a sheet lives in a window of its own. A node
 * with both width and height fixed answers from those (Compose's size modifier never asks the content), so nothing
 * under it is ever asked: a lazy list in a fixed box is fine. One fixed axis is not enough — a nested Row asks its
 * children's intrinsic width to share out the space even when the Row is only being asked for its height.
 */
private fun ComponentRegistry.firstWithoutIntrinsics(node: UINode): UINode? {
    for (child in node.children) {
        val schema = schema(child.type) ?: continue
        if (!schema.layout) continue
        if (child.props["width"] is SizeValue.Fixed && child.props["height"] is SizeValue.Fixed) continue
        if (!schema.intrinsics) return child
        firstWithoutIntrinsics(child)?.let { return it }
    }
    return null
}

/** A child that takes room and has no size of its own on [cross]; one that set it, `wrap` included, keeps it. */
private fun ComponentRegistry.stretches(child: UINode, cross: String): Boolean =
    schema(child.type)?.layout != false && child.props[cross] == null

/**
 * `gap` as a leading inset on every child that takes room, except the first of them. A child whose schema has no layout
 * (a popup, a sheet, an `Option`, a `BackHandler`) takes no room — a popup still sits in the parent's composition,
 * Compose's `Popup` anchors through a zero-size node there — so it gets no inset and does not count as first;
 * `Arrangement.spacedBy` would space that node too, and the parent would grow by one gap while the popup is open and
 * shrink back when it closes. Null when there is no gap or the child takes no room.
 */
private fun ComponentRegistry.gapBefore(scope: NodeScope): (UINode) -> Dp? {
    val gap = scope.get<Dp>("gap")?.takeIf { it > 0.dp } ?: return { null }
    val takesRoom = { child: UINode -> schema(child.type)?.layout != false }
    val first = scope.node.children.firstOrNull(takesRoom)?.id
    return { child -> gap.takeIf { takesRoom(child) && child.id != first } }
}

private fun verticalArrangement(justify: String?): Arrangement.Vertical = when (justify) {
    "center" -> Arrangement.Center
    "end" -> Arrangement.Bottom
    "spaceBetween" -> Arrangement.SpaceBetween
    else -> Arrangement.Top
}

private fun horizontalArrangement(justify: String?): Arrangement.Horizontal = when (justify) {
    "center" -> Arrangement.Center
    "end" -> Arrangement.End
    "spaceBetween" -> Arrangement.SpaceBetween
    else -> Arrangement.Start
}

private fun boxAlignment(name: String?): Alignment = when (name) {
    "topCenter" -> Alignment.TopCenter; "topEnd" -> Alignment.TopEnd
    "centerStart" -> Alignment.CenterStart; "center" -> Alignment.Center; "centerEnd" -> Alignment.CenterEnd
    "bottomStart" -> Alignment.BottomStart; "bottomCenter" -> Alignment.BottomCenter; "bottomEnd" -> Alignment.BottomEnd
    else -> Alignment.TopStart
}

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
private fun TextFieldComponent(scope: NodeScope) {
    // what outlives a composition (the observer, the transformation, a command) reads the scope of the latest one:
    // a command runs before the recomposition that would refresh a captured value
    val current by rememberUpdatedState(scope)
    fun limit() = current.get<Double>("maxLength")?.toInt()?.takeIf { it > 0 }
    fun clip(t: String) = clipTo(t, limit())
    val state = retain { TextFieldState(clip(scope.get<String>("initialText") ?: "")) }
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
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
    val leadingIcon: (@Composable () -> Unit)? = prefix?.let { p -> { Prefix(scope, p, colors.prefixColor(enabled = true, isError = error, focused = focused), style.minHeight) } }
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
    val modifier = scope.modifier(clickable = false).heightIn(min = style.minHeight).focusRequester(focusRequester)
        .then(if (oneTimeCode) Modifier.semantics { contentType = ContentType.SmsOtpCode } else Modifier)
        .onFocusChanged {
            // the first callback reports the initial unfocused state: not a change, not a focus loss
            if (it.isFocused == focused) return@onFocusChanged
            focused = it.isFocused
            if (scope.has("onFocusChange")) scope.dispatch("onFocusChange", buildJsonObject { put("focused", focused) }.toString())
            if (!focused) commit()
        }
    // M3's secure field is single-line; `singleLine=false` has no meaning for a masked field (docs/components.md §3)
    if (secure != null || keyboard == KeyboardType.Password) {
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
            lineLimits = if (scope.get<Boolean>("singleLine") ?: true) TextFieldLineLimits.SingleLine else TextFieldLineLimits.Default,
            colors = colors,
            contentPadding = style.contentPadding,
        )
    }
}

/**
 * The text before the input, drawn in M3's leading slot rather than its `prefix` (that one hides while the field is
 * empty and unfocused, and cannot take a tap), but coloured with the prefix colours: it is a prefix that only borrows the
 * slot. With `prefixBackground` it becomes a block inset [PrefixInset] in the field, its corners concentric with the
 * field's. M3 already leaves 4dp between the leading slot and the input (16dp padding less the 12dp a 24dp icon gets
 * inside its 48dp slot), so the 8dp end padding makes 12dp from block to input.
 */
@Composable
private fun Prefix(scope: NodeScope, text: String, color: Color, minHeight: Dp) {
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
    val outer = OutlinedTextFieldDefaults.shape
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
    val chosen = options.firstOrNull { it.first == selected }?.second ?: ""
    val style = textFieldStyle()
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (enabled) expanded = it }, modifier = scope.modifier(clickable = false)) {
        OutlinedTextField(
            // read-only: the text is the choice, so a new choice is a new state
            state = remember(chosen) { TextFieldState(chosen) },
            readOnly = true,
            colors = style.colors,
            contentPadding = style.contentPadding,
            enabled = enabled,
            lineLimits = TextFieldLineLimits.SingleLine,
            label = scope.get<String>("label")?.takeIf { it.isNotEmpty() }?.let { { Text(it) } },
            placeholder = scope.get<String>("placeholder")?.takeIf { it.isNotEmpty() }?.let { { Text(it) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled).fillMaxWidth().heightIn(min = style.minHeight),
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

private val MARK = Regex("""\*\*(.+?)\*\*|\[([^\]]+)]\(([^)\s]+)\)""")

/**
 * The `**bold**` and `[label](id)` spans of [text] — the Markdown subset SwiftUI's `Text` reads, flat, no nesting; stray
 * marks stay literal. Bold is a weight on the span. A link takes [linkStyles] and reports its id through [onLink]; with
 * no handler the brackets stay text (docs/components.md §3).
 */
internal fun markedText(text: String, linkStyles: TextLinkStyles, onLink: ((String) -> Unit)?): AnnotatedString = buildAnnotatedString {
    var at = 0
    for (m in MARK.findAll(text)) {
        val bold = m.groups[1]?.value
        if (bold == null && onLink == null) continue
        append(text.substring(at, m.range.first))
        if (bold != null) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(bold) }
        } else {
            val id = m.groupValues[3]
            pushLink(LinkAnnotation.Clickable(tag = id, styles = linkStyles) { onLink?.invoke(id) })
            append(m.groupValues[2])
            pop()
        }
        at = m.range.last + 1
    }
    append(text.substring(at))
}

private const val INDEX_BAR = "IndexBar"
private const val SECTION_HEADER = "SectionHeader"

/**
 * What a list makes of its children (docs/components.md §3): the [rows], which are the children but for an IndexBar;
 * that [bar], which floats over them (the first, when the page wrote more than one: [bars]); and which rows are
 * [headers] the list pins. The children themselves are the rows when there is no bar, so a plain list allocates
 * nothing here.
 */
private class ListShape(val rows: List<UINode>, val bar: UINode?, val bars: Int, val headers: IntArray) {
    /** The header pinned over row [index] once it is at the top: the last one before it. -1 for a header, or with none before. */
    fun headerOver(index: Int): Int {
        var over = -1
        for (header in headers) if (header < index) over = header else return if (header == index) -1 else over
        return over
    }
}

private val NoHeaders = IntArray(0)

/** One pass over the children; everything the list needs to know of them comes from here, once per change. */
private fun shapeOf(node: UINode): ListShape {
    // the persistent list under the state list: iterating the state list itself reads snapshot state at every step
    val children = node.children.toList()
    var bar: UINode? = null
    var bars = 0
    // only once a bar is met: the children before it, then every row after
    var rows: ArrayList<UINode>? = null
    var headers: ArrayList<Int>? = null
    var row = 0
    for (child in children) {
        val type = child.type
        if (type == INDEX_BAR) {
            if (bar == null) bar = child
            if (rows == null) rows = ArrayList<UINode>(children.size).also { it.addAll(children.subList(0, row)) }
            bars++
            continue
        }
        // `sticky` is on unless the page turned it off
        if (type == SECTION_HEADER && child.props["sticky"] != false) (headers ?: ArrayList<Int>().also { headers = it }).add(row)
        rows?.add(child)
        row++
    }
    return ListShape(rows = rows ?: children, bar = bar, bars = bars, headers = headers?.toIntArray() ?: NoHeaders)
}

/**
 * Rows are the node's children; virtualisation only in composition (docs/adr-003 §3.6). A sticky SectionHeader among
 * them is pinned by the list; an IndexBar child is not a row but a strip over the list's end edge, drawn and driven here.
 */
@Composable
private fun LazyColumnComponent(scope: NodeScope) {
    val state = retain { LazyListState() }
    val node = scope.node
    // each scroll in a job of its own, started where the command is taken so they begin in order. A command must not
    // wait for the animation of the one before it: the list's scroll mutex hands over to the later one, the last
    // command wins. And a scroll that is interrupted (by that later command, by the user's finger) ends with a
    // cancellation, which must end that scroll only, not the loop that takes commands
    val scrolls = rememberCoroutineScope()
    // worked out once each time the children change; the item provider, the commands, the end detection and the
    // index strip all read this one
    val shape = remember(node) { derivedStateOf { shapeOf(node) } }
    scope.Commands { command ->
        val index = ((command.args["index"] as? Double) ?: 0.0).toInt().coerceIn(0, maxOf(0, shape.value.rows.size - 1))
        if (command.name == "scrollTo") scrolls.launch(start = CoroutineStart.UNDISPATCHED) { state.showRow(shape, index, animated = true) }
    }
    // with the scroll position: a list coming back still at its end has already reported that length
    val reachedEnd = retain { ReachedEnd() }
    if (scope.has("onReachEnd")) {
        // fires on each arrival at the last row and when the list grows while there; re-arms once the user scrolls away
        LaunchedEffect(state, node) {
            snapshotFlow { (state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) to shape.value.rows.size }
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
    val list = @Composable { modifier: Modifier ->
        LazyColumn(state = state, modifier = modifier, verticalArrangement = if (gap > 0.dp) Arrangement.spacedBy(gap) else Arrangement.Top) {
            // one immutable copy of the rows per item provider, not the live list: a measure pass takes its provider
            // once, then asks it for the count and for each row by index, outside any snapshot, while the engine
            // thread may apply the next patch in between. Taken in here rather than in the composition: this block is
            // the provider's derived state, recomputed at the next measure after the rows change, so a command of the
            // same flush (which runs before the recomposition that flush causes) already finds the new rows
            val now = shape.value
            val rows = now.rows
            // a list without headers is the single run of items it always was
            if (now.headers.isEmpty()) items(rows, key = { it.id }) { child -> key(child.id) { scope.RenderChild(child) } }
            else {
                // a pinned header is an item of its own kind; the rows between two of them are one run
                var from = 0
                for (at in now.headers) {
                    if (at > from) rowRun(rows, from, at, scope)
                    val header = rows[at]
                    stickyHeader(key = header.id, contentType = SECTION_HEADER) { key(header.id) { scope.RenderChild(header) } }
                    from = at + 1
                }
                if (from < rows.size) rowRun(rows, from, rows.size, scope)
            }
        }
    }
    // read through a derived state: the list is recomposed when a bar comes or goes, not each time a row does
    val bar by remember(node) { derivedStateOf { shape.value.bar } }
    val bars by remember(node) { derivedStateOf { shape.value.bars } }
    if (bars > 1) LaunchedEffect(Unit) { scope.reportProblem("a LazyColumn takes one IndexBar; it has $bars, the first one is used") }
    // the strip needs a box around the list. Kept once there has been a bar, so that showing and hiding it (while
    // searching, say) does not move the list in the composition and rebuild its rows; a list that never has one is
    // composed exactly as before
    val boxed = remember(node) { booleanArrayOf(false) }
    if (bar != null) boxed[0] = true
    if (!boxed[0]) {
        list(scope.modifier())
        return
    }
    Layout(
        content = {
            list(Modifier)
            bar?.let { IndexBarStrip(it, shape, state, scrolls) }
        },
        modifier = scope.modifier(),
    ) { measurables, constraints ->
        // the list takes the constraints it would have had on its own; the strip floats over its end edge
        val listPlaceable = measurables[0].measure(constraints)
        val strip = measurables.getOrNull(1)?.measure(Constraints(maxWidth = listPlaceable.width, maxHeight = listPlaceable.height))
        layout(listPlaceable.width, listPlaceable.height) {
            listPlaceable.place(0, 0)
            strip?.placeRelative(listPlaceable.width - strip.width, (listPlaceable.height - strip.height) / 2)
        }
    }
}

/** The rows `[from, to)` as one run of items. */
private fun LazyListScope.rowRun(rows: List<UINode>, from: Int, to: Int, scope: NodeScope) {
    items(to - from, key = { rows[from + it].id }) { offset ->
        val child = rows[from + offset]
        key(child.id) { scope.RenderChild(child) }
    }
}

/** The height of a header pinned or on screen right now: the room to leave above a row that will have one over it. */
private fun LazyListState.pinnedHeaderSize(shape: ListShape): Int =
    layoutInfo.visibleItemsInfo.firstOrNull { item -> shape.headers.any { it == item.index } }?.size ?: 0

/**
 * Brings row [index] to the top of the list, and out from under the header pinned over it there: the row a page (or the
 * index strip) scrolled to is the row the user sees first.
 */
private suspend fun LazyListState.showRow(shape: State<ListShape>, index: Int, animated: Boolean) {
    if (animated) {
        // the header over the row is not laid out until the list gets there: aim with the one pinned now, settle after
        val room = if (shape.value.headerOver(index) < 0) 0 else pinnedHeaderSize(shape.value)
        animateScrollToItem(index, -room)
    } else {
        scrollToItem(index)
    }
    // read again: rows may have come or gone while it animated
    val header = shape.value.headerOver(index)
    if (header < 0) return
    val visible = layoutInfo.visibleItemsInfo
    val pinned = visible.firstOrNull { it.index == header } ?: return
    val target = visible.firstOrNull { it.index == index } ?: return
    // under the header by this much, or (aimed with a taller header than its own) short of it by as much
    val off = pinned.offset + pinned.size - target.offset
    if (off != 0) scrollBy(-off.toFloat())
}

/** An index label's circle, the height of the cell it sits in when the strip has all the room it wants, and the strip's width. */
private val IndexLabelSize = 16.dp
private val IndexLabelPitch = 20.dp
private val IndexStripWidth = 40.dp

/**
 * This style at the size it has under the default system font size, whatever that setting is now. The index strip's
 * cells are fixed in dp and there are twenty-odd of them in one column: labels that grew with the font size would be
 * cut by their cells, or would not fit the strip at all. A screen reader does not go by the letters (the strip is one
 * adjustable element), and the list beside it scrolls and searches without the strip.
 */
internal fun TextStyle.unscaled(density: Density): TextStyle = with(density) {
    // sp is dp times the font scale: the dp of the same number, turned back into sp, is the size at scale 1
    val size = if (fontSize.isSp) fontSize.value else 12f
    copy(fontSize = size.dp.toSp(), lineHeight = if (lineHeight.isSp) lineHeight.value.dp.toSp() else lineHeight)
}

/**
 * What an IndexBar knows of its list: the labels it shows, the row each one takes the list to, and which label a row
 * belongs under. The groups are the list's SectionHeaders; one that carries an `index` answers to the label of that
 * value, one without is in no label's group. A label without a group of its own goes to the next label down that has
 * one, the last such group when none follows.
 */
private class IndexModel(
    val labels: List<Pair<String, String>>,
    private val own: IntArray,
    private val targets: IntArray,
    private val groupRows: IntArray,
    private val groupLabels: IntArray,
) {
    /** The row label [label] takes the list to; -1 when the list has no group at all. */
    fun rowOf(label: Int): Int = targets.getOrElse(label) { -1 }

    /** Whether [label] has a group of its own, rather than standing in for the next one down. */
    fun hasGroup(label: Int): Boolean = own.getOrElse(label) { -1 } >= 0

    /** The label of the group row [row] is in; -1 above the first group, or when that group has no label of its own. */
    fun labelAt(row: Int): Int {
        var group = -1
        for (i in groupRows.indices) if (groupRows[i] <= row) group = i else break
        return if (group < 0) -1 else groupLabels[group]
    }
}

private fun indexModel(bar: UINode, rows: List<UINode>): IndexModel {
    val groups = ArrayList<Pair<String?, Int>>()
    for (at in rows.indices) {
        val row = rows[at]
        if (row.type == SECTION_HEADER) groups += (row.props["index"] as? String) to at
    }
    val options = bar.children.filter { it.type == "Option" }.map { (it.props["value"] as? String ?: "") to (it.props["text"] as? String ?: "") }
    val labels = options.ifEmpty { groups.mapNotNull { it.first }.distinct().map { it to it } }
    val own = IntArray(labels.size) { label -> groups.firstOrNull { it.first == labels[label].first }?.second ?: -1 }
    val last = groups.lastOrNull { it.first != null }?.second ?: -1
    val targets = IntArray(labels.size) { label -> (label until labels.size).firstOrNull { own[it] >= 0 }?.let { own[it] } ?: last }
    return IndexModel(
        labels = labels,
        own = own,
        targets = targets,
        groupRows = IntArray(groups.size) { groups[it].second },
        groupLabels = IntArray(groups.size) { group -> groups[group].first?.let { index -> labels.indexOfFirst { it.first == index } } ?: -1 },
    )
}

/**
 * The IndexBar of the list with this [shape], over its end edge. Which cell the finger is on is tracked here and the list scrolled from
 * here: nothing crosses the bridge while it slides (docs/adr-004 §3.1). The highlight is the cell under the finger
 * while it is down, the group the list is at otherwise. Neither Compose nor M3 has this component: the sizes are the
 * framework's, the colours the theme's. Takes no room when its labels do not fit.
 */
@Composable
private fun IndexBarStrip(bar: UINode, shape: State<ListShape>, state: LazyListState, scrolls: CoroutineScope) {
    val model by remember(bar, shape) { derivedStateOf { indexModel(bar, shape.value.rows) } }
    val current = rememberUpdatedState(model)
    var pressed by remember { mutableStateOf<Int?>(null) }
    val at by remember(state) { derivedStateOf { current.value.labelAt(state.firstVisibleItemIndex) } }
    val highlighted = pressed ?: at
    fun go(label: Int) {
        val row = current.value.rowOf(label)
        if (row >= 0) scrolls.launch(start = CoroutineStart.UNDISPATCHED) { state.showRow(shape, row, animated = false) }
    }
    val labels = model.labels
    val name = bar.props["label"] as? String
    val labelStyle = MaterialTheme.typography.bodySmall.unscaled(LocalDensity.current)
    Layout(
        content = {
            for ((index, label) in labels.withIndex()) {
                val on = index == highlighted
                Box(
                    Modifier.size(IndexLabelSize).then(if (on) Modifier.background(MaterialTheme.colorScheme.primary, CircleShape) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label.second,
                        // at its own size, centred over the circle: a line taller than the circle (the host's line
                        // height is its own business) would otherwise be cut to it from the top, and sit low
                        modifier = Modifier.wrapContentSize(unbounded = true),
                        style = labelStyle,
                        color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        },
        modifier = Modifier
            // one adjustable element to a screen reader, not a button per label: it says the group the list is at
            // and steps through them
            .clearAndSetSemantics {
                if (name != null) contentDescription = name
                stateDescription = labels.getOrNull(highlighted)?.second ?: ""
                if (labels.size > 1) {
                    val from = highlighted.coerceAtLeast(0)
                    progressBarRangeInfo = ProgressBarRangeInfo(from.toFloat(), 0f..(labels.size - 1).toFloat(), labels.size - 2)
                    setProgress { value ->
                        // a step lands on a group, not on a label standing in for the next one: stepping back onto
                        // such a label would come straight back to where it started and never get past it
                        val to = value.roundToInt().coerceIn(0, labels.size - 1)
                        val step = if (to < from) -1 else 1
                        var label = to
                        while (label in labels.indices && !current.value.hasGroup(label)) label += step
                        if (label in labels.indices) go(label)
                        true
                    }
                } else if (labels.size == 1) {
                    // nothing to step between; the one label can still be activated
                    onClick { go(0); true }
                }
            }
            // everything is consumed: inside a sheet or a pager a slide along the strip must not drag the container
            .pointerInput(Unit) {
                awaitEachGesture {
                    fun reach(y: Float) {
                        val count = current.value.labels.size
                        if (count == 0 || size.height == 0) return
                        val label = (y / size.height * count).toInt().coerceIn(0, count - 1)
                        if (label != pressed) { pressed = label; go(label) }
                    }
                    val down = awaitFirstDown()
                    down.consume()
                    try {
                        reach(down.position.y)
                        drag(down.id) { change -> change.consume(); reach(change.position.y) }
                    } finally {
                        pressed = null
                    }
                }
            },
    ) { measurables, constraints ->
        val label = IndexLabelSize.roundToPx()
        // labels squeezed into less than their own size cannot be read or hit: no strip then (a keyboard is up, say)
        if (measurables.isEmpty() || constraints.maxHeight < label * measurables.size || constraints.maxWidth < label) return@Layout layout(0, 0) {}
        val placeables = measurables.map { it.measure(Constraints.fixed(label, label)) }
        val width = IndexStripWidth.roundToPx().coerceAtMost(constraints.maxWidth)
        val height = (IndexLabelPitch.roundToPx() * placeables.size).coerceAtMost(constraints.maxHeight)
        val cell = height.toFloat() / placeables.size
        layout(width, height) {
            placeables.forEachIndexed { index, placeable -> placeable.place((width - label) / 2, (index * cell + (cell - label) / 2).toInt()) }
        }
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
                        // unset keeps the icon's own size: the 24dp every packaged icon carries, defined once in parseIcon
                        val size = scope.get<Dp>("backIconSize")
                        Icon(backIcon.vector, contentDescription = scope.get<String>("backLabel"), modifier = if (size != null) Modifier.size(size) else Modifier)
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
 * M3 primary tab row; the tabs are the `Option` children, read as data like [SelectComponent] reads them, and the
 * selection is the page's prop: a tap only reports `onSelect`, the page writes it back to `value`. An unmatched or
 * unset `value` selects no tab and draws no indicator; M3 still wants an index, so the first tab stands in for it.
 */
@Composable
private fun TabsComponent(scope: NodeScope) {
    val options = scope.node.children.filter { it.type == "Option" }.map { (it.props["value"] as? String ?: "") to (it.props["text"] as? String ?: "") }
    val value = scope.get<String>("value")
    val selected = options.indexOfFirst { it.first == value }
    PrimaryTabRow(
        selectedTabIndex = selected.coerceAtLeast(0),
        modifier = scope.modifier(clickable = false),
        indicator = { if (selected >= 0) TabRowDefaults.PrimaryIndicator(Modifier.tabIndicatorOffset(selected, matchContentSize = true), width = Dp.Unspecified) },
    ) {
        for ((index, option) in options.withIndex()) {
            Tab(
                selected = index == selected,
                onClick = { if (scope.has("onSelect")) scope.dispatch("onSelect", payload("value", option.first)) },
                text = { Text(option.second) },
                // M3's inactive label is onSurfaceVariant (primary-navigation-tab tokens); Compose's Tab defaults it to the active color
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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

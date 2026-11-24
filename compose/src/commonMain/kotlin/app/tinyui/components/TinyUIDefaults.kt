package app.tinyui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CheckboxColors
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import kotlin.math.floor

/**
 * Host overrides of M3 component defaults that `MaterialTheme` cannot express (docs/components.md §6). M3 derives some
 * component colours from theme roles rather than naming them — a text field's focused border is `primary` — and a
 * design system may assign them differently; a button's shape does not read `MaterialTheme.shapes` at all. Every field
 * is optional; null keeps the M3 default. Set once around [TinyUIPage] through [LocalTinyUIDefaults], the same way the
 * host sets its `MaterialTheme`; pages never see it, and nothing here is part of the host snapshot — looks only.
 */
class TinyUIDefaults(
    /** The look of every text input — `TextField`, `Select`, and the cells of `CodeField` (their colours). */
    val textField: TextFieldStyle? = null,
    /**
     * The look of a `TextField` or `Select` with `size="small"`: a design system's shorter field (a 40dp search field
     * among 48dp form fields), height and padding together as in MUI's `size` or Material's density. A piece left null
     * is [textField]'s.
     */
    val smallTextField: TextFieldStyle? = null,
    /** The look of `Button` per variant: a design system's primary / secondary / tertiary button. */
    val filledButton: ButtonStyle? = null,
    val outlinedButton: ButtonStyle? = null,
    val textButton: ButtonStyle? = null,
    /** The look of `Checkbox`: its colours and the strokes of its outline and checkmark. */
    val checkbox: CheckboxStyle? = null,
    /**
     * The look of a `[label](id)` link in `Text`, in Compose's own type: colour, weight, decoration per state. Null is the
     * primary colour at the text's own weight. A design system that sets links semi-bold says so here once; pages keep
     * writing the mark.
     */
    val textLinks: TextLinkStyles? = null,
    /** The look of `BottomSheet`: the colour of its container and of the content on it. */
    val bottomSheet: BottomSheetStyle? = null,
    /** The size of `TopAppBar`: the bar's height and its back icon. */
    val topAppBar: TopAppBarStyle? = null,
)

/**
 * What a host may set on a `TopAppBar`: sizes a design system fixes for every page and M3 does not take from the
 * theme. [height] is the bar's own height, the status bar not included (M3: 64dp); [backIconSize] is the glyph of the
 * back button, whose 48dp touch target stays (null: the icon's own size). A page that sets `height` or `backIconSize`
 * itself still wins.
 */
class TopAppBarStyle(
    val height: Dp? = null,
    val backIconSize: Dp? = null,
)

/**
 * What a host may set on a `BottomSheet`, in M3's own types — the parameters of M3's `ModalBottomSheet` that a design
 * system may assign differently from M3. M3 fills a sheet with `surfaceContainerLow`; a design system whose sheets sit
 * on another role says so here rather than changing what that role means everywhere else. Null keeps M3's default for
 * that piece; [contentColor] null is M3's content colour for the container in use, so setting [containerColor] to a
 * scheme role is usually enough.
 *
 * [maxHeightFraction] caps a sheet at that share of the window's height, in (0, 1]. M3 has no such parameter: a sheet is
 * as tall as its content, up to the top of the screen, so content that fills (`height="fill"`, a long list) covers the
 * whole page and leaves no scrim to tap. A design system whose full sheets stop short of the top (90% of the screen, say)
 * sets it here; a sheet shorter than the cap is untouched. Null keeps M3's.
 *
 * [dragHandle] false leaves out M3's drag handle (the short bar at the top), for a design system whose sheets have none;
 * the sheet still closes by swiping down, tapping the scrim or going back. Null or true keeps M3's.
 */
class BottomSheetStyle(
    val containerColor: Color? = null,
    val contentColor: Color? = null,
    val maxHeightFraction: Float? = null,
    val dragHandle: Boolean? = null,
) {
    init {
        require(maxHeightFraction == null || (maxHeightFraction > 0f && maxHeightFraction <= 1f)) {
            "maxHeightFraction is a share of the window's height, in (0, 1]: $maxHeightFraction"
        }
    }
}

/**
 * What a host may set on a text input, in M3's own types — the parameters of M3's `OutlinedTextField` that
 * `MaterialTheme` does not drive. Null keeps M3's default for that piece.
 *
 * [colors] also colour the cells of `CodeField` (their focused / unfocused / error indicator colours); build them inside
 * the host's `MaterialTheme`, as with any M3 colours, so they follow the scheme. [contentPadding] and [minHeight]
 * together make a denser field: M3's 56dp is 16dp above and below a 24dp line of text, so 12dp and 48dp is M3's
 * compact density — the height of a field whose label sits outside the box. `supportingText` hangs below the field and
 * is not part of [minHeight].
 */
class TextFieldStyle(
    val colors: TextFieldColors? = null,
    val contentPadding: PaddingValues? = null,
    val minHeight: Dp? = null,
)

/**
 * What a host may set on a `Checkbox`, in M3's own types — the parameters of M3's `Checkbox` that `MaterialTheme` does
 * not drive. Null keeps M3's default for that piece. The strokes are in pixels, as M3 takes them: build them inside a
 * composable with `LocalDensity`, e.g. `Stroke(with(LocalDensity.current) { 1.dp.toPx() })`. The box itself has no size
 * parameter in M3 (18dp with `ComposeMaterial3Flags.isCheckboxStylingFixEnabled`, else the older 20dp), so none here.
 */
class CheckboxStyle(
    val colors: CheckboxColors? = null,
    val outlineStroke: Stroke? = null,
    val checkmarkStroke: Stroke? = null,
)

/**
 * What a host may set on a `Button` variant, in M3's own types — the parameters of M3's `Button` / `OutlinedButton` /
 * `TextButton` that `MaterialTheme` does not drive. Null keeps M3's default for that piece; [disabledBorder] null keeps
 * M3's disabled border (an outlined button's outline at 12%), not [border]. [minHeight] is the least a button of this
 * variant is tall (M3: 40dp), the counterpart of [TextFieldStyle.minHeight] for a design system whose buttons and
 * fields share a height; a page that sets `height` still wins.
 */
class ButtonStyle(
    val colors: ButtonColors? = null,
    val border: BorderStroke? = null,
    val disabledBorder: BorderStroke? = null,
    val shape: Shape? = null,
    val contentPadding: PaddingValues? = null,
    val minHeight: Dp? = null,
)

/** The host's [TinyUIDefaults]; the M3 defaults when the host sets none. */
val LocalTinyUIDefaults = compositionLocalOf { TinyUIDefaults() }

/** A text input's look with every piece settled: the host's where set, else M3's outlined defaults. */
internal class ResolvedTextFieldStyle(val colors: TextFieldColors, val contentPadding: PaddingValues, val minHeight: Dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun textFieldStyle(size: String? = null): ResolvedTextFieldStyle {
    val defaults = LocalTinyUIDefaults.current
    val style = defaults.textField
    // the small field's pieces where the host set them, else the regular field's
    val small = if (size == "small") defaults.smallTextField else null
    return ResolvedTextFieldStyle(
        colors = small?.colors ?: style?.colors ?: OutlinedTextFieldDefaults.colors(),
        contentPadding = small?.contentPadding ?: style?.contentPadding ?: OutlinedTextFieldDefaults.contentPaddingWithoutLabel(),
        minHeight = small?.minHeight ?: style?.minHeight ?: OutlinedTextFieldDefaults.MinHeight,
    )
}

/** A `Checkbox`'s look with every piece settled: the host's where set, else M3's (the strokes M3's plain `Checkbox` builds). */
internal class ResolvedCheckboxStyle(val colors: CheckboxColors, val outlineStroke: Stroke, val checkmarkStroke: Stroke)

@Composable
internal fun checkboxStyle(): ResolvedCheckboxStyle {
    val style = LocalTinyUIDefaults.current.checkbox
    val m3Width = with(LocalDensity.current) { floor(CheckboxDefaults.StrokeWidth.toPx()) }
    return ResolvedCheckboxStyle(
        colors = style?.colors ?: CheckboxDefaults.colors(),
        outlineStroke = style?.outlineStroke ?: Stroke(width = m3Width),
        checkmarkStroke = style?.checkmarkStroke ?: Stroke(width = m3Width, cap = StrokeCap.Square),
    )
}

/** A `Button` variant's look with every piece settled: the host's where set, else M3's for that variant. */
internal class ResolvedButtonStyle(
    val colors: ButtonColors,
    val border: BorderStroke?,
    val shape: Shape,
    val contentPadding: PaddingValues,
    /** Null leaves the minimum to M3. */
    val minHeight: Dp?,
)

@Composable
internal fun buttonStyle(variant: String?, enabled: Boolean): ResolvedButtonStyle {
    val defaults = LocalTinyUIDefaults.current
    return when (variant) {
        "outlined" -> defaults.outlinedButton.resolve(
            enabled, ButtonDefaults.outlinedButtonColors(), ButtonDefaults.outlinedButtonBorder(enabled), ButtonDefaults.outlinedShape, ButtonDefaults.ContentPadding,
        )
        "text" -> defaults.textButton.resolve(enabled, ButtonDefaults.textButtonColors(), null, ButtonDefaults.textShape, ButtonDefaults.TextButtonContentPadding)
        else -> defaults.filledButton.resolve(enabled, ButtonDefaults.buttonColors(), null, ButtonDefaults.shape, ButtonDefaults.ContentPadding)
    }
}

private fun ButtonStyle?.resolve(enabled: Boolean, colors: ButtonColors, border: BorderStroke?, shape: Shape, contentPadding: PaddingValues) = ResolvedButtonStyle(
    colors = this?.colors ?: colors,
    border = (if (enabled) this?.border else this?.disabledBorder) ?: border,
    shape = this?.shape ?: shape,
    contentPadding = this?.contentPadding ?: contentPadding,
    minHeight = this?.minHeight,
)

/** A `BottomSheet`'s look with every piece settled: the host's where set, else M3's. */
internal class ResolvedBottomSheetStyle(val containerColor: Color, val contentColor: Color, val maxHeightFraction: Float?, val dragHandle: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun bottomSheetStyle(): ResolvedBottomSheetStyle {
    val style = LocalTinyUIDefaults.current.bottomSheet
    val container = style?.containerColor ?: BottomSheetDefaults.ContainerColor
    return ResolvedBottomSheetStyle(
        containerColor = container,
        contentColor = style?.contentColor ?: contentColorFor(container),
        maxHeightFraction = style?.maxHeightFraction,
        dragHandle = style?.dragHandle ?: true,
    )
}

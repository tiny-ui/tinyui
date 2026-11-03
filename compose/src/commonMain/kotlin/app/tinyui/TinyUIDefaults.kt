package app.tinyui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp

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
    /** The look of `Button` per variant: a design system's primary / secondary / tertiary button. */
    val filledButton: ButtonStyle? = null,
    val outlinedButton: ButtonStyle? = null,
    val textButton: ButtonStyle? = null,
)

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
 * What a host may set on a `Button` variant, in M3's own types — the parameters of M3's `Button` / `OutlinedButton` /
 * `TextButton` that `MaterialTheme` does not drive. Null keeps M3's default for that piece; [disabledBorder] null keeps
 * M3's disabled border (an outlined button's outline at 12%), not [border].
 */
class ButtonStyle(
    val colors: ButtonColors? = null,
    val border: BorderStroke? = null,
    val disabledBorder: BorderStroke? = null,
    val shape: Shape? = null,
    val contentPadding: PaddingValues? = null,
)

/** The host's [TinyUIDefaults]; the M3 defaults when the host sets none. */
val LocalTinyUIDefaults = compositionLocalOf { TinyUIDefaults() }

/** A text input's look with every piece settled: the host's where set, else M3's outlined defaults. */
internal class ResolvedTextFieldStyle(val colors: TextFieldColors, val contentPadding: PaddingValues, val minHeight: Dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun textFieldStyle(): ResolvedTextFieldStyle {
    val style = LocalTinyUIDefaults.current.textField
    return ResolvedTextFieldStyle(
        colors = style?.colors ?: OutlinedTextFieldDefaults.colors(),
        contentPadding = style?.contentPadding ?: OutlinedTextFieldDefaults.contentPaddingWithoutLabel(),
        minHeight = style?.minHeight ?: OutlinedTextFieldDefaults.MinHeight,
    )
}

/** A `Button` variant's look with every piece settled: the host's where set, else M3's for that variant. */
internal class ResolvedButtonStyle(val colors: ButtonColors, val border: BorderStroke?, val shape: Shape, val contentPadding: PaddingValues)

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
)

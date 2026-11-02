package app.tinyui

import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

/**
 * Host overrides of M3 component defaults that `MaterialTheme` cannot express (docs/components.md §6). M3 derives some
 * component colours from theme roles rather than naming them — a text field's focused border is `primary` — and a
 * design system may assign them differently. Every field is optional; null keeps the M3 default. Set once around
 * [TinyUIPage] through [LocalTinyUIDefaults], the same way the host sets its `MaterialTheme`; pages never see it.
 */
class TinyUIDefaults(
    /**
     * Colours of every text input — `TextField`, `Select`, and the cells of `CodeField` (its focused / unfocused / error
     * indicator colours). Composable because the colours are built from `MaterialTheme`.
     */
    val textFieldColors: (@Composable () -> TextFieldColors)? = null,
)

/** The host's [TinyUIDefaults]; the M3 defaults when the host sets none. */
val LocalTinyUIDefaults = compositionLocalOf { TinyUIDefaults() }

/** The text input colours the host chose, else M3's outlined defaults. */
@Composable
internal fun textFieldColors(): TextFieldColors = LocalTinyUIDefaults.current.textFieldColors?.invoke() ?: OutlinedTextFieldDefaults.colors()

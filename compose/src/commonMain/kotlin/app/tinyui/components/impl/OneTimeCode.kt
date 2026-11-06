package app.tinyui.components.impl

import androidx.compose.ui.text.input.PlatformImeOptions

/**
 * iOS reads the text content type only from here, never from semantics, and otherwise derives it from the keyboard:
 * NumberPassword would make a code field a password field. Null where the platform has nothing to say (Android).
 */
internal expect fun oneTimeCodeImeOptions(oneTimeCode: Boolean): PlatformImeOptions?

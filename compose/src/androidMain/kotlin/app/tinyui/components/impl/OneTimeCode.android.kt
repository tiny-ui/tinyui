package app.tinyui.components.impl

import androidx.compose.ui.text.input.PlatformImeOptions

// Android autofill takes the content type from semantics (the component sets it), so nothing goes through the IME options
internal actual fun oneTimeCodeImeOptions(oneTimeCode: Boolean): PlatformImeOptions? = null

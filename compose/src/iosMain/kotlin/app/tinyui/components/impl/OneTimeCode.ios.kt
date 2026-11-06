package app.tinyui.components.impl

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.text.input.PlatformImeOptions
import platform.UIKit.UITextContentTypeOneTimeCode

// explicit either way: unset, NumberPassword would be announced as a password and offered keychain entries
@OptIn(ExperimentalComposeUiApi::class)
internal actual fun oneTimeCodeImeOptions(oneTimeCode: Boolean): PlatformImeOptions? =
    PlatformImeOptions { textContentType(if (oneTimeCode) UITextContentTypeOneTimeCode else null) }

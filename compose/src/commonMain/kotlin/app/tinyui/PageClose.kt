package app.tinyui

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Closes the page being rendered, as `navigation.pop()` would; null outside a [TinyUIPage]. The `TopAppBar` back button
 * falls back to it when the host has no back dispatcher (a test, a host with its own navigation): with one, the button
 * goes through the dispatcher like system back (docs/components.md §3).
 */
internal val LocalClosePage = staticCompositionLocalOf<(() -> Unit)?> { null }

package app.tinyui

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Where back goes on one page when it does not come from the system: the `TopAppBar` back button. The last enabled
 * `BackHandler` gets it, as the system dispatcher would give it system back; with none, the page closes, which is what
 * the host's navigation does with system back (docs/components.md §3).
 */
internal class PageBack(private val close: () -> Unit) {
    /** One mounted `BackHandler`; its fields follow the latest props. */
    class Handler(var enabled: Boolean = true, var onBack: () -> Unit = {})

    // mount order: a handler mounted later is composed later, and the last composed wins there too
    private val handlers = ArrayList<Handler>()

    fun add(handler: Handler) { handlers += handler }
    fun remove(handler: Handler) { handlers -= handler }

    fun back() {
        val handler = handlers.lastOrNull { it.enabled }
        if (handler != null) handler.onBack() else close()
    }
}

/** The page being rendered; null outside a [TinyUIPage] (a component rendered on its own in a test). */
internal val LocalPageBack = staticCompositionLocalOf<PageBack?> { null }

package app.tinyui.host

import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.UriHandler
import app.tinyui.internal.plainTextClipEntry
import kotlin.concurrent.Volatile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The mount a call came from: the page's module name and the objects its `locals` provide. */
class PageContext internal constructor(val name: String, @Volatile internal var locals: Map<PageLocal<*>, Any>) {
    internal var uriHandler: UriHandler? = null
    internal var clipboard: Clipboard? = null

    @Suppress("UNCHECKED_CAST")
    operator fun <T : Any> get(local: PageLocal<T>): T? = locals[local] as T?

    internal suspend fun openWithSystem(url: String) {
        val handler = uriHandler ?: throw HostException("E_UNSUPPORTED", "the page is not on screen")
        withContext(Dispatchers.Main) {
            try {
                handler.openUri(url)
            } catch (e: IllegalArgumentException) {
                throw HostException("E_UNSUPPORTED", e.message ?: "nothing can open $url")
            }
        }
    }

    internal suspend fun copy(text: String) {
        val target = clipboard ?: throw HostException("E_UNSUPPORTED", "the page is not on screen")
        withContext(Dispatchers.Main) { target.setClipEntry(plainTextClipEntry(text)) }
    }
}

/** A typed key for an object one mount hands to capabilities, such as its screen's view model. */
class PageLocal<T : Any> {
    infix fun provides(value: T): PageLocalValue<T> = PageLocalValue(this, value)
}

class PageLocalValue<T : Any> internal constructor(val local: PageLocal<T>, val value: T)

package app.tinyui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** `ui.alert` / `ui.confirm`; [cancel] is null for an alert. */
internal class DialogRequest(val title: String?, val message: String, val confirm: String, val cancel: String?) {
    val result = CompletableDeferred<Boolean>()
}

/** The page's own snackbar and dialog (docs/native-api.md §10), drawn by [TinyUIPage] over the page. */
internal class PageUi {
    val snackbar = SnackbarHostState()
    var dialog: DialogRequest? by mutableStateOf(null)
        private set

    private val queue = Mutex()

    /** Shows [request] after any dialog already up; true when confirmed. */
    suspend fun ask(request: DialogRequest): Boolean = queue.withLock {
        dialog = request
        try {
            request.result.await()
        } finally {
            dialog = null
        }
    }

    @Composable
    fun Overlay(modifier: Modifier) {
        SnackbarHost(snackbar, modifier)
        dialog?.let { d ->
            AlertDialog(
                onDismissRequest = { d.result.complete(false) },
                title = d.title?.let { { Text(it) } },
                text = { Text(d.message) },
                confirmButton = { TextButton(onClick = { d.result.complete(true) }) { Text(d.confirm) } },
                dismissButton = d.cancel?.let { { TextButton(onClick = { d.result.complete(false) }) { Text(it) } } },
            )
        }
    }
}

/** The framework's own button labels when a page gives none. */
internal class DialogTexts(val ok: String, val cancel: String) {
    companion object {
        fun of(locale: String): DialogTexts = if (locale.lowercase().startsWith("zh")) DialogTexts("确定", "取消") else DialogTexts("OK", "Cancel")
    }
}

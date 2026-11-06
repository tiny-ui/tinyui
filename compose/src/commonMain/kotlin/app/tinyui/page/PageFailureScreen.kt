package app.tinyui.page

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.tinyui.TinyUI

/** The default failure screen: kind and message, plus the stack when [TinyUI.debug] is on. */
@Composable
fun PageFailureScreen(failure: PageFailure) {
    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("TinyUI page failed (${failure.kind})", style = MaterialTheme.typography.titleMedium)
        Text(failure.message, style = MaterialTheme.typography.bodyMedium)
        if (TinyUI.debug) {
            for (frame in failure.error.frames) Text(frame.toString(), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
    }
}

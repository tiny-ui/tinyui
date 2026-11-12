package app.tinyui.components.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.retain.retain
import androidx.compose.ui.focus.FocusRequester
import app.tinyui.components.NodeScope

/**
 * `autoFocus` of the input components: asks for focus once, when the field first appears. The flag is retained with
 * what the user typed, so a page coming back from the back stack does not raise the keyboard again.
 */
@Composable
internal fun AutoFocus(scope: NodeScope, focusRequester: FocusRequester) {
    val asked = retain { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (asked.value || scope.get<Boolean>("autoFocus") != true) return@LaunchedEffect
        asked.value = true
        focusRequester.requestFocus()
    }
}

package app.tinyui.components.impl

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.tinyui.components.NodeScope
import app.tinyui.components.bottomSheetStyle

/**
 * `open` is the page's; going false animates the sheet out before it leaves composition. A user dismissal (swipe, scrim,
 * back) has already hidden it when `onDismiss` fires: it stays down until `open` goes false and true again. Its colours
 * are the host's where set (`TinyUIDefaults.bottomSheet`), else M3's.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BottomSheetComponent(scope: NodeScope) {
    val open = scope.get<Boolean>("open") == true
    var showing by remember { mutableStateOf(false) }
    // content-sized, never parked half way
    val state = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    LaunchedEffect(open) {
        when {
            // reopened while the hide was still animating: still composed, so bring it back up
            open && showing -> state.show()
            open -> showing = true
            showing -> { state.hide(); showing = false }
        }
    }
    if (showing) {
        val style = bottomSheetStyle()
        ModalBottomSheet(
            onDismissRequest = { showing = false; if (scope.has("onDismiss")) scope.dispatch("onDismiss") },
            sheetState = state,
            containerColor = style.containerColor,
            contentColor = style.contentColor,
        ) { scope.Children() }
    }
}

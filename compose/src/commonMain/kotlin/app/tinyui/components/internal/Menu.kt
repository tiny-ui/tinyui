package app.tinyui.components.internal

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.tinyui.schema.NodeScope

/**
 * M3 dropdown menu anchored to the parent node, like [PopoverComponent]; the items are the `Option` children, read as
 * data. `open` is the page's, but a pick or a tap outside closes it here at once and is then reported, as
 * [BottomSheetComponent] does: it stays closed until `open` goes false and true again.
 */
@Composable
internal fun MenuComponent(scope: NodeScope) {
    val open = scope.get<Boolean>("open") == true
    var showing by remember { mutableStateOf(false) }
    LaunchedEffect(open) { showing = open }
    val options = scope.node.options()
    DropdownMenu(expanded = showing, onDismissRequest = { showing = false; if (scope.has("onDismiss")) scope.dispatch("onDismiss") }) {
        for ((value, text) in options) {
            DropdownMenuItem(text = { Text(text) }, onClick = { showing = false; if (scope.has("onSelect")) scope.dispatch("onSelect", payload("value", value)) })
        }
    }
}

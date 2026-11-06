package app.tinyui.components.internal

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import app.tinyui.schema.NodeScope

/**
 * M3 primary tab row; the tabs are the `Option` children, read as data like [SelectComponent] reads them, and the
 * selection is the page's prop: a tap only reports `onSelect`, the page writes it back to `value`. An unmatched or
 * unset `value` selects no tab and draws no indicator; M3 still wants an index, so the first tab stands in for it.
 */
@Composable
internal fun TabsComponent(scope: NodeScope) {
    val options = scope.node.children.filter { it.type == "Option" }.map { (it.props["value"] as? String ?: "") to (it.props["text"] as? String ?: "") }
    val value = scope.get<String>("value")
    val selected = options.indexOfFirst { it.first == value }
    PrimaryTabRow(
        selectedTabIndex = selected.coerceAtLeast(0),
        modifier = scope.modifier(clickable = false),
        indicator = { if (selected >= 0) TabRowDefaults.PrimaryIndicator(Modifier.tabIndicatorOffset(selected, matchContentSize = true), width = Dp.Unspecified) },
    ) {
        for ((index, option) in options.withIndex()) {
            Tab(
                selected = index == selected,
                onClick = { if (scope.has("onSelect")) scope.dispatch("onSelect", payload("value", option.first)) },
                text = { Text(option.second) },
                // M3's inactive label is onSurfaceVariant (primary-navigation-tab tokens); Compose's Tab defaults it to the active color
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

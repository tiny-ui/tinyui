package app.tinyui.components.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import app.tinyui.schema.NodeScope

internal const val SECTION_HEADER = "SectionHeader"

/** In a LazyColumn the list pins it (see [LazyColumnComponent]); on its own it is a Box. */
@Composable
internal fun SectionHeaderComponent(scope: NodeScope) {
    Box(modifier = scope.modifier(), contentAlignment = boxAlignment(scope["align"])) { scope.Children() }
}

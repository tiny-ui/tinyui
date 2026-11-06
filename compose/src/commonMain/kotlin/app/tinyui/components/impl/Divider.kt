package app.tinyui.components.impl

import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import app.tinyui.components.NodeScope

@Composable
internal fun DividerComponent(scope: NodeScope) {
    HorizontalDivider(
        modifier = scope.modifier(),
        thickness = scope.get<Dp>("thickness") ?: DividerDefaults.Thickness,
        color = scope.color("color") ?: DividerDefaults.color,
    )
}

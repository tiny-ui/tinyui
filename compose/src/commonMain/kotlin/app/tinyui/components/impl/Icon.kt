package app.tinyui.components.impl

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tinyui.schema.IconValue
import app.tinyui.schema.NodeScope

@Composable
internal fun IconComponent(scope: NodeScope) {
    val icon = scope.get<IconValue>("icon") ?: return
    Icon(icon.vector, contentDescription = scope.get<String>("label"), tint = scope.color("tint") ?: LocalContentColor.current, modifier = scope.modifier().size(scope.get<Dp>("size") ?: 24.dp))
}

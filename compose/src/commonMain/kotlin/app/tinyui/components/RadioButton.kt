package app.tinyui.components

import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import app.tinyui.schema.NodeScope

@Composable
internal fun RadioButtonComponent(scope: NodeScope) {
    RadioButton(
        selected = scope.get<Boolean>("selected") == true,
        onClick = if (scope.has("onClick")) ({ scope.dispatch("onClick") }) else null,
        enabled = scope.get<Boolean>("enabled") ?: true,
        modifier = scope.modifier(clickable = false),
    )
}

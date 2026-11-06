package app.tinyui.components.impl

import androidx.compose.material3.Checkbox
import androidx.compose.runtime.Composable
import app.tinyui.components.NodeScope
import app.tinyui.host.checkboxStyle

@Composable
internal fun CheckboxComponent(scope: NodeScope) {
    // the stroke overload with M3's own strokes draws exactly what the plain one does; the host may replace either
    val style = checkboxStyle()
    Checkbox(
        checked = scope.get<Boolean>("checked") == true,
        onCheckedChange = if (scope.has("onClick")) ({ scope.dispatch("onClick") }) else null,
        checkmarkStroke = style.checkmarkStroke,
        outlineStroke = style.outlineStroke,
        enabled = scope.get<Boolean>("enabled") ?: true,
        colors = style.colors,
        modifier = scope.modifier(clickable = false),
    )
}

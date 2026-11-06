package app.tinyui.components.impl

import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import app.tinyui.host.buttonStyle
import app.tinyui.schema.NodeScope

@Composable
internal fun ButtonComponent(scope: NodeScope) {
    val onClick = { if (scope.has("onClick")) scope.dispatch("onClick") }
    val enabled = scope.get<Boolean>("enabled") ?: true
    val label: @Composable () -> Unit = { if (scope.node.children.isEmpty()) Text(scope["text"] ?: "") else scope.Children() }
    val modifier = scope.modifier(clickable = false) // the button owns the click and its enabled state
    val variant = scope.get<String>("variant")
    // the host's look for this variant where it set one, else M3's (TinyUIDefaults)
    val style = buttonStyle(variant, enabled)
    when (variant) {
        "outlined" -> OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier, shape = style.shape, colors = style.colors, border = style.border, contentPadding = style.contentPadding, content = { label() })
        "text" -> TextButton(onClick = onClick, enabled = enabled, modifier = modifier, shape = style.shape, colors = style.colors, border = style.border, contentPadding = style.contentPadding, content = { label() })
        else -> Button(onClick = onClick, enabled = enabled, modifier = modifier, shape = style.shape, colors = style.colors, border = style.border, contentPadding = style.contentPadding, content = { label() })
    }
}

package app.tinyui.components.impl

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.tinyui.components.NodeScope
import app.tinyui.components.buttonStyle

@Composable
internal fun ButtonComponent(scope: NodeScope) {
    val onClick = { if (scope.has("onClick")) scope.dispatch("onClick") }
    val enabled = scope.get<Boolean>("enabled") ?: true
    val label: @Composable () -> Unit = { if (scope.node.children.isEmpty()) Text(scope["text"] ?: "") else scope.Children() }
    val variant = scope.get<String>("variant")
    // the host's look for this variant where it set one, else M3's (TinyUIDefaults)
    val style = buttonStyle(variant, enabled)
    // the button owns the click and its enabled state; the host's minimum sits inside the page's own height, which wins
    val modifier = scope.modifier(clickable = false).then(style.minHeight?.let { Modifier.heightIn(min = it) } ?: Modifier)
    when (variant) {
        "outlined" -> OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier, shape = style.shape, colors = style.colors, border = style.border, contentPadding = style.contentPadding, content = { label() })
        "text" -> TextButton(onClick = onClick, enabled = enabled, modifier = modifier, shape = style.shape, colors = style.colors, border = style.border, contentPadding = style.contentPadding, content = { label() })
        else -> Button(onClick = onClick, enabled = enabled, modifier = modifier, shape = style.shape, colors = style.colors, border = style.border, contentPadding = style.contentPadding, content = { label() })
    }
}

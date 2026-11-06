package app.tinyui.components.internal

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import app.tinyui.schema.NodeScope

@Composable
internal fun BoxComponent(scope: NodeScope) {
    Box(modifier = scope.modifier(), contentAlignment = boxAlignment(scope["align"])) { scope.Children() }
}

internal fun boxAlignment(name: String?): Alignment = when (name) {
    "topCenter" -> Alignment.TopCenter; "topEnd" -> Alignment.TopEnd
    "centerStart" -> Alignment.CenterStart; "center" -> Alignment.Center; "centerEnd" -> Alignment.CenterEnd
    "bottomStart" -> Alignment.BottomStart; "bottomCenter" -> Alignment.BottomCenter; "bottomEnd" -> Alignment.BottomEnd
    else -> Alignment.TopStart
}

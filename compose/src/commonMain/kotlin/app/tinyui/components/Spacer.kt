package app.tinyui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import app.tinyui.schema.NodeScope

@Composable
internal fun SpacerComponent(scope: NodeScope) {
    Spacer(scope.modifier())
}

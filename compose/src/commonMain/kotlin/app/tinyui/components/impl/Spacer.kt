package app.tinyui.components.impl

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import app.tinyui.components.NodeScope

@Composable
internal fun SpacerComponent(scope: NodeScope) {
    Spacer(scope.modifier())
}

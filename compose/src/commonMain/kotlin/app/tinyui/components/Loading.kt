package app.tinyui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LoadingIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tinyui.schema.NodeScope

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun LoadingComponent(scope: NodeScope) {
    LoadingIndicator(
        modifier = scope.modifier().size(scope.get<Dp>("size") ?: 24.dp),
        color = scope.color("color") ?: LoadingIndicatorDefaults.indicatorColor,
    )
}

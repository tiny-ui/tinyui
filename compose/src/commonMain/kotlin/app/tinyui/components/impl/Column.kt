package app.tinyui.components.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.tinyui.components.ComponentRegistry
import app.tinyui.components.NodeScope

@Composable
internal fun ComponentRegistry.ColumnComponent(scope: NodeScope) {
    val scroll = scope.get<Boolean>("scroll") == true
    val gap = gapBefore(scope)
    val stretch = stretchOf(scope, cross = "width")
    Column(
        modifier = scope.modifier().then(if (scroll) Modifier.verticalScroll(retainScrollState()) else Modifier)
            .then(if (stretch == Stretch.Intrinsic) Modifier.width(IntrinsicSize.Max) else Modifier),
        verticalArrangement = verticalArrangement(scope["justify"]),
        horizontalAlignment = when (scope.get<String>("align")) { "center" -> Alignment.CenterHorizontally; "end" -> Alignment.End; else -> Alignment.Start },
    ) {
        scope.Children { child ->
            val weight = if (scroll) Modifier else weightOf(child)?.let { Modifier.weight(it) } ?: Modifier
            val stretched = if (stretch != Stretch.None && stretches(child, "width")) weight.fillMaxWidth() else weight
            gap(child)?.let { stretched.padding(top = it) } ?: stretched
        }
    }
}

private fun verticalArrangement(justify: String?): Arrangement.Vertical = when (justify) {
    "center" -> Arrangement.Center
    "end" -> Arrangement.Bottom
    "spaceBetween" -> Arrangement.SpaceBetween
    else -> Arrangement.Top
}

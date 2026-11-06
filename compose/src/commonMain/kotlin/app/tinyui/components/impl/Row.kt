package app.tinyui.components.impl

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.tinyui.schema.ComponentRegistry
import app.tinyui.schema.NodeScope

@Composable
internal fun ComponentRegistry.RowComponent(scope: NodeScope) {
    val scroll = scope.get<Boolean>("scroll") == true
    val gap = gapBefore(scope)
    val stretch = stretchOf(scope, cross = "height")
    Row(
        modifier = scope.modifier().then(if (scroll) Modifier.horizontalScroll(retainScrollState()) else Modifier)
            .then(if (stretch == Stretch.Intrinsic) Modifier.height(IntrinsicSize.Min) else Modifier),
        horizontalArrangement = horizontalArrangement(scope["justify"]),
        verticalAlignment = when (scope.get<String>("align")) { "center" -> Alignment.CenterVertically; "end" -> Alignment.Bottom; else -> Alignment.Top },
    ) {
        scope.Children { child ->
            val weight = if (scroll) Modifier else weightOf(child)?.let { Modifier.weight(it) } ?: Modifier
            val stretched = if (stretch != Stretch.None && stretches(child, "height")) weight.fillMaxHeight() else weight
            gap(child)?.let { stretched.padding(start = it) } ?: stretched
        }
    }
}

private fun horizontalArrangement(justify: String?): Arrangement.Horizontal = when (justify) {
    "center" -> Arrangement.Center
    "end" -> Arrangement.End
    "spaceBetween" -> Arrangement.SpaceBetween
    else -> Arrangement.Start
}

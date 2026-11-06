package app.tinyui.components.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import app.tinyui.components.IconValue
import app.tinyui.components.NodeScope
import app.tinyui.components.SizeValue

/**
 * M3 small top app bar under the status bar; the children are the actions. The status bar padding sits outside the
 * common modifier, so `height` sizes the bar itself; inset padding consumed by the host already (a Scaffold) is not
 * added again. The back button injects a back into the system dispatcher, as a toolbar calls
 * `onBackPressedDispatcher.onBackPressed()`: whatever would take system back takes it (a `BackHandler`, the host's
 * navigation). Without a dispatcher there is no system back, so it does nothing, like a `BackHandler`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TopAppBarComponent(scope: NodeScope) {
    val container = scope.color("background") ?: MaterialTheme.colorScheme.surface
    val backIcon = scope.get<IconValue>("backIcon")
    val dispatcher = LocalNavigationEventDispatcherOwner.current?.navigationEventDispatcher
    val input = remember { DirectNavigationEventInput() }
    if (dispatcher != null) {
        DisposableEffect(dispatcher, input) {
            dispatcher.addInput(input)
            onDispose { dispatcher.removeInput(input) }
        }
    }
    Box(Modifier.background(container).windowInsetsPadding(TopAppBarDefaults.windowInsets)) {
        TopAppBar(
            title = { scope.get<String>("title")?.takeIf { it.isNotEmpty() }?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
            navigationIcon = {
                if (backIcon != null) {
                    IconButton(onClick = { if (dispatcher != null) input.backCompleted() }) {
                        // unset keeps the icon's own size: the 24dp every packaged icon carries, defined once in parseIcon
                        val size = scope.get<Dp>("backIconSize")
                        Icon(backIcon.vector, contentDescription = scope.get<String>("backLabel"), modifier = if (size != null) Modifier.size(size) else Modifier)
                    }
                }
            },
            actions = { scope.Children() },
            // the insets are padded above, outside the height the page gives
            windowInsets = WindowInsets(0),
            expandedHeight = (scope.get<SizeValue>("height") as? SizeValue.Fixed)?.dp ?: TopAppBarDefaults.TopAppBarExpandedHeight,
            colors = TopAppBarDefaults.topAppBarColors(containerColor = container),
            modifier = scope.modifier(),
        )
    }
}

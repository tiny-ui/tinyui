package app.tinyui.components

import androidx.compose.runtime.Composable
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import app.tinyui.schema.NodeScope

/** Takes back from the system dispatcher while enabled, which is also where the `TopAppBar` button sends it. */
@Composable
internal fun BackHandlerComponent(scope: NodeScope) {
    // without a dispatcher there is no system back to take, from the gesture or the top bar button
    if (LocalNavigationEventDispatcherOwner.current == null) return
    val enabled = (scope.get<Boolean>("enabled") ?: true) && scope.has("onBack")
    NavigationBackHandler(state = rememberNavigationEventState(NavigationEventInfo.None), isBackEnabled = enabled, onBackCompleted = { scope.dispatch("onBack") })
}

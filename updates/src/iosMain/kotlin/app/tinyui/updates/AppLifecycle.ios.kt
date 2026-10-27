package app.tinyui.updates

import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationState

internal actual fun observeAppLifecycle(onForeground: () -> Unit, onBackground: () -> Unit): () -> Unit {
    val center = NSNotificationCenter.defaultCenter
    val main = NSOperationQueue.mainQueue
    val tokens = listOf(
        center.addObserverForName(UIApplicationDidBecomeActiveNotification, null, main) { _ -> onForeground() },
        center.addObserverForName(UIApplicationDidEnterBackgroundNotification, null, main) { _ -> onBackground() },
    )
    // observing after the app became active: that notification is gone
    main.addOperationWithBlock {
        if (UIApplication.sharedApplication.applicationState == UIApplicationState.UIApplicationStateActive) onForeground()
    }
    return { tokens.forEach(center::removeObserver) }
}

package app.tinyui.updates

import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationState

actual fun UpdatesScheduler.attachToAppLifecycle() {
    val center = NSNotificationCenter.defaultCenter
    val main = NSOperationQueue.mainQueue
    center.addObserverForName(UIApplicationDidBecomeActiveNotification, null, main) { _ -> onForeground() }
    center.addObserverForName(UIApplicationDidEnterBackgroundNotification, null, main) { _ -> onBackground() }
    // attached after the app became active: that notification is gone
    main.addOperationWithBlock {
        if (UIApplication.sharedApplication.applicationState == UIApplicationState.UIApplicationStateActive) onForeground()
    }
}

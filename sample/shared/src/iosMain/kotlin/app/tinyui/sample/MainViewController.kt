package app.tinyui.sample

import androidx.compose.ui.window.ComposeUIViewController
import okio.Path.Companion.toPath
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    val support = NSFileManager.defaultManager.URLsForDirectory(NSApplicationSupportDirectory, NSUserDomainMask).first() as NSURL
    val dir = support.path!!.toPath() / "tinyui"
    return ComposeUIViewController { App(updatesDir = dir) }
}

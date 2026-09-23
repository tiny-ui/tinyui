package app.tinyui

import wang.harlon.quickjs.QuickJs

object TinyUI {
    /** This library's version: the tinyui-core version it ships with, also in a source build. */
    val version: String get() = VERSION

    /** Version of the quickjs-kmp SDK this build links against. */
    val engineVersion: String get() = QuickJs.sdkVersion

    /** Debug builds show the (mapped) JS stack on the page-failure screen; keep false in release. */
    var debug: Boolean = false
}

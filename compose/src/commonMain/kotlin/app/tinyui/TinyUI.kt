package app.tinyui

import app.tinyui.generated.RuntimeBytecode
import kotlin.io.encoding.Base64
import wang.harlon.quickjs.QuickJs

object TinyUI {
    /** This library's version: the tinyui-core version it ships with, also in a source build. */
    val version: String get() = VERSION

    /** Version of the quickjs-kmp SDK this build links against. */
    val engineVersion: String get() = QuickJs.sdkVersion

    /** Debug builds show the (mapped) JS stack on the page-failure screen; keep false in release. */
    var debug: Boolean = false

    /**
     * Whether pages built against tinyui [pkg] run on a host whose tinyui is [host]: same major, host not older
     * (docs/updates.md §1.1). False for anything that is not a `major.minor.patch[-pre]` version.
     */
    fun isCompatible(host: String, pkg: String): Boolean {
        val h = SemVer.parse(host) ?: return false
        val p = SemVer.parse(pkg) ?: return false
        return h.major == p.major && h >= p
    }

    /** The runtime modules every page imports; they ship with this library, never in a package (docs/adr-006-hot-updates.md §2.11). */
    internal val runtime: RuntimeBundle by lazy {
        RuntimeBundle(
            core = Base64.decode(RuntimeBytecode.core.joinToString("")),
            native = Base64.decode(RuntimeBytecode.native.joinToString("")),
        )
    }
}

/** The same precedence as the CLI's `version.ts`: numbers compared as text, so none is too large to parse. */
private class SemVer(val core: List<String>, val pre: List<String>) : Comparable<SemVer> {
    val major: String get() = core[0]

    override fun compareTo(other: SemVer): Int {
        for (i in 0..2) compareNumeric(core[i], other.core[i]).let { if (it != 0) return it }
        if (pre.isEmpty() || other.pre.isEmpty()) return other.pre.size - pre.size
        for ((x, y) in pre.zip(other.pre)) {
            if (x == y) continue
            val nx = NUMERIC.matches(x)
            val ny = NUMERIC.matches(y)
            return when {
                nx && ny -> compareNumeric(x, y)
                nx -> -1
                ny -> 1
                else -> x.compareTo(y)
            }
        }
        return pre.size - other.pre.size
    }

    companion object {
        private val NUMERIC = Regex("0|[1-9][0-9]*")
        private val PATTERN = Regex("""(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(?:-([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?""")

        /** Digits without leading zeros: the longer one is larger, equal lengths compare as text. */
        private fun compareNumeric(a: String, b: String): Int = if (a.length != b.length) a.length - b.length else a.compareTo(b)

        fun parse(version: String): SemVer? {
            val m = PATTERN.matchEntire(version) ?: return null
            val (major, minor, patch, preText) = m.destructured
            val pre = if (preText.isEmpty()) emptyList() else preText.split('.')
            if (pre.any { id -> id.all { it in '0'..'9' } && !NUMERIC.matches(id) }) return null
            return SemVer(listOf(major, minor, patch), pre)
        }
    }
}

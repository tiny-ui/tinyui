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

/** The same precedence as the CLI's `version.ts`: a pre-release sorts before its release. */
private class SemVer(val major: Int, val minor: Int, val patch: Int, val pre: List<String>) : Comparable<SemVer> {
    override fun compareTo(other: SemVer): Int {
        compareValues(major, other.major).let { if (it != 0) return it }
        compareValues(minor, other.minor).let { if (it != 0) return it }
        compareValues(patch, other.patch).let { if (it != 0) return it }
        if (pre.isEmpty() || other.pre.isEmpty()) return other.pre.size - pre.size
        for ((x, y) in pre.zip(other.pre)) {
            if (x == y) continue
            val nx = x.toIntOrNull()
            val ny = y.toIntOrNull()
            return when {
                nx != null && ny != null -> compareValues(nx, ny)
                nx != null -> -1
                ny != null -> 1
                else -> x.compareTo(y)
            }
        }
        return pre.size - other.pre.size
    }

    companion object {
        private val PATTERN = Regex("""(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-([0-9A-Za-z.-]+))?""")

        fun parse(version: String): SemVer? {
            val m = PATTERN.matchEntire(version) ?: return null
            val (major, minor, patch, pre) = m.destructured
            return SemVer(major.toInt(), minor.toInt(), patch.toInt(), if (pre.isEmpty()) emptyList() else pre.split('.'))
        }
    }
}

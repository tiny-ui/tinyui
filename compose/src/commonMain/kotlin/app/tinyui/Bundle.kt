package app.tinyui

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Where a package's bytes come from; [path] is relative to the package (`manifest.json`, `pages/home.bin`). Null when absent. */
fun interface BundleFiles {
    suspend fun read(path: String): ByteArray?
}

/** One page of a [Bundle], ready for [TinyUIPage]. */
class LoadedPage(val module: PageModule, val sourceMaps: SourceMaps, val bundle: Bundle)

/**
 * A package as `tinyui build` produced it (docs/updates.md §3): page bytecode, and a page's `.js.map` when the files
 * have it; the runtime comes with this library. The bundle never knows where the bytes live.
 */
class Bundle(val manifest: BuildManifest, private val files: BundleFiles) {
    val name: String get() = manifest.name

    private val lock = Mutex()
    private val maps = HashMap<String, String?>()
    private var i18n: PackageI18n? = null

    suspend fun page(name: String): LoadedPage {
        require(name in manifest.pages) { "page $name is not in package ${manifest.name}: ${manifest.pages}" }
        val bytecode = read(manifest.file(name) + ".bin")
        val module = PageModule(name, bytecode, manifest.buildId(name), i18n())
        return LoadedPage(module, sourceMaps(name), this)
    }

    /** The package's strings, every language read once (docs/native-api.md §8). */
    private suspend fun i18n(): PackageI18n = lock.withLock {
        i18n ?: run {
            val spec = manifest.i18n ?: return@run PackageI18n.EMPTY
            PackageI18n.parse(spec.defaultLocale, spec.files.mapValues { (_, path) -> read(path).decodeToString() })
        }.also { i18n = it }
    }

    private suspend fun sourceMaps(module: String): SourceMaps {
        val map = lock.withLock {
            if (module in maps) maps[module] else files.read(manifest.file(module) + ".js.map")?.decodeToString().also { maps[module] = it }
        }
        return if (map == null) SourceMaps.EMPTY else SourceMaps(mapOf(module to map))
    }

    private suspend fun read(path: String): ByteArray =
        files.read(path) ?: throw IllegalStateException("package ${manifest.name} has no $path")

    companion object {
        suspend fun load(files: BundleFiles): Bundle {
            val json = files.read("manifest.json") ?: throw IllegalStateException("no manifest.json in the package files")
            return Bundle(BuildManifest.parse(json.decodeToString()), files)
        }
    }
}

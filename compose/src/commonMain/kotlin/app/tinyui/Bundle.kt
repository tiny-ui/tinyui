package app.tinyui

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Where a package's bytes come from; [path] is relative to the package (`manifest.json`, `pages/home.bin`). Null when absent. */
fun interface BundleFiles {
    suspend fun read(path: String): ByteArray?
}

/** One page of a [Bundle], ready for [TinyUIPage]. */
class LoadedPage(val runtime: RuntimeBundle, val module: PageModule, val sourceMaps: SourceMaps, val bundle: Bundle)

/**
 * A package as `tinyui build` produced it (docs/updates.md §3): the runtime bytecode is read once and shared by
 * every page, a page's `.js.map` rides along when the files have it. The bundle never knows where the bytes live.
 */
class Bundle(val manifest: BuildManifest, private val files: BundleFiles) {
    val name: String get() = manifest.name

    private val lock = Mutex()
    private var runtime: RuntimeBundle? = null
    private val maps = HashMap<String, String?>()

    suspend fun page(name: String): LoadedPage {
        require(name in manifest.pages) { "page $name is not in package ${manifest.name}: ${manifest.pages}" }
        val runtime = runtime()
        val bytecode = read(manifest.file(name) + ".bin")
        val module = PageModule(name, bytecode, manifest.buildId(name))
        return LoadedPage(runtime, module, sourceMaps(manifest.runtime + name), this)
    }

    private suspend fun runtime(): RuntimeBundle = lock.withLock {
        runtime ?: RuntimeBundle(core = read(manifest.file("tinyui-core") + ".bin"), native = read(manifest.file("tinyui-native") + ".bin")).also { runtime = it }
    }

    private suspend fun sourceMaps(modules: List<String>): SourceMaps {
        val found = HashMap<String, String>()
        for (module in modules) {
            val map = lock.withLock {
                if (module in maps) maps[module] else files.read(manifest.file(module) + ".js.map")?.decodeToString().also { maps[module] = it }
            }
            if (map != null) found[module] = map
        }
        return if (found.isEmpty()) SourceMaps.EMPTY else SourceMaps(found)
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

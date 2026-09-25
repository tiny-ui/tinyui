package app.tinyui

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okio.FileSystem
import okio.Path

/** Each package's `storage` (docs/native-api.md §7), loaded on first use and kept for the life of the App. */
@OptIn(ExperimentalAtomicApi::class)
internal class Storages(private val dir: Path?, private val sink: PageSink, private val fs: FileSystem = platformFileSystem) {
    private val byPackage = AtomicReference<Map<String, PackageStorage>>(emptyMap())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Null when the host gave no data directory. */
    fun of(pkg: String): PackageStorage? {
        val root = dir ?: return null
        byPackage.load()[pkg]?.let { return it }
        val loaded = PackageStorage(fs, root / "storage" / "$pkg.json", scope, sink)
        var result = loaded
        byPackage.update { map -> map[pkg]?.let { result = it; map } ?: (map + (pkg to loaded)) }
        return result
    }
}

/**
 * One package's key → JSON text, read whole into memory; writes land in memory at once and reach the file together
 * after [FLUSH_DELAY_MS], through a temporary file renamed over it.
 */
@OptIn(ExperimentalAtomicApi::class)
internal class PackageStorage(private val fs: FileSystem, private val file: Path, private val scope: CoroutineScope, private val sink: PageSink) {
    private val values = AtomicReference(read())
    private val flush = AtomicReference<Job?>(null)

    fun get(key: String): String? = values.load()[key]

    /** Null on success, or the E3 code. */
    fun set(key: String, json: String): String? {
        var over = false
        values.update { map ->
            val next = map + (key to json)
            if (sizeOf(next) > LIMIT_CHARS) { over = true; map } else { over = false; next }
        }
        if (over) return "E_QUOTA"
        scheduleFlush()
        return null
    }

    fun remove(key: String) {
        values.update { it - key }
        scheduleFlush()
    }

    fun clear() {
        values.store(emptyMap())
        scheduleFlush()
    }

    private fun scheduleFlush() {
        val job = scope.launch(start = CoroutineStart.LAZY) {
            delay(FLUSH_DELAY_MS)
            write(values.load())
        }
        flush.exchange(job)?.cancel()
        job.start()
    }

    private fun read(): Map<String, String> = try {
        if (!fs.exists(file)) emptyMap()
        else Json.parseToJsonElement(fs.read(file) { readUtf8() }).jsonObject.mapValues { it.value.toString() }
    } catch (e: Exception) {
        sink.log("storage: ${file.name} unreadable, starting empty: ${e.message}")
        emptyMap()
    }

    private fun write(map: Map<String, String>) {
        try {
            file.parent?.let { fs.createDirectories(it) }
            val tmp = file.parent!! / "${file.name}.tmp"
            val json = JsonObject(map.mapValues { Json.parseToJsonElement(it.value) as JsonElement }).toString()
            fs.write(tmp) { writeUtf8(json) }
            fs.atomicMove(tmp, file)
        } catch (e: Exception) {
            sink.log("storage: writing ${file.name} failed: ${e.message}")
        }
    }

    private fun sizeOf(map: Map<String, String>): Int = map.entries.sumOf { it.key.length + it.value.length }

    companion object {
        /** The whole package, keys plus JSON values, in characters (docs/native-api.md §14). */
        const val LIMIT_CHARS = 1_000_000
        const val FLUSH_DELAY_MS = 200L
    }
}

/** okio declares `FileSystem.SYSTEM` per platform, not in common code. */
internal expect val platformFileSystem: FileSystem

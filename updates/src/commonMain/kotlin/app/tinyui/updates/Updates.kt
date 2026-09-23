package app.tinyui.updates

import app.tinyui.BuildManifest
import app.tinyui.Bundle
import app.tinyui.PageFailure
import app.tinyui.PageHost
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import okio.FileSystem
import okio.IOException
import okio.Path
import wang.harlon.quickjs.QuickJs

/**
 * Hot updates for a set of packages (docs/updates.md §4): verifies, stores, selects and rolls back, never
 * fetches on its own. The host provides its embedded packages, a `fetch` for the relative paths of §2, a
 * directory, a stable install id and the `hostVersion` it declares.
 */
class Updates internal constructor(
    packages: List<Bundle>,
    val hostVersion: String,
    private val dir: Path,
    private val installId: String,
    private val fetch: suspend (path: String) -> ByteArray,
    private val onEvent: (UpdateEvent) -> Unit,
    private val fs: FileSystem,
    private val engine: String,
    private val protocol: Int,
    private val verifier: SignatureVerifier,
) {
    constructor(
        packages: List<Bundle>,
        hostVersion: String,
        dir: Path,
        installId: String,
        fetch: suspend (path: String) -> ByteArray,
        onEvent: (UpdateEvent) -> Unit = {},
    ) : this(packages, hostVersion, dir, installId, fetch, onEvent, platformFileSystem, QuickJs.upstreamCommit, PageHost.PROTOCOL, PlatformSignatureVerifier)

    private val packages: Map<String, Package>
    private val checkLock = Mutex()
    private var inFlight: CompletableDeferred<Map<String, CheckResult>>? = null

    init {
        require(packages.isNotEmpty()) { "Updates needs at least one embedded package" }
        require(isHostVersion(hostVersion)) { "hostVersion must be a positive integer, got \"$hostVersion\"; it counts what the host provides to pages, it is not the App version" }
        val byName = LinkedHashMap<String, Package>()
        for (bundle in packages) {
            require(bundle.name !in byName) { "two embedded packages are named ${bundle.name}" }
            require(bundle.manifest.engine == engine) { "package ${bundle.name} was built for engine ${bundle.manifest.engine}, this host embeds $engine" }
            byName[bundle.name] = Package(bundle)
        }
        this.packages = byName
        for (p in byName.values) p.selectAtStartup()
        // App upgrades drop packages: their directories go too
        fs.listOrNull(dir)?.filter { it.name !in byName }?.forEach { fs.deleteRecursively(it) }
        for (p in byName.values) {
            onEvent(UpdateEvent.Running(p.name, p.current.manifest.version, if (p.current === p.embedded) Source.EMBEDDED else Source.INSTALLED))
        }
    }

    /** The bundle every page of [pkg] loads from in this process: chosen at construction, embedded again after a rollback. */
    fun current(pkg: String): Bundle = pkg(pkg).current

    /** Runs the check for every package at once; a second call while one is running joins it. */
    suspend fun check(): Map<String, CheckResult> {
        val (run, owner) = checkLock.withLock {
            inFlight?.let { it to false } ?: CompletableDeferred<Map<String, CheckResult>>().also { inFlight = it }.let { it to true }
        }
        if (!owner) return run.await()
        try {
            val results = coroutineScope { packages.keys.map { pkg -> async { pkg to check(pkg) } }.map { it.await() } }.toMap()
            run.complete(results)
            return results
        } catch (e: Throwable) {
            run.completeExceptionally(e)
            throw e
        } finally {
            checkLock.withLock { inFlight = null }
        }
    }

    /** One package; two calls for the same package at once run one after the other. */
    suspend fun check(pkg: String): CheckResult {
        val p = pkg(pkg)
        return p.lock.withLock { p.check() }.also { onEvent(it.toEvent(pkg)) }
    }

    internal fun embedded(pkg: String): Bundle = pkg(pkg).embedded

    /**
     * A page of [bundle], an installed package of [pkg], failed (docs/updates.md §4.5): blacklist that version and go
     * back to embedded. A newer version `check()` installed meanwhile is left alone, only the failed one is dropped.
     */
    internal suspend fun rollBack(pkg: String, bundle: Bundle, page: String, failure: PageFailure) {
        val p = pkg(pkg)
        val version = bundle.manifest.version
        p.lock.withLock {
            if (version !in p.failed) p.failed += version
            p.current = p.embedded
            if (p.installed == version) {
                p.installed = null
                runCatching { fs.deleteRecursively(p.root / INSTALLED / version) }
            }
            p.saveState()
        }
        onEvent(UpdateEvent.RolledBack(pkg, version, page, failure.kind, failure.error.buildId, failure.message))
    }

    private fun pkg(name: String): Package = packages[name] ?: throw IllegalArgumentException("no embedded package named $name; have ${packages.keys}")

    private fun inRollout(pkg: String, version: String, rollout: Int): Boolean {
        val hash = "$installId:$pkg:$version".encodeUtf8().sha256()
        val bucket = ((hash[0].toLong() and 0xff) shl 24 or ((hash[1].toLong() and 0xff) shl 16) or ((hash[2].toLong() and 0xff) shl 8) or (hash[3].toLong() and 0xff)) % 100
        return bucket < rollout
    }

    @Serializable
    private class State(val installed: String? = null, val failed: List<String> = emptyList())

    private class Pointer(val version: String, val rollout: Int, val signature: String)

    private inner class Package(val embedded: Bundle) {
        val name: String = embedded.name
        val root: Path = dir / name
        val lock = Mutex()
        var installed: String? = null
        val failed = ArrayList<String>()
        var current: Bundle = embedded

        fun selectAtStartup() {
            readState()
            runCatching { fs.deleteRecursively(root / STAGING) }
            val installedDir = root / INSTALLED
            val keep = installed
            fs.listOrNull(installedDir)?.filter { it.name != keep }?.forEach { fs.deleteRecursively(it) }
            if (keep == null) return
            val loaded = loadInstalled(installedDir / keep)
            if (loaded != null) {
                current = loaded
                return
            }
            installed = null
            saveState()
            fs.deleteRecursively(installedDir / keep)
        }

        /** The installed package when it is still the one to run: newer than embedded, this host version, intact. */
        private fun loadInstalled(dir: Path): Bundle? {
            val manifest = runCatching { BuildManifest.parse(fs.read(dir / MANIFEST) { readUtf8() }) }.getOrNull() ?: return null
            if (manifest.name != name || manifest.hostVersion != hostVersion || manifest.version in failed) return null
            if (manifest.createdAt <= embedded.manifest.createdAt) return null
            val files = HashMap<String, ByteArray>()
            for (module in manifest.runtime + manifest.pages) {
                val path = manifest.file(module) + ".bin"
                val bytes = runCatching { fs.read(dir / path) { readByteArray() } }.getOrNull()
                if (bytes == null || bytes.toByteString().sha256().hex() != manifest.hashes[module]) {
                    failed += manifest.version
                    onEvent(UpdateEvent.Failed(name, manifest.version, FailStage.INTEGRITY, "$path is missing or does not match manifest.hashes"))
                    return null
                }
                files[path] = bytes
            }
            return Bundle(manifest) { path -> files[path] }
        }

        suspend fun check(): CheckResult {
            val pointer = try {
                parsePointer(fetch("$name/$hostVersion/$POINTER").decodeToString())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                return CheckResult.Failed(null, FailStage.POINTER, e.message ?: e.toString())
            }
            val version = pointer.version
            if (!isPathSegment(version)) return CheckResult.Failed(version, FailStage.POINTER, "version \"$version\" is not a path segment")
            if (version == installed) return CheckResult.UpToDate(version)
            if (version == embedded.manifest.version) return revertToEmbedded(version)
            if (version in failed) return CheckResult.Skipped(version, SkipReason.FAILED_BEFORE)
            if (!inRollout(name, version, pointer.rollout)) return CheckResult.Skipped(version, SkipReason.ROLLOUT)

            val manifestBytes = try {
                fetch("$name/$hostVersion/$version/$MANIFEST")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                return CheckResult.Failed(version, FailStage.MANIFEST, e.message ?: e.toString())
            }
            val trusted = embedded.manifest.publicKey
            if (!verifier.verify(trusted, manifestBytes, pointer.signature)) {
                val theirs = runCatching { BuildManifest.parse(manifestBytes.decodeToString()).publicKey }.getOrNull()
                val hint = if (theirs == trusted) "the manifest names the embedded publicKey" else "the manifest names another publicKey (rotated?)"
                return CheckResult.Failed(version, FailStage.SIGNATURE, "signature does not verify; $hint")
            }
            val manifest = try {
                BuildManifest.parse(manifestBytes.decodeToString())
            } catch (e: Exception) {
                return CheckResult.Failed(version, FailStage.MANIFEST, e.message ?: e.toString())
            }
            val mismatch = buildSet {
                if (manifest.name != name) add(Mismatch.NAME)
                if (manifest.version != version) add(Mismatch.VERSION)
                if (manifest.hostVersion != hostVersion) add(Mismatch.HOST_VERSION)
                if (manifest.engine != engine) add(Mismatch.ENGINE)
                if (manifest.protocol != protocol) add(Mismatch.PROTOCOL)
            }
            if (mismatch.isNotEmpty()) return CheckResult.Skipped(version, SkipReason.INCOMPATIBLE, mismatch)
            if (manifest.createdAt <= embedded.manifest.createdAt) {
                return if (installed != null) revertToEmbedded(version) else CheckResult.Skipped(version, SkipReason.OLDER_THAN_EMBEDDED)
            }

            val stagingRoot = root / STAGING
            val staging = stagingRoot / version
            fun discard() = runCatching { fs.deleteRecursively(stagingRoot) }
            try {
                fs.deleteRecursively(stagingRoot)
                fs.createDirectories(staging)
                val failure = downloadInto(staging, manifest, manifestBytes)
                if (failure != null) {
                    discard()
                    return failure
                }
                val target = root / INSTALLED / version
                fs.createDirectories(root / INSTALLED)
                fs.deleteRecursively(target)
                fs.atomicMove(staging, target)
                discard()
                installed = version
                saveState()
                fs.list(root / INSTALLED).filter { it.name != version }.forEach { fs.deleteRecursively(it) }
            } catch (e: CancellationException) {
                discard()
                throw e
            } catch (e: IOException) {
                discard()
                return CheckResult.Failed(version, FailStage.STORAGE, e.message ?: e.toString())
            }
            return CheckResult.Installed(version)
        }

        /** The pointer targets no newer than embedded: embedded is what should run, from the next start (docs/updates.md §4.3). */
        private fun revertToEmbedded(version: String): CheckResult {
            val drop = installed ?: return CheckResult.UpToDate(version)
            installed = null
            try {
                saveState()
            } catch (e: IOException) {
                installed = drop
                return CheckResult.Failed(version, FailStage.STORAGE, e.message ?: e.toString())
            }
            // state.json no longer names it: a leftover directory is swept at the next start
            runCatching { fs.deleteRecursively(root / INSTALLED / drop) }
            return CheckResult.Reverted(version)
        }

        /** Every file of [manifest] into [staging], each checked against `hashes`; null once all are there. */
        private suspend fun downloadInto(staging: Path, manifest: BuildManifest, manifestBytes: ByteArray): CheckResult.Failed? {
            val version = manifest.version
            for (module in manifest.runtime + manifest.pages) {
                val path = manifest.file(module) + ".bin"
                val bytes = try {
                    fetch("$name/$hostVersion/$version/$path")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    currentCoroutineContext().ensureActive()
                    return CheckResult.Failed(version, FailStage.DOWNLOAD, "$path: ${e.message ?: e}")
                }
                if (bytes.toByteString().sha256().hex() != manifest.hashes[module]) {
                    return CheckResult.Failed(version, FailStage.INTEGRITY, "$path does not match manifest.hashes")
                }
                val file = staging / path
                file.parent?.let { fs.createDirectories(it) }
                fs.write(file) { write(bytes) }
            }
            fs.write(staging / MANIFEST) { write(manifestBytes) }
            return null
        }

        private fun readState() {
            val state = runCatching { json.decodeFromString(State.serializer(), fs.read(root / STATE) { readUtf8() }) }.getOrNull() ?: return
            installed = state.installed
            failed += state.failed
        }

        fun saveState() {
            // a temporary file and a rename: the state is whole at every instant
            fs.createDirectories(root)
            val tmp = root / "$STATE.tmp"
            fs.write(tmp) { writeUtf8(json.encodeToString(State.serializer(), State(installed, failed.takeLast(MAX_FAILED)))) }
            fs.atomicMove(tmp, root / STATE)
        }
    }

    private fun parsePointer(text: String): Pointer {
        val root = Json.parseToJsonElement(text).jsonObject
        val version = root["version"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("current.json has no version")
        val signature = root["signature"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("current.json has no signature")
        val rollout = root["rollout"]?.let { it.jsonPrimitive.intOrNull ?: throw IllegalArgumentException("current.json rollout is not an integer: $it") } ?: 100
        return Pointer(version, rollout.coerceIn(0, 100), signature)
    }

    private companion object {
        const val POINTER = "current.json"
        const val MANIFEST = "manifest.json"
        const val STATE = "state.json"
        const val STAGING = "staging"
        const val INSTALLED = "installed"
        const val MAX_FAILED = 10
        val json = Json { ignoreUnknownKeys = true }

        fun isHostVersion(value: String): Boolean = value.isNotEmpty() && value[0] in '1'..'9' && value.all { it in '0'..'9' }

        fun isPathSegment(value: String): Boolean = value.isNotEmpty() && value != "." && value != ".." && value.all { it.isLetterOrDigit() || it == '.' || it == '_' || it == '-' }
    }
}

/** okio declares `FileSystem.SYSTEM` per platform, not in common code. */
internal expect val platformFileSystem: FileSystem

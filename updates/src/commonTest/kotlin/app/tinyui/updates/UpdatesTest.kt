package app.tinyui.updates

import app.tinyui.BuildManifest
import app.tinyui.Bundle
import app.tinyui.PageError
import app.tinyui.PageFailure
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.encodeUtf8
import okio.FileSystem
import okio.IOException
import okio.Path
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class UpdatesTest {
    // the real file system on both platforms (okio's fake one does not link on iOS with this Kotlin), a fresh directory per test
    private val fs = FileSystem.SYSTEM
    private val root: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "tinyui-updates-test-${Random.nextLong().toULong()}"
    private val dir: Path = root / "tinyui"

    @AfterTest
    fun cleanUp() {
        fs.deleteRecursively(root)
    }
    private val server = HashMap<String, ByteArray>()
    private val events = ArrayList<UpdateEvent>()
    private val fetch: suspend (String) -> ByteArray = { path -> server[path] ?: throw IOException("404 $path") }

    /** Anything but the literal "bad" verifies: the real algorithm has its own test. */
    private val lenient = SignatureVerifier { _, _, signature -> signature != "bad" }

    private fun manifest(
        name: String = "shop",
        version: String = "v1",
        createdAt: String = "2026-09-22T10:00:00Z",
        engine: String = Fixture.ENGINE,
        protocol: Int = 1,
        publicKey: String = Fixture.PUBLIC_KEY,
        files: Map<String, String> = mapOf("runtime/core.bin" to "CORE-$version", "runtime/native.bin" to "NATIVE-$version", "pages/home.bin" to "HOME-$version"),
        runtimeVersion: String? = "1",
    ): String {
        val modules = mapOf("tinyui-core" to "runtime/core", "tinyui-native" to "runtime/native", "$name/home" to "pages/home")
        val hashes = modules.entries.joinToString(",") { (m, p) -> "\"$m\":\"${files.getValue("$p.bin").encodeUtf8().sha256().hex()}\"" }
        val rv = if (runtimeVersion == null) "" else ",\"runtimeVersion\":\"$runtimeVersion\""
        return """{"runtime":["tinyui-core","tinyui-native"],"pages":["$name/home"],"files":{${modules.entries.joinToString(",") { "\"${it.key}\":\"${it.value}\"" }}},"buildIds":{},"name":"$name","publicKey":"$publicKey","version":"$version","createdAt":"$createdAt","engine":"$engine","protocol":$protocol,"hashes":{$hashes}$rv}"""
    }

    private fun embedded(name: String = "shop", version: String = "v0", createdAt: String = "2026-09-22T09:00:00Z"): Bundle {
        val files = mapOf("runtime/core.bin" to "CORE-$version", "runtime/native.bin" to "NATIVE-$version", "pages/home.bin" to "HOME-$version")
        val json = manifest(name, version, createdAt, files = files, runtimeVersion = null)
        return Bundle(BuildManifest.parse(json)) { path -> files[path]?.encodeToByteArray() }
    }

    /** Publishes [manifestJson] as `<pkg>/<rv>/<version>` with its files and a pointer to it. */
    private fun publish(manifestJson: String, pkg: String = "shop", rv: String = "1", rollout: Int = 100, signature: String = "ok", pointerVersion: String? = null, files: Map<String, String>? = null) {
        val m = BuildManifest.parse(manifestJson)
        val version = pointerVersion ?: m.version
        server["$pkg/$rv/current.json"] = """{"version":"$version","rollout":$rollout,"signature":"$signature"}""".encodeToByteArray()
        server["$pkg/$rv/$version/manifest.json"] = manifestJson.encodeToByteArray()
        val content = files ?: mapOf("runtime/core.bin" to "CORE-${m.version}", "runtime/native.bin" to "NATIVE-${m.version}", "pages/home.bin" to "HOME-${m.version}")
        for ((path, bytes) in content) server["$pkg/$rv/$version/$path"] = bytes.encodeToByteArray()
    }

    private fun updates(vararg bundles: Bundle, installId: String = "install-1", rv: String = "1", verifier: SignatureVerifier = lenient, engine: String = Fixture.ENGINE) =
        Updates(bundles.toList(), rv, dir, installId, fetch, { events += it }, fs, engine, 1, verifier)

    private suspend fun pageBytes(bundle: Bundle) = bundle.page("shop/home").module.bytecode.decodeToString()

    @Test
    fun installsAValidUpdateAndRunsItFromTheNextStart() = runTest {
        publish(manifest())
        val first = updates(embedded())
        assertEquals(listOf<UpdateEvent>(UpdateEvent.Running("shop", "v0", Source.EMBEDDED)), events)
        assertEquals(CheckResult.Installed("v1"), first.check("shop"))
        assertEquals(UpdateEvent.Installed("shop", "v1"), events.last())
        assertEquals("HOME-v0", pageBytes(first.current("shop")), "the process keeps what it started with")
        assertTrue(fs.exists(dir / "shop/installed/v1/manifest.json"))
        assertTrue(fs.exists(dir / "shop/installed/v1/pages/home.bin"))
        assertFalse(fs.exists(dir / "shop/staging"))
        assertContains(fs.read(dir / "shop/state.json") { readUtf8() }, "\"installed\":\"v1\"")

        events.clear()
        val second = updates(embedded())
        assertEquals(listOf<UpdateEvent>(UpdateEvent.Running("shop", "v1", Source.INSTALLED)), events)
        assertEquals("HOME-v1", pageBytes(second.current("shop")))
        assertEquals(CheckResult.UpToDate("v1"), second.check("shop"))
    }

    @Test
    fun aBrokenOrEscapingPointerFails() = runTest {
        val u = updates(embedded())
        assertEquals(FailStage.POINTER, (u.check("shop") as CheckResult.Failed).stage, "404")
        server["shop/1/current.json"] = "not json".encodeToByteArray()
        assertEquals(FailStage.POINTER, (u.check("shop") as CheckResult.Failed).stage)
        publish(manifest(), pointerVersion = "../escape")
        val escaped = assertIs<CheckResult.Failed>(u.check("shop"))
        assertEquals(FailStage.POINTER, escaped.stage)
        assertFalse(fs.exists(dir / "shop/staging"))
    }

    @Test
    fun aBadSignatureFailsBeforeAnythingElseIsLookedAt() = runTest {
        publish(manifest(name = "other", engine = "x"), signature = "bad")
        val same = assertIs<CheckResult.Failed>(updates(embedded()).check("shop"))
        assertEquals(FailStage.SIGNATURE, same.stage)
        assertContains(same.message, "embedded publicKey")
        publish(manifest(publicKey = Fixture.OTHER_PUBLIC_KEY), signature = "bad")
        val rotated = assertIs<CheckResult.Failed>(updates(embedded()).check("shop"))
        assertContains(rotated.message, "another publicKey")
        assertFalse(fs.exists(dir / "shop/installed"))
    }

    @Test
    fun aManifestForAnotherPackageHostOrEngineIsSkippedAsIncompatible() = runTest {
        val u = updates(embedded())
        publish(manifest(name = "other"))
        assertEquals(CheckResult.Skipped("v1", SkipReason.INCOMPATIBLE, setOf(Mismatch.NAME)), u.check("shop"))
        publish(manifest(engine = "f".repeat(40), protocol = 2, runtimeVersion = "2"))
        assertEquals(setOf(Mismatch.ENGINE, Mismatch.PROTOCOL, Mismatch.RUNTIME_VERSION), (u.check("shop") as CheckResult.Skipped).mismatch)
        publish(manifest(version = "v9"), pointerVersion = "v1")
        assertEquals(setOf(Mismatch.VERSION), (u.check("shop") as CheckResult.Skipped).mismatch)
        publish(manifest(createdAt = "2026-09-22T09:00:00Z"))
        assertEquals(CheckResult.Skipped("v1", SkipReason.OLDER_THAN_EMBEDDED), u.check("shop"))
    }

    @Test
    fun rolloutIsARepeatableDiceRollPerInstallPackageAndVersion() = runTest {
        publish(manifest(), rollout = 0)
        assertEquals(CheckResult.Skipped("v1", SkipReason.ROLLOUT), updates(embedded()).check("shop"))
        publish(manifest(), rollout = 50)
        val hits = (1..200).count { i ->
            Updates(listOf(embedded()), "1", root / "roll-$i", "install-$i", fetch, {}, fs, Fixture.ENGINE, 1, lenient).check("shop") is CheckResult.Installed
        }
        assertTrue(hits in 70..130, "about half of 200 installs are in a 50% rollout, got $hits")
        val once = Updates(listOf(embedded()), "1", root / "once", "install-7", fetch, {}, fs, Fixture.ENGINE, 1, lenient).check("shop")
        val again = Updates(listOf(embedded()), "1", root / "again", "install-7", fetch, {}, fs, Fixture.ENGINE, 1, lenient).check("shop")
        assertEquals(once::class, again::class, "the same install rolls the same")
    }

    @Test
    fun aFileThatDoesNotMatchItsHashOrCannotBeFetchedLeavesNothingBehind() = runTest {
        publish(manifest(), files = mapOf("runtime/core.bin" to "CORE-v1", "runtime/native.bin" to "NATIVE-v1", "pages/home.bin" to "tampered"))
        val integrity = assertIs<CheckResult.Failed>(updates(embedded()).check("shop"))
        assertEquals(FailStage.INTEGRITY, integrity.stage)
        assertContains(integrity.message, "pages/home.bin")
        server.remove("shop/1/v1/runtime/native.bin")
        publish(manifest(), files = mapOf("runtime/core.bin" to "CORE-v1", "pages/home.bin" to "HOME-v1"))
        val download = assertIs<CheckResult.Failed>(updates(embedded()).check("shop"))
        assertEquals(FailStage.DOWNLOAD, download.stage)
        assertFalse(fs.exists(dir / "shop/staging"))
        assertFalse(fs.exists(dir / "shop/installed"))
    }

    @Test
    fun packagesAreCheckedIndependently() = runTest {
        publish(manifest())
        publish(manifest(name = "orders"), pkg = "orders", signature = "bad")
        val results = updates(embedded(), embedded(name = "orders")).check()
        assertEquals(CheckResult.Installed("v1"), results["shop"])
        assertEquals(FailStage.SIGNATURE, (results["orders"] as CheckResult.Failed).stage)
        assertTrue(fs.exists(dir / "shop/installed/v1/manifest.json"))
        assertFalse(fs.exists(dir / "orders/installed"))
    }

    @Test
    fun startupDropsWhatNoLongerApplies() = runTest {
        publish(manifest())
        updates(embedded()).check("shop")
        fs.createDirectories(dir / "shop/staging/v2")
        fs.createDirectories(dir / "gone/installed/v1")
        fs.write(dir / "gone/state.json") { writeUtf8("{}") }

        // the App now embeds something newer than what was installed
        events.clear()
        val upgraded = updates(embedded(version = "v5", createdAt = "2026-09-23T00:00:00Z"))
        assertEquals("HOME-v5", pageBytes(upgraded.current("shop")))
        assertEquals(Source.EMBEDDED, (events.single() as UpdateEvent.Running).source)
        assertFalse(fs.exists(dir / "shop/installed/v1"))
        assertFalse(fs.exists(dir / "shop/staging"))
        assertFalse(fs.exists(dir / "gone"), "a package the App no longer embeds")

        // installed for another runtime version
        publish(manifest())
        updates(embedded()).check("shop")
        assertTrue(fs.exists(dir / "shop/installed/v1"))
        val other = updates(embedded(), rv = "2")
        assertEquals("HOME-v0", pageBytes(other.current("shop")))
        assertFalse(fs.exists(dir / "shop/installed/v1"))
    }

    @Test
    fun startupChecksTheInstalledBytesAndBlacklistsACorruptedPackage() = runTest {
        publish(manifest())
        updates(embedded()).check("shop")
        fs.write(dir / "shop/installed/v1/pages/home.bin") { writeUtf8("HOME-v1 ") }
        events.clear()
        val u = updates(embedded())
        assertEquals("HOME-v0", pageBytes(u.current("shop")))
        val failed = assertIs<UpdateEvent.Failed>(events.first())
        assertEquals(FailStage.INTEGRITY, failed.stage)
        assertEquals(UpdateEvent.Running("shop", "v0", Source.EMBEDDED), events.last())
        assertFalse(fs.exists(dir / "shop/installed/v1"))
        assertEquals(CheckResult.Skipped("v1", SkipReason.FAILED_BEFORE), u.check("shop"))
        assertContains(fs.read(dir / "shop/state.json") { readUtf8() }, "\"failed\":[\"v1\"]")
    }

    @Test
    fun aFailedPageRollsThePackageBackToEmbedded() = runTest {
        publish(manifest())
        updates(embedded()).check("shop")
        events.clear()
        val u = updates(embedded())
        assertEquals("HOME-v1", pageBytes(u.current("shop")))
        u.rollBack("shop", "shop/home", PageFailure(PageError("E2", "shop/home", "cccccccc", "boom")))
        assertSame(u.embedded("shop"), u.current("shop"))
        assertEquals(UpdateEvent.RolledBack("shop", "v1", "shop/home", "E2", "cccccccc", "boom"), events.last())
        assertFalse(fs.exists(dir / "shop/installed/v1"))
        assertEquals(CheckResult.Skipped("v1", SkipReason.FAILED_BEFORE), u.check("shop"))
        assertEquals("HOME-v0", pageBytes(updates(embedded()).current("shop")), "and stays embedded after a restart")
    }

    @Test
    fun refusesPackagesThatCannotRunOnThisHost() {
        assertFailsWith<IllegalArgumentException> { updates(embedded(), embedded()) }
        assertFailsWith<IllegalArgumentException> { updates(embedded(), engine = "0".repeat(40)) }
        assertFailsWith<IllegalArgumentException> { updates(embedded(), rv = "../x") }
        assertFailsWith<IllegalArgumentException> { updates(embedded()).current("nowhere") }
    }

    @Test
    fun installsAPackageTinyuiBundleSignedWithTheRealVerifier() = runTest {
        server["shop/1/current.json"] = """{"version":"${Fixture.VERSION}","rollout":100,"signature":"${Fixture.SIGNATURE}"}""".encodeToByteArray()
        server["shop/1/${Fixture.VERSION}/manifest.json"] = Fixture.MANIFEST.encodeToByteArray()
        for ((path, bytes) in Fixture.FILES) server["shop/1/${Fixture.VERSION}/$path"] = bytes.encodeToByteArray()
        val u = updates(embedded(), verifier = PlatformSignatureVerifier)
        assertEquals(CheckResult.Installed(Fixture.VERSION), u.check("shop"))
        assertEquals("HOME2", pageBytes(updates(embedded(), verifier = PlatformSignatureVerifier).current("shop")))
        server["shop/1/${Fixture.VERSION}/manifest.json"] = Fixture.MANIFEST.replace("\"protocol\": 1", "\"protocol\": 2").encodeToByteArray()
        fs.deleteRecursively(dir)
        assertEquals(FailStage.SIGNATURE, (updates(embedded(), verifier = PlatformSignatureVerifier).check("shop") as CheckResult.Failed).stage)
    }
}

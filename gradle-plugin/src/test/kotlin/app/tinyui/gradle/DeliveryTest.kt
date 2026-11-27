package app.tinyui.gradle

import groovy.json.JsonOutput
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeliveryTest {
    private val keys = keyPair()
    private val publicKey = rawPoint(keys)
    private val base = "https://updates.example/shop/staging"
    private val server = mutableMapOf<String, ByteArray>()
    private val out = File(Files.createTempDirectory("tinyui-delivery").toFile(), "login")
    private val delivery = Delivery { url -> server[url] }

    private fun resolve(hostVersion: String = "3", strict: Boolean = false, key: String = publicKey, fetch: Delivery = delivery) =
        fetch.resolve(base, "login", hostVersion, key, strict, out)

    /** Publishes [version] of package `login` for [hostVersion], the way `tinyui bundle` lays it out. */
    private fun publish(
        hostVersion: String,
        version: String,
        createdAt: String = "2026-11-22T00:00:00Z",
        rollout: Int? = null,
        signer: KeyPair = keys,
        edit: (MutableMap<String, Any>) -> Unit = {},
    ) {
        val page = "page of $version".toByteArray()
        val words = """{"hello":"$version"}""".toByteArray()
        val manifest = mutableMapOf<String, Any>(
            "name" to "login",
            "publicKey" to rawPoint(signer),
            "version" to version,
            "createdAt" to createdAt,
            "hostVersion" to hostVersion,
            "pages" to listOf("login/home"),
            "files" to mapOf("login/home" to "pages/home"),
            "hashes" to mapOf("login/home" to Delivery.sha256(page), "i18n/en.json" to Delivery.sha256(words)),
            "i18n" to mapOf("default" to "en", "files" to mapOf("en" to "i18n/en.json")),
        ).also(edit)
        val manifestBytes = JsonOutput.toJson(manifest).toByteArray()
        val root = "$base/login/$hostVersion"
        server["$root/$version/manifest.json"] = manifestBytes
        server["$root/$version/pages/home.jsb"] = page
        server["$root/$version/i18n/en.json"] = words
        val pointer = mutableMapOf<String, Any>("version" to version, "signature" to sign(signer, manifestBytes))
        rollout?.let { pointer["rollout"] = it }
        server["$root/current.json"] = JsonOutput.toJson(pointer).toByteArray()
    }

    @Test
    fun embedsWhatTheChannelPointsAt() {
        publish("3", "v1")
        val resolved = resolve()
        assertEquals("v1", resolved.version)
        assertEquals("3", resolved.hostVersion)
        assertTrue(resolved.changed)
        assertNull(resolved.warning)
        assertEquals("page of v1", File(out, "pages/home.jsb").readText())
        assertEquals("""{"hello":"v1"}""", File(out, "i18n/en.json").readText())
        // the manifest is the downloaded bytes, not a reserialization
        assertTrue(File(out, "manifest.json").readBytes().contentEquals(server["$base/login/3/v1/manifest.json"]!!))
    }

    @Test
    fun anUnchangedPointerDownloadsNothing() {
        publish("3", "v1")
        resolve()
        val asked = mutableListOf<String>()
        val resolved = resolve(fetch = Delivery { url -> asked += url; server[url] })
        assertFalse(resolved.changed)
        assertEquals(listOf("$base/login/3/current.json"), asked)
    }

    @Test
    fun aNewVersionReplacesTheWholeDirectory() {
        publish("3", "v1")
        resolve()
        File(out, "pages/gone.jsb").writeText("left by v1")
        publish("3", "v2")
        // a file changed behind the build's back makes the directory no longer the published v1
        assertEquals("v2", resolve().version)
        assertFalse(File(out, "pages/gone.jsb").exists())
        assertEquals("page of v2", File(out, "pages/home.jsb").readText())
    }

    @Test
    fun aReplacementThatCannotBeWrittenLeavesThePreviousPackage() {
        publish("3", "v1")
        resolve()
        // a file where a directory has to go: assembling the new package fails before anything is swapped
        assertFailsWith<Exception> { Delivery.replaceDir(out, mapOf("pages" to byteArrayOf(1), "pages/home.jsb" to byteArrayOf(2))) }
        assertNotNull(Delivery.published(out, "login", publicKey))
        assertEquals(listOf(out.name), out.parentFile.list()!!.toList())
    }

    @Test
    fun replacingLeavesNothingBesideThePackage() {
        publish("3", "v1")
        resolve()
        publish("3", "v2")
        resolve()
        assertEquals(listOf(out.name), out.parentFile.list()!!.toList())
    }

    @Test
    fun aTamperedDirectoryIsDownloadedAgain() {
        publish("3", "v1")
        resolve()
        File(out, "pages/home.jsb").writeText("edited")
        assertTrue(resolve().changed)
        assertEquals("page of v1", File(out, "pages/home.jsb").readText())
    }

    @Test
    fun aVersionMidRolloutIsRefused() {
        publish("3", "v1", rollout = 20)
        val e = assertFailsWith<ResolveException> { resolve() }
        assertTrue("20%" in e.message!!, e.message)
        assertFalse(out.exists())
    }

    @Test
    fun aVersionSignedWithAnotherKeyIsRefused() {
        publish("3", "v1", signer = keyPair())
        assertFailsWith<ResolveException> { resolve() }
        assertFalse(out.exists())
    }

    @Test
    fun aManifestNamingAnotherKeyIsRefusedEvenWhenTheSignatureHolds() {
        val other = rawPoint(keyPair())
        publish("3", "v1") { it["publicKey"] = other }
        val e = assertFailsWith<ResolveException> { resolve() }
        assertTrue(other in e.message!!, e.message)
    }

    @Test
    fun aManifestForAnotherPackageVersionOrHostVersionIsRefused() {
        publish("3", "v1") { it["name"] = "checkout" }
        assertFailsWith<ResolveException> { resolve() }
        publish("3", "v1") { it["version"] = "v0" }
        assertFailsWith<ResolveException> { resolve() }
        publish("3", "v1") { it["hostVersion"] = "2" }
        assertFailsWith<ResolveException> { resolve() }
        assertFalse(out.exists())
    }

    @Test
    fun aFileThatDoesNotMatchItsHashIsRefused() {
        publish("3", "v1")
        server["$base/login/3/v1/pages/home.jsb"] = "something else".toByteArray()
        assertFailsWith<ResolveException> { resolve() }
        assertFalse(out.exists())
    }

    @Test
    fun aPathThatLeavesThePackageIsRefused() {
        publish("3", "v1") { it["files"] = mapOf("login/home" to "../../escape") }
        assertFailsWith<ResolveException> { resolve() }
    }

    @Test
    fun noChannelFallsBackToAnOlderHostVersion() {
        publish("2", "v1")
        for (strict in listOf(false, true)) {
            val e = assertFailsWith<ResolveException> { resolve(hostVersion = "3", strict = strict) }
            assertTrue("host version 3" in e.message!!, e.message)
        }
        assertFalse(out.exists())
    }

    @Test
    fun nothingPublishedAtAllFails() {
        assertFailsWith<ResolveException> { resolve() }
    }

    @Test
    fun withoutNetworkKeepsTheLastResultOffProduction() {
        publish("3", "v1")
        resolve()
        val down = Delivery { throw IOException("no route to host") }
        val resolved = resolve(fetch = down)
        assertEquals("v1", resolved.version)
        assertFalse(resolved.changed)
        assertNotNull(resolved.warning)
        assertFailsWith<ResolveException> { resolve(strict = true, fetch = down) }
    }

    @Test
    fun withoutNetworkWhatAnOlderHostVersionResolvedIsNotKept() {
        publish("2", "v1")
        resolve(hostVersion = "2")
        val e = assertFailsWith<ResolveException> { resolve(hostVersion = "3", fetch = Delivery { throw IOException("no route to host") }) }
        assertTrue("host version 2" in e.message!!, e.message)
    }

    @Test
    fun withoutNetworkAndNothingResolvedBeforeFails() {
        assertFailsWith<ResolveException> { resolve(fetch = Delivery { throw IOException("no route to host") }) }
    }

    @Test
    fun aPackageBuiltFromSourcesIsNotALastResult() {
        out.mkdirs()
        File(out, "manifest.json").writeText("""{"name":"login","publicKey":"$publicKey","version":"local","createdAt":"2026-11-22T00:00:00Z","pages":[],"files":{},"hashes":{}}""")
        assertNull(Delivery.published(out, "login", publicKey))
        assertFailsWith<ResolveException> { resolve(fetch = Delivery { throw IOException("no route to host") }) }
    }

    @Test
    fun aHostVersionThatIsNotAPositiveIntegerIsRefused() {
        assertFailsWith<ResolveException> { resolve(hostVersion = "1.2.0") }
        assertFailsWith<ResolveException> { resolve(hostVersion = "0") }
    }

    private fun keyPair(): KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

    private fun rawPoint(pair: KeyPair): String {
        val point = (pair.public as ECPublicKey).w
        fun fixed(n: java.math.BigInteger): ByteArray = n.toByteArray().let { b -> ByteArray(32).also { o -> b.takeLast(32).toByteArray().copyInto(o, 32 - minOf(32, b.size)) } }
        return Base64.getEncoder().encodeToString(byteArrayOf(0x04) + fixed(point.affineX) + fixed(point.affineY))
    }

    private fun sign(pair: KeyPair, data: ByteArray): String =
        Base64.getEncoder().encodeToString(Signature.getInstance("SHA256withECDSA").run { initSign(pair.private); update(data); sign() })
}

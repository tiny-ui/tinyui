package app.tinyui

import app.tinyui.components.registerBuiltins
import app.tinyui.schema.ComponentRegistry
import app.tinyui.schema.ComponentSchema
import wang.harlon.quickjs.QuickJs
import kotlin.test.Test
import kotlin.test.assertEquals

class PackageCheckTest {
    private val sink = object : PageSink {
        override fun error(error: PageError) {}
        override fun log(line: String) {}
    }

    private val host = TinyUIHost(
        ComponentRegistry().registerBuiltins().apply {
            register(ComponentSchema("ta.Icon", emptyMap(), emptyMap(), emptyMap(), children = false, layout = true)) {}
        },
        sink,
        CapabilityRegistry().apply { register("checkout.start") { _, _ -> null } },
    )

    private fun manifest(
        engine: String = QuickJs.upstreamCommit,
        protocol: Int = PageHost.PROTOCOL,
        tinyui: String = TinyUI.version,
        requires: Map<String, PageRequires> = mapOf("shop/home" to PageRequires(listOf("ta.Icon"), listOf("checkout.start", "store.get"))),
    ) = BuildManifest(
        runtime = emptyList(), pages = listOf("shop/home"), files = emptyMap(), buildIds = emptyMap(),
        name = "shop", publicKey = "k", version = "v1", createdAt = "2026-09-24T00:00:00Z",
        engine = engine, protocol = protocol, hashes = emptyMap(), tinyui = tinyui, requires = requires,
    )

    @Test
    fun aPackageBuiltForThisHostHasNoProblems() {
        assertEquals(emptyList(), PackageCheck.problems(manifest(), host))
    }

    @Test
    fun namesEveryMismatch() {
        val problems = PackageCheck.problems(
            manifest(
                engine = "0".repeat(40),
                protocol = 99,
                tinyui = "0.0.1",
                requires = mapOf("shop/home" to PageRequires(listOf("ta.Rating"), listOf("coupon.apply"))),
            ),
            host,
        )
        assertEquals(
            listOf(
                "built for engine ${"0".repeat(40)}, the host embeds ${QuickJs.upstreamCommit}",
                "built for protocol 99, the host implements ${PageHost.PROTOCOL}",
                "built with tinyui 0.0.1, the host has ${TinyUI.version}",
                "shop/home uses component ta.Rating, which the host does not register",
                "shop/home calls coupon.apply, which the host does not provide",
            ),
            problems,
        )
    }

    @Test
    fun readsTinyuiAndRequiresFromTheManifest() {
        val parsed = BuildManifest.parse(
            """{"name":"shop","publicKey":"k","tinyui":"0.5.0","requires":{"shop/home":{"components":["ta.Icon"],"capabilities":["checkout.start"]}}}""",
        )
        assertEquals("0.5.0", parsed.tinyui)
        assertEquals(listOf("ta.Icon"), parsed.requires.getValue("shop/home").components)
        assertEquals(listOf("checkout.start"), parsed.requires.getValue("shop/home").capabilities)
    }
}

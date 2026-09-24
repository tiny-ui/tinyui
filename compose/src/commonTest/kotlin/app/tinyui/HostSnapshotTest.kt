package app.tinyui

import app.tinyui.components.registerBuiltins
import app.tinyui.schema.ComponentRegistry
import app.tinyui.schema.ComponentSchema
import app.tinyui.schema.FieldSpec
import app.tinyui.schema.PropSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HostSnapshotTest {
    private val sink = object : PageSink {
        override fun error(error: PageError) {}
        override fun log(line: String) {}
    }

    private val icon = ComponentSchema(
        type = "ta.Icon",
        props = mapOf(
            "name" to PropSpec.Str(default = null, required = true),
            "tint" to PropSpec.ColorSpec(default = "primary"),
            "size" to PropSpec.Dp(default = 24.0),
            "label" to PropSpec.Str(default = "a; b\ncapabilities"),
        ),
        events = emptyMap(), commands = emptyMap(), children = false, layout = true,
    )

    private val rating = ComponentSchema(
        type = "ta.Rating",
        props = mapOf(
            "value" to PropSpec.Num(default = null, required = true),
            "style" to PropSpec.Enum(setOf("stars", "hearts"), default = "stars", initial = true),
            "step" to PropSpec.Num(default = 0.5),
        ),
        events = mapOf("onChange" to mapOf("value" to FieldSpec.Num, "fromUser" to FieldSpec.Bool)),
        commands = mapOf("reset" to emptyMap()),
        children = true, layout = false,
    )

    private fun host(capabilities: CapabilityRegistry = CapabilityRegistry()) = TinyUIHost(
        ComponentRegistry().registerBuiltins().apply {
            register(rating) {}
            register(icon) {}
        },
        sink,
        capabilities,
    )

    @Test
    fun listsDottedComponentsWithTheirSchemaAndCapabilityNamesSorted() {
        val capabilities = CapabilityRegistry().apply {
            register("coupon.apply") { _, _ -> null }
            register("checkout.start") { _, _ -> null }
        }
        assertEquals(
            """
            hostVersion 3
            tinyui ${TinyUI.version}

            components
              ta.Icon    label?: string = "a; b\ncapabilities", name: string, size?: dp = 24, tint?: color = primary; layout
              ta.Rating  step?: number = 0.5, style?: enum(hearts|stars) = stars (initial), value: number; events onChange(fromUser: boolean, value: number); commands reset(); children

            capabilities
              checkout.start
              coupon.apply

            """.trimIndent(),
            HostSnapshot.render(host(capabilities), "3"),
        )
    }

    @Test
    fun aHostOfTheSnapshotHasNoProblemsItsTinyuiOnlyHasToBeCompatibleWithTheLowerBound() {
        val host = host()
        val snapshot = HostSnapshot.render(host, "3")
        assertEquals(emptyList(), HostSnapshot.problems(snapshot, host, "3"))
        val floor = TinyUI.version.substringBefore('.') + ".0.0"
        assertEquals(emptyList(), HostSnapshot.problems(snapshot.replace("tinyui ${TinyUI.version}", "tinyui $floor"), host, "3"))
    }

    @Test
    fun namesAnotherHostVersionANewerLowerBoundAndChangedSections() {
        val host = host()
        val snapshot = HostSnapshot.render(host, "3")
        assertEquals(1, HostSnapshot.problems(snapshot, host, "4").size)
        val newer = HostSnapshot.problems(snapshot.replace("tinyui ${TinyUI.version}", "tinyui 99.0.0"), host, "3")
        assertEquals(listOf("tinyui ${TinyUI.version} is not compatible with this host version's lower bound 99.0.0 (same major, not older)"), newer)
        assertEquals(listOf("components or capabilities differ from the snapshot"), HostSnapshot.problems(snapshot.replace("  ta.Icon", "  ta.Icon2"), host, "3"))
    }

    @Test
    fun writesBothSectionsEvenWhenEmpty() {
        val bare = TinyUIHost(ComponentRegistry().registerBuiltins(), sink)
        assertEquals("hostVersion 1\ntinyui ${TinyUI.version}\n\ncomponents\n\ncapabilities\n", HostSnapshot.render(bare, "1"))
    }

    @Test
    fun registriesRejectRegistrationOnceInAHost() {
        val host = host()
        assertFailsWith<IllegalStateException> { host.components.register(ComponentSchema("ta.Other", emptyMap(), emptyMap(), emptyMap(), children = false, layout = true)) {} }
        assertFailsWith<IllegalStateException> { host.capabilities.register("late.one") { _, _ -> null } }
    }

    @Test
    fun rejectsAHostVersionThatIsNotAPositiveInteger() {
        for (bad in listOf("0", "1.2.0", "", "01")) assertFailsWith<IllegalArgumentException>(bad) { HostSnapshot.render(host(), bad) }
    }

    @Test
    fun registryRejectsFrameworkDuplicateAndBlankNames() {
        val registry = CapabilityRegistry().apply { register("a.b") { _, _ -> null } }
        for (bad in listOf("a.b", "store.get", "", "a b")) {
            assertFailsWith<IllegalArgumentException>(bad) { registry.register(bad) { _, _ -> null } }
        }
    }
}

package app.tinyui.bundle

import app.tinyui.TinyUI
import app.tinyui.TinyUIHost
import app.tinyui.components.registerBuiltins
import app.tinyui.CapabilityRegistry
import app.tinyui.HttpChannel
import app.tinyui.NativeRoute
import app.tinyui.Session
import app.tinyui.SessionSource
import app.tinyui.PageError
import app.tinyui.PageSink
import app.tinyui.components.ComponentRegistry
import app.tinyui.components.ComponentSchema
import app.tinyui.components.FieldSpec
import app.tinyui.components.PropSpec
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.descriptors.listSerialDescriptor
import kotlinx.serialization.descriptors.mapSerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlin.test.Test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@OptIn(InternalSerializationApi::class, kotlinx.serialization.ExperimentalSerializationApi::class)
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

    // a lazy feed: no intrinsics, so the line says so; the two above keep their bytes because the default writes nothing
    private val feed = ComponentSchema(
        type = "ta.Feed", props = emptyMap(), events = emptyMap(), commands = emptyMap(), children = true, layout = true, intrinsics = false,
    )

    // what `serializer<ChooseAccountParams>().descriptor` would be for a data class with these fields
    private val chooseAccount = buildClassSerialDescriptor("ChooseAccountParams") {
        element<String>("token")
        element("user", buildClassSerialDescriptor("User") { element<String>("id"); element<Boolean>("main"); element<Int>("model") })
        element("tags", listSerialDescriptor<String>(), isOptional = true)
        element("note", String.serializer().descriptor.nullable, isOptional = true)
        element("labels", mapSerialDescriptor<String, String>(), isOptional = true)
        element("kind", buildSerialDescriptor("Kind", SerialKind.ENUM) { element("b", buildClassSerialDescriptor("b")); element("a", buildClassSerialDescriptor("a")) }, isOptional = true)
    }

    private fun host(
        capabilities: CapabilityRegistry = CapabilityRegistry(),
        channels: Map<String, HttpChannel> = emptyMap(),
        session: SessionSource? = null,
        routes: List<NativeRoute> = emptyList(),
    ) = TinyUIHost(
        ComponentRegistry().registerBuiltins().apply {
            register(rating) {}
            register(icon) {}
            register(feed) {}
        },
        sink,
        capabilities,
        channels = channels,
        session = session,
        routes = routes,
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
              ta.Feed    children; layout; noIntrinsics
              ta.Icon    label?: string = "a; b\ncapabilities", name: string, size?: dp = 24, tint?: color = primary; layout
              ta.Rating  step?: number = 0.5, style?: enum(hearts|stars) = stars (initial), value: number; events onChange(fromUser: boolean, value: number); commands reset(); children

            capabilities
              checkout.start
              coupon.apply
              session.signIn

            channels
              app
              pay

            routes
              account/choose  {"type":"object","properties":{"token":{"type":"string"},"user":{"type":"object","properties":{"id":{"type":"string"},"main":{"type":"boolean"},"model":{"type":"number"}},"required":["id","main","model"]},"tags":{"type":"array","items":{"type":"string"}},"note":{"anyOf":[{"type":"null"},{"type":"string"}]},"labels":{"type":"object","additionalProperties":{"type":"string"}},"kind":{"enum":["a","b"]}},"required":["token","user"]}
              settings        {"type":"object","properties":{},"required":[]}

            """.trimIndent(),
            HostSnapshot.render(
                host(
                    capabilities,
                    channels = mapOf("pay" to HttpChannel { error("unused") }, "app" to HttpChannel { error("unused") }),
                    session = SessionSource(MutableStateFlow(Session.LoggedOut)) {},
                    routes = listOf(NativeRoute("settings", buildClassSerialDescriptor("Settings")), NativeRoute("account/choose", chooseAccount)),
                ),
                "3",
            ),
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
        assertEquals(listOf("components, capabilities, channels or routes differ from the snapshot"), HostSnapshot.problems(snapshot.replace("  ta.Icon", "  ta.Icon2"), host, "3"))
    }

    @Test
    fun writesEverySectionEvenWhenEmpty() {
        val bare = TinyUIHost(ComponentRegistry().registerBuiltins(), sink)
        assertEquals("hostVersion 1\ntinyui ${TinyUI.version}\n\ncomponents\n\ncapabilities\n\nchannels\n\nroutes\n", HostSnapshot.render(bare, "1"))
    }

    @Test
    fun aSnapshotFrozenBeforeChannelsMatchesAHostWithout() {
        val bare = TinyUIHost(ComponentRegistry().registerBuiltins(), sink)
        val old = "hostVersion 1\ntinyui ${TinyUI.version}\n\ncomponents\n\ncapabilities\n"
        assertEquals(emptyList(), HostSnapshot.problems(old, bare, "1"))
        val withChannel = TinyUIHost(ComponentRegistry().registerBuiltins(), sink, channels = mapOf("app" to HttpChannel { error("unused") }))
        assertEquals(1, HostSnapshot.problems(old, withChannel, "1").size)
    }

    @Test
    fun aSnapshotFrozenBeforeRoutesMatchesAHostWithoutAndARouteIsAChange() {
        val bare = TinyUIHost(ComponentRegistry().registerBuiltins(), sink)
        val old = "hostVersion 1\ntinyui ${TinyUI.version}\n\ncomponents\n\ncapabilities\n\nchannels\n"
        assertEquals(emptyList(), HostSnapshot.problems(old, bare, "1"))
        val withRoute = host(routes = listOf(NativeRoute("settings", buildClassSerialDescriptor("Settings"))))
        assertEquals(1, HostSnapshot.problems(HostSnapshot.render(host(), "1"), withRoute, "1").size)
    }

    @Test
    fun aRouteIsDeclaredOnceAndItsParamsHaveAFixedShape() {
        val settings = NativeRoute("settings", buildClassSerialDescriptor("Settings"))
        assertFailsWith<IllegalArgumentException> { host(routes = listOf(settings, settings)) }
        assertFailsWith<IllegalArgumentException> { NativeRoute("no spaces", buildClassSerialDescriptor("X")) }
        val open = NativeRoute("open", buildClassSerialDescriptor("Open") { element("any", buildSerialDescriptor("Any", PolymorphicKind.OPEN)) })
        assertFailsWith<IllegalArgumentException> { open.schema }
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

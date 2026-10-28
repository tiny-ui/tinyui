package app.tinyui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import wang.harlon.quickjs.JsBytecode
import app.tinyui.components.registerBuiltins
import app.tinyui.schema.ComponentRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow

/** Drives PageHost with a hand-written stand-in for `tinyui-core`: the K/J entry shapes, not the real runtime. */
class PageHostTest {
    private val log = mutableListOf<String>()
    private val errors = mutableListOf<PageError>()
    private val sink = object : PageSink {
        override fun error(error: PageError) { errors += error; log += error.toString() }
        override fun log(line: String) { log += line }
    }

    private val zoneFlow = MutableStateFlow("America/New_York")

    private val core = module("tinyui-core", """
        let patches = [], seq = [], count = 0, handler = null, page = null, emits = 0, settled = []; const settle = (line) => { settled.push(line); patches.push(["p",1,"text", settled.slice().sort().join(";")]); };
        globalThis.__tinyui = {
            mount(p, props, host) {
                page = p; const name = JSON.parse(props).name ?? "world";
                patches.push(["c",1,"Text"],["p",1,"text","hello " + name + " / " + Object.keys(JSON.parse(host).components).length],
                             ["c",2,"Button"],["p",2,"text","+1"],["p",2,"onClick",true],["c",3,"Column"],["i",3,1,0],["i",3,2,1],["i",0,3,0]);
                handler = () => { count++; patches.push(["p",1,"text","count " + count]); };
            },
            unmount() {}, visible() {},
            resolve(cbId, json) { if (cbId >= 11) settle("ok " + cbId + " " + json); else patches.push(["p",1,"text", cbId === 9 ? "http " + json : "timer " + cbId]); },
            reject(cbId, json) { if (cbId >= 11) settle("rejected " + cbId + " " + json); else patches.push(["p",1,"text","rejected " + json]); },
            dispatch(id, event, payloadJson) {
                if (id === 2 && event === "onClick") handler();
                if (event === "onTimer") __host_call("timer.schedule", 7, JSON.stringify({ ms: 10 }));
                if (event === "onBoom") { try { throw new Error("boom"); } catch (e) { __host_report("E1", JSON.stringify({ entry: "dispatch", message: e.message, stack: e.stack })); } }
                if (event === "onReject") Promise.reject(new Error("nobody catches"));
                if (event === "onQuery") patches.push(["p",1,"text", __host_query("store.get", JSON.stringify({ key: "cart" })) + "|" + __host_query("i18n.t", JSON.stringify({ key: "hi" })) + "|" + JSON.parse(__host_query("device.info", "{}")).os]);
                if (event === "onHttp") __host_call("http.request", 9, JSON.stringify({ channel: "app", method: "GET", url: "/todos" }));
                if (event === "onCapability") { __host_call("checkout.start", 11, JSON.stringify({ plan: "annual" })); __host_call("checkout.start", 12, JSON.stringify({ plan: "weekly" })); __host_call("nobody.home", 13, "{}"); }
                if (event === "onSubscribe") { __host_send("store.subscribe", JSON.stringify({ key: "cart" })); __host_send("store.subscribe", JSON.stringify({ key: "cart" })); __host_send("events.subscribe", JSON.stringify({ topic: "net" })); __host_send("events.subscribe", JSON.stringify({ topic: "net" })); patches.push(["p",1,"text","subscribed"]); }
                if (event === "onSet") __host_send("store.set", JSON.stringify({ key: "cart", value: payloadJson }));
                if (event === "onQueries") patches.push(["p",1,"text", [__host_query("i18n.locale", "{}"), __host_query("i18n.t", JSON.stringify({ key: "missing" })), __host_query("session.get", "{}"), __host_query("storage.set", JSON.stringify({ key: "k", value: "1" }))].join("|")]);
                if (event === "onFramework") {
                    __host_send("analytics.track", JSON.stringify({ name: "tap", props: { n: 2, ok: true, s: "x" } }));
                    __host_call("http.request", 14, JSON.stringify({ channel: "app", method: "GET", url: "/gone" }));
                    __host_call("http.request", 15, JSON.stringify({ channel: "nope", method: "GET", url: "/x" }));
                    __host_call("session.signIn", 16, "{}");
                    __host_call("clipboard.setText", 17, "{}");
                    __host_call("clipboard.setText", 18, JSON.stringify({ text: "ORD-1024" }));
                }
                // 2026-01-01 and 2026-07-01 UTC: either side of daylight saving
                if (event === "onZone") patches.push(["p",1,"text", [__host_query("i18n.timeZone", "{}"), __host_query("i18n.zoneOffset", JSON.stringify({ ms: 1767225600000 })), __host_query("i18n.zoneOffset", JSON.stringify({ ms: 1782864000000 }))].join("|")]);
                if (event === "onI18nSubscribe") { __host_send("i18n.subscribe", "{}"); patches.push(["p",1,"text","subscribed"]); }
                if (event === "onTagged") { __host_call("http.request", 21, JSON.stringify({ channel: "app", method: "POST", url: "/code", body: {}, tags: { captchaScene: "signup" } })); __host_call("http.request", 22, JSON.stringify({ channel: "app", method: "GET", url: "/code", tags: { n: 1 } })); }
                if (event === "onEncrypt") { __host_call("crypto.rsaEncrypt", 23, JSON.stringify({ text: "s3cret", publicKey: payloadJson })); __host_call("crypto.rsaEncrypt", 24, JSON.stringify({ text: "s3cret", publicKey: "bm9wZQ==" })); }
                if (event === "onNavigate") { __host_send("navigation.push", JSON.stringify({ page: "pages/detail", params: { id: 42 } })); __host_send("navigation.pop", JSON.stringify({ result: { saved: true } })); patches.push(["p",1,"text","navigated"]); }
                if (event === "onSeq") { seq.push(JSON.parse(payloadJson).i); patches.push(["p",1,"text","seq " + seq.length + " " + (seq.every((v, i) => v === i) ? "ordered" : "shuffled")]); }
            },
            emit(topic, json) { if (topic === "probe") __host_send("navigation.pop", JSON.stringify({ result: "probe" })); emits++; patches.push(["p",1,"text", topic + " " + json + " #" + emits]); },
            flush() { if (count > 2) throw new Error("render exploded"); if (patches.length) { __host_apply(JSON.stringify(patches)); patches = []; } },
        };
        export const VERSION = "stub";
    """)
    private val native = module("tinyui-native", "export const NATIVE = 1;")
    private val page = PageModule(
        "pages/counter", module("pages/counter", "export default function Counter() { return 'page'; }"), "abcd1234",
        PackageI18n("en", mapOf("en" to mapOf("hi" to "hello-hi"))),
    )

    private val tinyui = TinyUIHost(
        ComponentRegistry().registerBuiltins(), sink,
        CapabilityRegistry().apply {
            register("checkout.start") { args, page ->
                if (args.contains("annual")) """{"url":"https://pay/${page[Merchant]}/${page.name}"}""" else throw HostException("E_INVALID", "no such plan")
            }
        },
        channels = mapOf("app" to HttpChannel { request ->
            if (request.url == "/code") return@HttpChannel HttpResponse(200, emptyMap(), "\"${request.tags["captchaScene"]}\"")
            if (request.url == "/todos") HttpResponse(200, mapOf("content-type" to "application/json"), """[{"id":1}]""") else HttpResponse(404, emptyMap(), """{"gone":true}""")
        }),
        locale = MutableStateFlow("en"),
        timeZone = zoneFlow,
        analytics = AnalyticsSink { name, props, page -> tracked += "${page.name} $name $props" },
        navigator = object : Navigator {
            override fun push(page: String, paramsJson: String, from: PageContext) { navigated += "push $page $paramsJson from ${from.name}/${from[Merchant]}" }
            override fun pop(resultJson: String?, from: PageContext) { navigated += "pop $resultJson from ${from.name}/${from[Merchant]}" }
        },
    ).apply { store.setJson("cart", """{"n":2}""") }
    private val tracked = mutableListOf<String>()
    private val navigated = mutableListOf<String>()
    private val store = tinyui.store

    private fun host(props: String = "{}", maps: SourceMaps = SourceMaps.EMPTY, app: TinyUIHost = tinyui) =
        PageHost(RuntimeBundle(core, native), page, app, props, listOf(Merchant provides "acme"), sourceMaps = maps)

    // runTest's virtual time never advances the real engine thread: wait on a real dispatcher
    private suspend fun PageHost.await(check: () -> Boolean) =
        withContext(Dispatchers.Default) {
            try {
                withTimeout(5_000) {
                    while (!check()) {
                        failure?.let { if (!check()) error("page failed: ${it.message}\n${log.joinToString("\n")}") }
                        delay(10)
                    }
                }
            } catch (e: TimeoutCancellationException) {
                error("timed out; text=${tree.node(1)?.props?.get("text")} log=${log.joinToString(" | ")}")
            }
        }

    @Test
    fun mountsThroughTheEngineAndRendersFromTheTree() = runTest {
        val host = host("""{"name":"tinyui"}""")
        host.start()
        host.await { host.tree.root.children.isNotEmpty() }
        assertEquals("hello tinyui / 17", host.tree.node(1)!!.props["text"])
        assertNull(host.failure)
        host.close()
    }

    @Test
    fun dispatchIsATransactionEndingInFlush() = runTest {
        val host = host()
        host.start()
        host.await { host.tree.root.children.isNotEmpty() }
        host.dispatch(2, "onClick", "{}")
        host.await { host.tree.node(1)!!.props["text"] == "count 1" }
        host.dispatch(2, "onClick", "{}")
        host.await { host.tree.node(1)!!.props["text"] == "count 2" }
        assertNull(host.failure)
        host.close()
    }

    @Test
    fun eventsReachThePageInTheOrderTheyWereDispatched() = runTest {
        val host = host()
        host.start()
        host.await { host.tree.root.children.isNotEmpty() }
        // onChange then onComplete from one keystroke: the page must never see them swapped
        withContext(Dispatchers.Default) { repeat(500) { host.dispatch(2, "onSeq", """{"i":$it}""") } }
        host.await { (host.tree.node(1)!!.props["text"] as String).startsWith("seq 500") }
        assertEquals("seq 500 ordered", host.tree.node(1)!!.props["text"])
        host.close()
    }

    @Test
    fun tagsReachTheChannelAndMustBeStrings() = runTest {
        val host = host()
        host.start()
        host.await { host.tree.root.children.isNotEmpty() }
        host.dispatch(2, "onTagged", "{}")
        host.await { (host.tree.node(1)!!.props["text"] as String).count { it == ';' } == 1 }
        val text = host.tree.node(1)!!.props["text"] as String
        assertTrue(text.contains("ok 21") && text.contains("signup"), text)
        assertTrue(text.contains("rejected 22") && text.contains("E_INVALID"), text)
        host.close()
    }

    @Test
    fun navigationTellsTheNavigatorWhichMountCalled() = runTest {
        val host = host()
        host.start()
        host.await { host.tree.root.children.isNotEmpty() }
        host.dispatch(2, "onNavigate", "{}")
        host.await { host.tree.node(1)!!.props["text"] == "navigated" }
        // the locals the page was mounted with come back: that is how a host finds the caller's own entry
        assertEquals(
            listOf("""push pages/detail {"id":42} from pages/counter/acme""", """pop {"saved":true} from pages/counter/acme"""),
            navigated,
        )
        host.close()
    }

    @Test
    fun aResultEmittedBeforeTheMountArrivesAfterIt() = runTest {
        val host = host()
        // a page remounted from its props gets navigation.result while it is still loading
        host.emit("navigation.result", """{"result":{"saved":true},"from":"pages/detail"}""")
        host.start()
        host.await { host.tree.node(1)?.props?.get("text") == """navigation.result {"result":{"saved":true},"from":"pages/detail"} #1""" }
        host.close()
    }

    @Test
    fun aPageWhoseMountFailedTakesNoWaitingEntry() = runTest {
        val host = host("not json") // the stub's mount parses props: it throws once entries are already set
        host.emit("probe", "{}") // the stub answers it with navigation.pop: visible if it reaches the engine
        host.start()
        host.await { host.failure != null }
        delay(200)
        assertEquals("E6", host.failure!!.kind)
        assertEquals(emptyList(), navigated)
        host.close()
    }

    @Test
    fun rsaEncryptGoesThroughTheBridge() = runTest {
        val host = host()
        host.start()
        host.await { host.tree.root.children.isNotEmpty() }
        host.dispatch(2, "onEncrypt", RsaTestKeys.PUBLIC)
        host.await { (host.tree.node(1)!!.props["text"] as String).count { it == ';' } == 1 }
        val text = host.tree.node(1)!!.props["text"] as String
        // 256 bytes of ciphertext are 344 base64 characters, quoted
        assertTrue(Regex("ok 23 \"[A-Za-z0-9+/=]{344}\"").containsMatchIn(text), text)
        assertTrue(text.contains("rejected 24") && text.contains("E_INVALID"), text)
        host.close()
    }

    @Test
    fun timersComeBackThroughResolveAndE1ReportsKeepTheirFields() = runTest {
        val host = host()
        host.start()
        host.await { host.tree.root.children.isNotEmpty() }
        host.dispatch(2, "onTimer", "{}")
        host.await { host.tree.node(1)!!.props["text"] == "timer 7" }
        host.dispatch(2, "onBoom", "{}")
        host.await { errors.any { it.kind == "E1" } }
        val e = errors.single { it.kind == "E1" }
        assertEquals("boom", e.message)
        assertEquals("dispatch", e.entry)
        assertEquals("pages/counter", e.page)
        assertEquals("abcd1234", e.buildId)
        val frame = e.frames.first()
        assertEquals("dispatch", frame.function)
        assertEquals("tinyui-core", frame.file, "engine frames name the module; stack=${e.jsStack}")
        assertEquals(15, frame.line)
        assertNotNull(frame.column, "bytecode compiled with --strip-source keeps columns; stack=${e.jsStack}")
        host.close()
    }

    @Test
    fun framesAreMappedThroughTheModuleSourceMap() = runTest {
        // generated line 15 → src/core.ts line 3 column 7: fourteen empty groups, then one segment [0, 0, 2, 6]
        val map = SourceMaps(mapOf("tinyui-core" to """{"version":3,"sources":["src/core.ts"],"mappings":";;;;;;;;;;;;;;AAEM"}"""))
        val host = host(maps = map)
        host.start()
        host.await { host.tree.root.children.isNotEmpty() }
        host.dispatch(2, "onBoom", "{}")
        host.await { errors.any { it.kind == "E1" } }
        val frame = errors.single { it.kind == "E1" }.frames.first()
        assertEquals(true, frame.mapped)
        assertEquals("src/core.ts", frame.file)
        assertEquals(3, frame.line)
        assertEquals(7, frame.column)
        host.close()
    }

    @Test
    fun unhandledRejectionsAreE7() = runTest {
        val host = host()
        host.start()
        host.await { host.tree.root.children.isNotEmpty() }
        host.dispatch(2, "onReject", "{}")
        host.await { errors.any { it.kind == "E7" } }
        val e = errors.single { it.kind == "E7" }
        assertEquals(true, "nobody catches" in e.message, e.message)
        assertEquals(true, e.frames.isNotEmpty(), "stack=${e.jsStack}")
        assertNull(host.failure)
        host.close()
    }

    @Test
    fun hostServicesAnswerJ2J3J4AndK5() = runTest {
        val host = host()
        host.start()
        host.await { host.tree.root.children.isNotEmpty() }
        val text = { host.tree.node(1)!!.props["text"] as? String }
        host.dispatch(2, "onQuery", "{}")
        host.await { text() == """{"n":2}|"hello-hi"|${platformInfo()["os"]}""" }
        host.dispatch(2, "onHttp", "{}")
        host.await { text() == """http {"status":200,"headers":{"content-type":"application/json"},"body":[{"id":1}]}""" }
        host.dispatch(2, "onCapability", "{}")
        host.await { text()?.count { it == ';' } == 2 }
        assertEquals(
            """ok 11 {"url":"https://pay/acme/pages/counter"};rejected 12 {"code":"E_INVALID","message":"no such plan"};rejected 13 {"code":"E_UNSUPPORTED","message":"nobody.home is not available"}""",
            text(),
        )
        host.dispatch(2, "onSubscribe", "{}")
        host.await { text() == "subscribed" }
        tinyui.events.emit("net", """{"online":false}""")
        host.await { text() == """net {"online":false} #1""" }
        host.dispatch(2, "onSet", """{"n":3}""")
        host.await { text() == """store:cart {"value":{"n":3}} #2""" }
        assertEquals("""{"n":3}""", store.getJson("cart"))
        delay(100)
        assertEquals("""store:cart {"value":{"n":3}} #2""", text(), "two subscribe calls still deliver each change once")
        host.close()
    }

    @Test
    fun frameworkInterfacesWorkWithoutTheHostImplementingThem() = runTest {
        val host = host()
        host.start()
        host.await { host.tree.root.children.isNotEmpty() }
        val text = { host.tree.node(1)!!.props["text"] as? String }
        host.dispatch(2, "onFramework", "{}")
        host.await { text()?.count { it == ';' } == 4 }
        assertEquals(
            """rejected 14 {"status":404,"headers":{},"body":{"gone":true},"code":"E_HTTP","message":"HTTP 404"};""" +
                """rejected 15 {"code":"E_UNSUPPORTED","message":"the host has no http channel \"nope\""};""" +
                """rejected 16 {"code":"E_UNSUPPORTED","message":"the host provides no session"};""" +
                """rejected 17 {"code":"E_INVALID","message":"clipboard.setText needs text"};""" +
                """rejected 18 {"code":"E_UNSUPPORTED","message":"the page is not on screen"}""",
            text(),
        )
        assertEquals(listOf("pages/counter tap {n=2, ok=true, s=x}"), tracked)
        host.close()
    }

    @Test
    fun frameworkQueriesAnswerWithDefaultsWhereTheHostGaveNothing() = runTest {
        val host = host()
        host.start()
        host.await { host.tree.root.children.isNotEmpty() }
        val text = { host.tree.node(1)!!.props["text"] as? String }
        host.dispatch(2, "onQueries", "{}")
        host.await { text()?.startsWith("\"en\"") == true }
        assertEquals(""""en"|"missing"|{"loggedIn":false,"userId":null}|"E_UNSUPPORTED"""", text(), "no session, no data directory")
        host.close()
    }

    @Test
    fun timeZoneQueriesFollowTheHostZoneIncludingDaylightSaving() = runTest {
        val host = host()
        host.start()
        host.await { host.tree.root.children.isNotEmpty() }
        val text = { host.tree.node(1)!!.props["text"] as? String }
        host.dispatch(2, "onZone", "{}")
        host.await { text() == """"America/New_York"|-300|-240""" }
        host.dispatch(2, "onI18nSubscribe", "{}")
        // the current locale and zone arrive first, in either order
        host.await { text()?.endsWith("#2") == true }
        zoneFlow.value = "Asia/Tokyo"
        host.await { text() == """i18n.timeZone {"timeZone":"Asia/Tokyo"} #3""" }
        host.dispatch(2, "onZone", "{}")
        host.await { text() == """"Asia/Tokyo"|540|540""" }
        zoneFlow.value = "Nowhere/Atlantis"
        host.await { text() == """i18n.timeZone {"timeZone":"Nowhere/Atlantis"} #4""" }
        host.dispatch(2, "onZone", "{}")
        host.dispatch(2, "onZone", "{}")
        val system = systemTimeZone()
        host.await { text() == """"Nowhere/Atlantis"|${zoneOffsetMinutes(system, 1767225600000)}|${zoneOffsetMinutes(system, 1782864000000)}""" }
        assertEquals(1, log.count { "unknown time zone \"Nowhere/Atlantis\"" in it }, "reported once, shown in the system zone")
        host.close()
    }

    @Test
    fun withoutAHostZoneTheSystemZoneIsCheckedAgainWhenThePageComesBack() = runTest {
        val system = MutableStateFlow("Europe/London")
        val app = TinyUIHost(ComponentRegistry().registerBuiltins(), sink).apply { systemZone = { system.value } }
        val host = host(app = app)
        host.start()
        host.await { host.tree.root.children.isNotEmpty() }
        val text = { host.tree.node(1)!!.props["text"] as? String }
        host.dispatch(2, "onI18nSubscribe", "{}")
        host.await { text() == "subscribed" }
        host.visible(false)
        host.visible(true)
        host.dispatch(2, "onZone", "{}")
        host.await { text() == """"Europe/London"|0|60""" }
        system.value = "Asia/Tokyo"
        host.visible(false)
        host.visible(true)
        // #1: coming back with the zone unchanged sent nothing
        host.await { text() == """i18n.timeZone {"timeZone":"Asia/Tokyo"} #1""" }
        host.dispatch(2, "onZone", "{}")
        host.await { text() == """"Asia/Tokyo"|540|540""" }
        host.close()
    }

    @Test
    fun renderErrorsFailThePage() = runTest {
        val host = host()
        host.start()
        host.await { host.tree.root.children.isNotEmpty() }
        repeat(3) { host.dispatch(2, "onClick", "{}") }
        host.await { host.failure != null }
        val failure = assertNotNull(host.failure)
        assertEquals("E2", failure.kind)
        assertEquals(true, "render exploded" in failure.message)
        assertEquals(true, failure.error.frames.any { it.function == "flush" && it.file == "tinyui-core" }, "stack=${failure.error.jsStack}")
        assertEquals(1, errors.count { it.kind == "E2" }, "E2 goes through the sink once")
        host.close()
    }

    private fun module(name: String, source: String): ByteArray =
        JsBytecode.compile(source.trimIndent(), name, module = true, strip = JsBytecode.Strip.SOURCE)
}

private val Merchant = PageLocal<String>()

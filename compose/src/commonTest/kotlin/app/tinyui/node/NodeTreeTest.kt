package app.tinyui.node

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.Color
import app.tinyui.schema.ColorValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tinyui.components.registerBuiltins
import app.tinyui.schema.ComponentRegistry
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NodeTreeTest {
    private val problems = mutableListOf<PatchProblem>()
    private val tree = NodeTree(ComponentRegistry().registerBuiltins()) { problems += it }

    private fun ids(node: UINode) = node.children.map { it.id }

    @Test
    fun mountsTheCounterTree() {
        tree.apply("""[["c",1,"Text"],["p",1,"text","Count: 0"],["c",2,"Button"],["p",2,"text","+1"],["p",2,"onClick",true],["c",3,"Column"],["i",3,1,0],["i",3,2,1],["i",0,3,0]]""")
        assertEquals(listOf(3), ids(tree.root))
        assertEquals(listOf(1, 2), ids(tree.node(3)!!))
        assertEquals("Count: 0", tree.node(1)!!.props["text"])
        assertEquals(true, tree.node(2)!!.events["onClick"])
        assertTrue(problems.isEmpty(), problems.toString())
    }

    @Test
    fun convertsPropsOnWriteAndRestoresDefaultsOnNull() {
        tree.apply("""[["c",1,"Text"],["p",1,"text","t"],["p",1,"color","#FF0000"],["p",1,"fontSize",18],["i",0,1,0]]""")
        val node = tree.node(1)!!
        assertEquals(ColorValue.Literal(Color(0xFFFF0000)), node.props["color"])
        tree.apply("""[["p",1,"weight",2],["p",1,"borderWidth",1.5],["p",1,"borderColor","outline"],["p",1,"paddingHorizontal",6],["p",1,"scroll",true]]""")
        assertEquals(2.0, node.props["weight"])
        assertEquals(1.5.dp, node.props["borderWidth"])
        assertEquals(6.dp, node.props["paddingHorizontal"])
        assertEquals(ColorValue.Token("outline"), node.props["borderColor"])
        assertEquals(1, problems.size, "scroll is not a Text prop: ${problems}")
        problems.clear()
        assertEquals(18.sp, node.props["fontSize"])
        tree.apply("""[["p",1,"color",null]]""")
        assertNull(node.props["color"], "null restores the schema default, which Text.color does not have")
        assertTrue(problems.isEmpty(), problems.toString())
    }

    @Test
    fun moveRemoveAndCommandsFollowTheProtocol() {
        tree.apply("""[["c",1,"Column"],["c",2,"Text"],["p",2,"text","a"],["c",3,"Text"],["p",3,"text","b"],["c",4,"Text"],["p",4,"text","c"],["i",1,2,0],["i",1,3,1],["i",1,4,2],["i",0,1,0]]""")
        tree.apply("""[["m",1,4,0]]""")
        assertEquals(listOf(4, 2, 3), ids(tree.node(1)!!))
        tree.apply("""[["r",2]]""")
        assertEquals(listOf(4, 3), ids(tree.node(1)!!))
        assertNull(tree.node(2))
        tree.apply("""[["r",1]]""")
        assertEquals(1, tree.size, "removing a subtree forgets every descendant")
        assertTrue(problems.isEmpty(), problems.toString())
    }

    @Test
    fun rootIsOnlyEverAParentAndCyclesAreRejected() {
        tree.apply("""[["c",1,"Column"],["c",2,"Column"],["i",1,2,0],["i",0,1,0]]""")
        tree.apply("""[["r",0],["i",2,0,0],["i",2,1,0],["i",1,1,0],["p",0,"text","x"],["m",1,2,7]]""")
        assertEquals(listOf("the root container is not a child", "the root container is not a child", "i: node already has a parent", "i: node already has a parent", "the root container is not a child", "index 7 out of [0, 0], clamped"),
            problems.map { it.reason })
        assertEquals(listOf(1), ids(tree.root))
        assertEquals(listOf(2), ids(tree.node(1)!!))
        problems.clear()
        tree.apply("""[["m",1,2]]""")
        assertEquals("missing index", problems.single().reason)
        assertEquals(listOf(2), ids(tree.node(1)!!), "a rejected move leaves the list untouched")
    }

    @Test
    fun initialPropsOnlyCountAtCreationAndCommandArgsAreValidated() {
        tree.apply("""[["c",1,"TextField"],["p",1,"initialText","a"],["i",0,1,0]]""")
        assertEquals("a", tree.node(1)!!.props["initialText"])
        tree.apply("""[["p",1,"initialText","b"],["p",1,"placeholder","hint"]]""")
        assertEquals("a", tree.node(1)!!.props["initialText"], "later writes to an initial prop are ignored")
        assertEquals("hint", tree.node(1)!!.props["placeholder"])
        assertEquals(1, problems.size); assertTrue("initial prop" in problems.single().reason)
        problems.clear()
        tree.apply("""[["x",1,"setText",{"text":"z"}],["x",1,"setText",{}],["x",1,"setText",{"text":3}],["x",1,"focus",{}]]""")
        val sent = received(tree.node(1)!!)
        assertEquals(listOf("setText", "focus"), sent.map { it.name })
        assertEquals("z", sent[0].args["text"])
        assertEquals(2, problems.size)
    }

    @Test
    fun receivingCommandsNeverConflictsWithAFlushBeingApplied() {
        tree.apply("""[["c",1,"TextField"],["i",0,1,0],["x",1,"setText",{"text":"a"}]]""")
        val node = tree.node(1)!!
        // the main thread takes a command inside its own snapshot while the engine thread applies the next flush:
        // with commands as snapshot state both wrote the same list, and the later apply failed with a conflict
        val main = Snapshot.takeMutableSnapshot()
        val first = main.enter { node.commands.tryReceive().getOrNull() }
        tree.apply("""[["x",1,"focus",{}]]""")
        main.apply().check()
        main.dispose()
        assertEquals("setText", first?.name)
        assertEquals(listOf("focus"), received(node).map { it.name })
    }

    @Test
    fun aCommandArrivesOnlyAfterItsFlushIsApplied() = runTest {
        tree.apply("""[["c",1,"TextField"],["i",0,1,0]]""")
        val node = tree.node(1)!!
        val events = ArrayList<String>()
        // runs on the spot, inside the send: if the command went out before the commit, it would come first
        val receiver = launch(UnconfinedTestDispatcher(testScheduler)) { node.commands.receive(); events += "received" }
        val applied = Snapshot.registerApplyObserver { changed, _ -> if (node.props in changed) events += "applied" }
        tree.apply("""[["x",1,"focus",{}],["p",1,"placeholder","hint"]]""")
        applied.dispose()
        receiver.cancel()
        assertEquals(listOf("applied", "received"), events)
    }

    @Test
    fun anApplyReenteredFromReportJoinsTheFlushItInterrupted() = runTest {
        var reentered = false
        lateinit var reentrant: NodeTree
        reentrant = NodeTree(ComponentRegistry().registerBuiltins()) {
            if (!reentered) { reentered = true; reentrant.apply("""[["x",1,"setText",{"text":"inner"}]]""") }
        }
        reentrant.apply("""[["c",1,"TextField"],["i",0,1,0]]""")
        val node = reentrant.node(1)!!
        // each command as it arrives, with what the tree shows at that moment
        val seen = ArrayList<String>()
        val receiver = launch(UnconfinedTestDispatcher(testScheduler)) {
            for (c in node.commands) seen += "${c.name}:${node.props["placeholder"]}"
        }
        // the unknown command reports, the sink reenters; the placeholder comes after both
        reentrant.apply("""[["x",1,"focus",{}],["x",1,"nope",{}],["p",1,"placeholder","hint"]]""")
        receiver.cancel()
        assertTrue(reentered)
        assertEquals(listOf("focus:hint", "setText:hint"), seen)
    }

    @Test
    fun aRemovedNodeGetsNoMoreCommands() {
        tree.apply("""[["c",1,"TextField"],["i",0,1,0],["c",2,"TextField"],["i",0,2,1]]""")
        val first = tree.node(1)!!
        val second = tree.node(2)!!
        // one still waiting from an earlier flush, one sent in the flush that removes the node
        tree.apply("""[["x",1,"focus",{}]]""")
        tree.apply("""[["x",1,"setText",{"text":"a"}],["r",1],["x",2,"focus",{}]]""")
        assertTrue(first.commands.isClosedForReceive)
        assertEquals(listOf("focus"), received(second).map { it.name })
    }

    private fun received(node: UINode): List<Command> = generateSequence { node.commands.tryReceive().getOrNull() }.toList()

    @Test
    fun requiredPropsAreCheckedWhenTheCreatingFlushEnds() {
        tree.apply("""[["c",1,"Text"],["i",0,1,0]]""")
        assertEquals("required prop text was not set in the creating flush", problems.single().reason)
        problems.clear()
        tree.apply("""[["c",3,"Text"],["p",3,"text",null],["i",0,3,1]]""")
        assertEquals(listOf("text is required on Text; null is not allowed", "required prop text was not set in the creating flush"), problems.map { it.reason })
        problems.clear()
        tree.apply("""[["c",2,"Button"],["p",2,"text","ok"],["i",0,2,1]]""")
        assertTrue(problems.isEmpty(), problems.toString())
    }

    @Test
    fun layoutPropsApplyToEveryLayoutComponent() {
        tree.apply("""[["c",1,"Spacer"],["p",1,"width","fill"],["p",1,"height",12],["p",1,"padding",4],["i",0,1,0]]""")
        val node = tree.node(1)!!
        assertEquals(app.tinyui.schema.SizeValue.Fill, node.props["width"])
        assertEquals(app.tinyui.schema.SizeValue.Fixed(12.dp), node.props["height"])
        assertEquals(4.dp, node.props["padding"])
        assertTrue(problems.isEmpty(), problems.toString())
    }

    @Test
    fun badOpsAreSkippedAndReported() {
        tree.apply("""[["c",1,"Text"],["p",1,"text","t"],["p",1,"nope","x"],["p",1,"fontSize","big"],["p",1,"onTap",true],["p",99,"text","x"],["c",2,"pp.Unknown"],["i",0,2,7],["x",1,"focus",{}],["i",0,1,0]]""")
        val expected = listOf("prop not in schema", "cannot convert", "event not in schema", "unknown node 99", "unknown component type", "index 7 out of", "command not in schema")
        assertEquals(expected.size, problems.size, problems.toString())
        for ((reason, prefix) in problems.map { it.reason }.zip(expected)) assertTrue(reason.startsWith(prefix), "$reason should start with $prefix")
        assertEquals("pp.Unknown", tree.node(2)!!.type, "the requested type stays; rendering falls back to Placeholder")
        assertEquals(listOf(1, 2), ids(tree.root), "the clamped insert (7 → 0 on an empty root) still lands")
        tree.apply("not json")
        assertEquals("message is not a JSON array", problems.last().reason.substringBefore(" ("))
    }
}

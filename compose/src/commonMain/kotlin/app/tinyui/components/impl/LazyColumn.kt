package app.tinyui.components.impl

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tinyui.node.UINode
import app.tinyui.schema.Commands
import app.tinyui.schema.NodeScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * What a list makes of its children (docs/components.md §3): the [rows], which are the children but for an IndexBar;
 * that [bar], which floats over them (the first, when the page wrote more than one: [bars]); and which rows are
 * [headers] the list pins. The children themselves are the rows when there is no bar, so a plain list allocates
 * nothing here.
 */
internal class ListShape(val rows: List<UINode>, val bar: UINode?, val bars: Int, val headers: IntArray) {
    /** The header pinned over row [index] once it is at the top: the last one before it. -1 for a header, or with none before. */
    fun headerOver(index: Int): Int {
        var over = -1
        for (header in headers) if (header < index) over = header else return if (header == index) -1 else over
        return over
    }
}

private val NoHeaders = IntArray(0)

/** One pass over the children; everything the list needs to know of them comes from here, once per change. */
private fun shapeOf(node: UINode): ListShape {
    // the persistent list under the state list: iterating the state list itself reads snapshot state at every step
    val children = node.children.toList()
    var bar: UINode? = null
    var bars = 0
    // only once a bar is met: the children before it, then every row after
    var rows: ArrayList<UINode>? = null
    var headers: ArrayList<Int>? = null
    var row = 0
    for (child in children) {
        val type = child.type
        if (type == INDEX_BAR) {
            if (bar == null) bar = child
            if (rows == null) rows = ArrayList<UINode>(children.size).also { it.addAll(children.subList(0, row)) }
            bars++
            continue
        }
        // `sticky` is on unless the page turned it off
        if (type == SECTION_HEADER && child.props["sticky"] != false) (headers ?: ArrayList<Int>().also { headers = it }).add(row)
        rows?.add(child)
        row++
    }
    return ListShape(rows = rows ?: children, bar = bar, bars = bars, headers = headers?.toIntArray() ?: NoHeaders)
}

/**
 * Rows are the node's children; virtualisation only in composition (docs/adr-003 §3.6). A sticky SectionHeader among
 * them is pinned by the list; an IndexBar child is not a row but a strip over the list's end edge, drawn and driven here.
 */
@Composable
internal fun LazyColumnComponent(scope: NodeScope) {
    val state = retain { LazyListState() }
    val node = scope.node
    // each scroll in a job of its own, started where the command is taken so they begin in order. A command must not
    // wait for the animation of the one before it: the list's scroll mutex hands over to the later one, the last
    // command wins. And a scroll that is interrupted (by that later command, by the user's finger) ends with a
    // cancellation, which must end that scroll only, not the loop that takes commands
    val scrolls = rememberCoroutineScope()
    // worked out once each time the children change; the item provider, the commands, the end detection and the
    // index strip all read this one
    val shape = remember(node) { derivedStateOf { shapeOf(node) } }
    scope.Commands { command ->
        val index = ((command.args["index"] as? Double) ?: 0.0).toInt().coerceIn(0, maxOf(0, shape.value.rows.size - 1))
        if (command.name == "scrollTo") scrolls.launch(start = CoroutineStart.UNDISPATCHED) { state.showRow(shape, index, animated = true) }
    }
    // with the scroll position: a list coming back still at its end has already reported that length
    val reachedEnd = retain { ReachedEnd() }
    if (scope.has("onReachEnd")) {
        // fires on each arrival at the last row and when the list grows while there; re-arms once the user scrolls away
        LaunchedEffect(state, node) {
            snapshotFlow { (state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) to shape.value.rows.size }
                .distinctUntilChanged()
                .collect { (last, size) ->
                    val atEnd = size > 0 && last >= size - 1
                    if (!atEnd) reachedEnd.size = -1
                    else if (reachedEnd.size != size) { reachedEnd.size = size; scope.dispatch("onReachEnd") }
                }
        }
    }
    if (scope.has("onScrollEnd")) {
        LaunchedEffect(state) {
            var scrolling = false
            snapshotFlow { state.isScrollInProgress }.distinctUntilChanged().collect { now ->
                if (scrolling && !now) scope.dispatch("onScrollEnd", buildJsonObject { put("index", state.firstVisibleItemIndex) }.toString())
                scrolling = now
            }
        }
    }
    val gap = scope.get<Dp>("gap") ?: 0.dp
    val list = @Composable { modifier: Modifier ->
        LazyColumn(state = state, modifier = modifier, verticalArrangement = if (gap > 0.dp) Arrangement.spacedBy(gap) else Arrangement.Top) {
            // one immutable copy of the rows per item provider, not the live list: a measure pass takes its provider
            // once, then asks it for the count and for each row by index, outside any snapshot, while the engine
            // thread may apply the next patch in between. Taken in here rather than in the composition: this block is
            // the provider's derived state, recomputed at the next measure after the rows change, so a command of the
            // same flush (which runs before the recomposition that flush causes) already finds the new rows
            val now = shape.value
            val rows = now.rows
            // a list without headers is the single run of items it always was
            if (now.headers.isEmpty()) items(rows, key = { it.id }) { child -> key(child.id) { scope.RenderChild(child) } }
            else {
                // a pinned header is an item of its own kind; the rows between two of them are one run
                var from = 0
                for (at in now.headers) {
                    if (at > from) rowRun(rows, from, at, scope)
                    val header = rows[at]
                    stickyHeader(key = header.id, contentType = SECTION_HEADER) { key(header.id) { scope.RenderChild(header) } }
                    from = at + 1
                }
                if (from < rows.size) rowRun(rows, from, rows.size, scope)
            }
        }
    }
    // read through a derived state: the list is recomposed when a bar comes or goes, not each time a row does
    val bar by remember(node) { derivedStateOf { shape.value.bar } }
    val bars by remember(node) { derivedStateOf { shape.value.bars } }
    if (bars > 1) LaunchedEffect(Unit) { scope.reportProblem("a LazyColumn takes one IndexBar; it has $bars, the first one is used") }
    // the strip needs a box around the list. Kept once there has been a bar, so that showing and hiding it (while
    // searching, say) does not move the list in the composition and rebuild its rows; a list that never has one is
    // composed exactly as before
    val boxed = remember(node) { booleanArrayOf(false) }
    if (bar != null) boxed[0] = true
    if (!boxed[0]) {
        list(scope.modifier())
        return
    }
    Layout(
        content = {
            list(Modifier)
            bar?.let { IndexBarStrip(it, shape, state, scrolls) }
        },
        modifier = scope.modifier(),
    ) { measurables, constraints ->
        // the list takes the constraints it would have had on its own; the strip floats over its end edge
        val listPlaceable = measurables[0].measure(constraints)
        val strip = measurables.getOrNull(1)?.measure(Constraints(maxWidth = listPlaceable.width, maxHeight = listPlaceable.height))
        layout(listPlaceable.width, listPlaceable.height) {
            listPlaceable.place(0, 0)
            strip?.placeRelative(listPlaceable.width - strip.width, (listPlaceable.height - strip.height) / 2)
        }
    }
}

/** The rows `[from, to)` as one run of items. */
private fun LazyListScope.rowRun(rows: List<UINode>, from: Int, to: Int, scope: NodeScope) {
    items(to - from, key = { rows[from + it].id }) { offset ->
        val child = rows[from + offset]
        key(child.id) { scope.RenderChild(child) }
    }
}

/** The height of a header pinned or on screen right now: the room to leave above a row that will have one over it. */
private fun LazyListState.pinnedHeaderSize(shape: ListShape): Int =
    layoutInfo.visibleItemsInfo.firstOrNull { item -> shape.headers.any { it == item.index } }?.size ?: 0

/**
 * Brings row [index] to the top of the list, and out from under the header pinned over it there: the row a page (or the
 * index strip) scrolled to is the row the user sees first.
 */
internal suspend fun LazyListState.showRow(shape: State<ListShape>, index: Int, animated: Boolean) {
    if (animated) {
        // the header over the row is not laid out until the list gets there: aim with the one pinned now, settle after
        val room = if (shape.value.headerOver(index) < 0) 0 else pinnedHeaderSize(shape.value)
        animateScrollToItem(index, -room)
    } else {
        scrollToItem(index)
    }
    // read again: rows may have come or gone while it animated
    val header = shape.value.headerOver(index)
    if (header < 0) return
    val visible = layoutInfo.visibleItemsInfo
    val pinned = visible.firstOrNull { it.index == header } ?: return
    val target = visible.firstOrNull { it.index == index } ?: return
    // under the header by this much, or (aimed with a taller header than its own) short of it by as much
    val off = pinned.offset + pinned.size - target.offset
    if (off != 0) scrollBy(-off.toFloat())
}

/** The list length `onReachEnd` last fired for; -1 once the user is away from the end. */
private class ReachedEnd(var size: Int = -1)

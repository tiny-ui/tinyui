package app.tinyui.components.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.tinyui.node.UINode
import app.tinyui.schema.NodeScope
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

internal const val INDEX_BAR = "IndexBar"

/** Drawn by the LazyColumn it is a direct child of ([IndexBarStrip]), never through here: anywhere else it has no list to index. */
@Composable
internal fun IndexBarComponent(scope: NodeScope) {
    LaunchedEffect(Unit) { scope.reportProblem("IndexBar only works as a direct child of LazyColumn") }
}

/** An index label's circle, the height of the cell it sits in when the strip has all the room it wants, and the strip's width. */
private val IndexLabelSize = 16.dp
private val IndexLabelPitch = 20.dp
private val IndexStripWidth = 40.dp

/**
 * This style at the size it has under the default system font size, whatever that setting is now. The index strip's
 * cells are fixed in dp and there are twenty-odd of them in one column: labels that grew with the font size would be
 * cut by their cells, or would not fit the strip at all. A screen reader does not go by the letters (the strip is one
 * adjustable element), and the list beside it scrolls and searches without the strip.
 */
internal fun TextStyle.unscaled(density: Density): TextStyle = with(density) {
    // sp is dp times the font scale: the dp of the same number, turned back into sp, is the size at scale 1
    val size = if (fontSize.isSp) fontSize.value else 12f
    copy(fontSize = size.dp.toSp(), lineHeight = if (lineHeight.isSp) lineHeight.value.dp.toSp() else lineHeight)
}

/**
 * What an IndexBar knows of its list: the labels it shows, the row each one takes the list to, and which label a row
 * belongs under. The groups are the list's SectionHeaders; one that carries an `index` answers to the label of that
 * value, one without is in no label's group. A label without a group of its own goes to the next label down that has
 * one, the last such group when none follows.
 */
private class IndexModel(
    val labels: List<Pair<String, String>>,
    private val own: IntArray,
    private val targets: IntArray,
    private val groupRows: IntArray,
    private val groupLabels: IntArray,
) {
    /** The row label [label] takes the list to; -1 when the list has no group at all. */
    fun rowOf(label: Int): Int = targets.getOrElse(label) { -1 }

    /** Whether [label] has a group of its own, rather than standing in for the next one down. */
    fun hasGroup(label: Int): Boolean = own.getOrElse(label) { -1 } >= 0

    /** The label of the group row [row] is in; -1 above the first group, or when that group has no label of its own. */
    fun labelAt(row: Int): Int {
        var group = -1
        for (i in groupRows.indices) if (groupRows[i] <= row) group = i else break
        return if (group < 0) -1 else groupLabels[group]
    }
}

private fun indexModel(bar: UINode, rows: List<UINode>): IndexModel {
    val groups = ArrayList<Pair<String?, Int>>()
    for (at in rows.indices) {
        val row = rows[at]
        if (row.type == SECTION_HEADER) groups += (row.props["index"] as? String) to at
    }
    val options = bar.options()
    val labels = options.ifEmpty { groups.mapNotNull { it.first }.distinct().map { it to it } }
    val own = IntArray(labels.size) { label -> groups.firstOrNull { it.first == labels[label].first }?.second ?: -1 }
    val last = groups.lastOrNull { it.first != null }?.second ?: -1
    val targets = IntArray(labels.size) { label -> (label until labels.size).firstOrNull { own[it] >= 0 }?.let { own[it] } ?: last }
    return IndexModel(
        labels = labels,
        own = own,
        targets = targets,
        groupRows = IntArray(groups.size) { groups[it].second },
        groupLabels = IntArray(groups.size) { group -> groups[group].first?.let { index -> labels.indexOfFirst { it.first == index } } ?: -1 },
    )
}

/** Where the strip left the list when it took it to the group of [label]: the first row in view and how far that is scrolled. */
private class Arrival(val model: IndexModel, val label: Int, val index: Int, val offset: Int)

/**
 * The IndexBar of the list with this [shape], over its end edge. Which cell the finger is on is tracked here and the list scrolled from
 * here: nothing crosses the bridge while it slides (docs/adr-004 §3.1). The highlight is the cell under the finger
 * while it is down; after that the group the strip took the list to, until the list moves again; otherwise the group
 * the list is at. Neither Compose nor M3 has this component: the sizes are the
 * framework's, the colours the theme's. Takes no room when its labels do not fit.
 */
@Composable
internal fun IndexBarStrip(bar: UINode, shape: State<ListShape>, state: LazyListState, scrolls: CoroutineScope) {
    val model by remember(bar, shape) { derivedStateOf { indexModel(bar, shape.value.rows) } }
    val current = rememberUpdatedState(model)
    var pressed by remember { mutableStateOf<Int?>(null) }
    // the group the strip last took the list to, and where that left the list. A group near the end cannot come to the
    // top, and the row first in view is then of an earlier group: the strip keeps showing the one it went to for as
    // long as the list has not moved since
    var arrived by remember { mutableStateOf<Arrival?>(null) }
    val at by remember(state) {
        derivedStateOf {
            val model = current.value
            val there = arrived
            if (there != null && there.model === model && there.index == state.firstVisibleItemIndex && there.offset == state.firstVisibleItemScrollOffset) there.label
            else model.labelAt(state.firstVisibleItemIndex)
        }
    }
    // forgotten the first time the list is anywhere else: coming back to the very same place later (the end of the
    // list, scrolled away from and back to) is the list's own doing
    LaunchedEffect(state) {
        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }.collect { (index, offset) ->
            val there = arrived
            if (there != null && (there.index != index || there.offset != offset)) arrived = null
        }
    }
    val highlighted = pressed ?: at
    fun go(label: Int) {
        val model = current.value
        val row = model.rowOf(label)
        if (row < 0) return
        scrolls.launch(start = CoroutineStart.UNDISPATCHED) {
            state.showRow(shape, row, animated = false)
            // the label of the group at that row: a label without a group of its own stood in for this one
            arrived = Arrival(model, model.labelAt(row), state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset)
        }
    }
    val labels = model.labels
    val name = bar.props["label"] as? String
    val labelStyle = MaterialTheme.typography.bodySmall.unscaled(LocalDensity.current)
    Layout(
        content = {
            for ((index, label) in labels.withIndex()) {
                val on = index == highlighted
                Box(
                    Modifier.size(IndexLabelSize).then(if (on) Modifier.background(MaterialTheme.colorScheme.primary, CircleShape) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label.second,
                        // at its own size, centred over the circle: a line taller than the circle (the host's line
                        // height is its own business) would otherwise be cut to it from the top, and sit low
                        modifier = Modifier.wrapContentSize(unbounded = true),
                        style = labelStyle,
                        color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        },
        modifier = Modifier
            // one adjustable element to a screen reader, not a button per label: it says the group the list is at
            // and steps through them
            .clearAndSetSemantics {
                if (name != null) contentDescription = name
                stateDescription = labels.getOrNull(highlighted)?.second ?: ""
                if (labels.size > 1) {
                    val from = highlighted.coerceAtLeast(0)
                    progressBarRangeInfo = ProgressBarRangeInfo(from.toFloat(), 0f..(labels.size - 1).toFloat(), labels.size - 2)
                    setProgress { value ->
                        // a step lands on a group, not on a label standing in for the next one: stepping back onto
                        // such a label would come straight back to where it started and never get past it
                        val to = value.roundToInt().coerceIn(0, labels.size - 1)
                        val step = if (to < from) -1 else 1
                        var label = to
                        while (label in labels.indices && !current.value.hasGroup(label)) label += step
                        if (label in labels.indices) go(label)
                        true
                    }
                } else if (labels.size == 1) {
                    // nothing to step between; the one label can still be activated
                    onClick { go(0); true }
                }
            }
            // everything is consumed: inside a sheet or a pager a slide along the strip must not drag the container
            .pointerInput(Unit) {
                awaitEachGesture {
                    fun reach(y: Float) {
                        val count = current.value.labels.size
                        if (count == 0 || size.height == 0) return
                        val label = (y / size.height * count).toInt().coerceIn(0, count - 1)
                        if (label != pressed) { pressed = label; go(label) }
                    }
                    val down = awaitFirstDown()
                    down.consume()
                    try {
                        reach(down.position.y)
                        drag(down.id) { change -> change.consume(); reach(change.position.y) }
                    } finally {
                        pressed = null
                    }
                }
            },
    ) { measurables, constraints ->
        val label = IndexLabelSize.roundToPx()
        // labels squeezed into less than their own size cannot be read or hit: no strip then (a keyboard is up, say)
        if (measurables.isEmpty() || constraints.maxHeight < label * measurables.size || constraints.maxWidth < label) return@Layout layout(0, 0) {}
        val placeables = measurables.map { it.measure(Constraints.fixed(label, label)) }
        val width = IndexStripWidth.roundToPx().coerceAtMost(constraints.maxWidth)
        val height = (IndexLabelPitch.roundToPx() * placeables.size).coerceAtMost(constraints.maxHeight)
        val cell = height.toFloat() / placeables.size
        layout(width, height) {
            placeables.forEachIndexed { index, placeable -> placeable.place((width - label) / 2, (index * cell + (cell - label) / 2).toInt()) }
        }
    }
}

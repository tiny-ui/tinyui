package app.tinyui.node

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel

/** One node of the page tree; the recomposition unit (docs/adr-003-kotlin-node-tree-and-registry.md). */
class UINode internal constructor(val id: Int, val type: String) {
    /** Typed prop values, converted on write by the component's schema. */
    val props: SnapshotStateMap<String, Any?> = mutableStateMapOf()
    /** Events the JS side registered a handler for. */
    val events: SnapshotStateMap<String, Boolean> = mutableStateMapOf()
    val children: SnapshotStateList<UINode> = mutableStateListOf()
    /**
     * One-shot commands for the component (`x` op), oldest first, each sent once its flush is applied. A queue, not
     * snapshot state (docs/adr-004 §3.2): the engine thread sends while the main thread receives, and a state list
     * written from both conflicts when either snapshot applies.
     */
    val commands: ReceiveChannel<Command> get() = queue
    internal val queue = Channel<Command>(Channel.UNLIMITED)
    internal var parent: UINode? = null
    /** Set once the creating flush is applied: `initial` props stop accepting writes. */
    internal var created = false
}

class Command(val name: String, val args: Map<String, Any?>)

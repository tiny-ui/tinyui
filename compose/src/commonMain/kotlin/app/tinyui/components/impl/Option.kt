package app.tinyui.components.impl

import androidx.compose.runtime.Composable
import app.tinyui.components.NodeScope
import app.tinyui.components.UINode

/** Data for its Select / Menu / Tabs / IndexBar, never drawn: they read it with [options]. */
@Composable
internal fun OptionComponent(scope: NodeScope) {}

/** The Options among this node's children, in order, each as its value to its text; a prop it does not carry reads as "". */
internal fun UINode.options(): List<Pair<String, String>> =
    children.filter { it.type == "Option" }.map { (it.props["value"] as? String ?: "") to (it.props["text"] as? String ?: "") }

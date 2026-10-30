package app.tinyui.node

import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import app.tinyui.schema.ComponentRegistry

const val ROOT_ID = 0

/** Reported for every op the tree could not apply (E5). The page keeps running. */
class PatchProblem(val op: String, val reason: String) {
    override fun toString(): String = "$reason: $op"
}

/**
 * The page's node table. Only the JS thread writes it, inside [apply]; the main thread only recomposes
 * (docs/adr-003-kotlin-node-tree-and-registry.md §3.2). Op semantics: docs/patch-protocol.md.
 */
class NodeTree(private val registry: ComponentRegistry, private val report: (PatchProblem) -> Unit) {
    val root = UINode(ROOT_ID, "root")
    private val nodes = HashMap<Int, UINode>().apply { put(ROOT_ID, root) }
    val size: Int get() = nodes.size

    fun node(id: Int): UINode? = nodes[id]

    /**
     * Applies one flush atomically: other threads see the tree before or after, never in between. Nothing leaves the
     * snapshot while it is open: removed nodes' queues are cancelled, commands sent and problems reported only once the
     * flush is applied, in that order. So a component never gets a command ahead of the props it came with, a flush that
     * fails to apply leaves no trace beyond its report, and a sink hears of a flush once it is complete: if it writes to
     * the tree from [report], that is a flush of its own, after this one.
     */
    fun apply(json: String) {
        val ops = try {
            Json.parseToJsonElement(json).jsonArray
        } catch (e: Exception) {
            report(PatchProblem(json.take(200), "message is not a JSON array (${e.message})"))
            return
        }
        val flush = Flush()
        try {
            Snapshot.withMutableSnapshot {
                for (op in ops) applyOp(op, flush)
                for (node in flush.born) {
                    node.created = true
                    registry.schema(node.type)?.props?.forEach { (key, spec) ->
                        if (spec.required && node.props[key] == null) flush.problem("[\"c\",${node.id},\"${node.type}\"]", "required prop $key was not set in the creating flush")
                    }
                }
            }
        } catch (t: Throwable) {
            // what went wrong is still worth hearing; nothing else of this flush happens
            for (p in flush.problems) report(p)
            throw t
        }
        // cancelled first: a command for a node removed in the same flush is dropped, not delivered
        for (node in flush.removed) node.commands.cancel()
        for ((node, command) in flush.sent) node.commands.trySend(command)
        for (p in flush.problems) report(p)
    }

    /** Drops every node like `r` would: their queues close, so nothing waits on a node that is gone. */
    fun clear() {
        for (node in nodes.values) if (node !== root) node.commands.cancel()
        root.children.clear()
        nodes.clear()
        nodes[ROOT_ID] = root
    }

    private fun applyOp(op: JsonElement, flush: Flush) {
        val fields = op as? JsonArray ?: return flush.problem(op.toString(), "op is not an array")
        val kind = fields.getOrNull(0)?.string() ?: return flush.problem(op.toString(), "op without kind")
        when (kind) {
            "c" -> create(fields, op, flush)
            "p" -> setProp(fields, op, flush)
            "i" -> insert(fields, op, flush)
            "m" -> move(fields, op, flush)
            "r" -> remove(fields, op, flush)
            "x" -> command(fields, op, flush)
            else -> flush.problem(op.toString(), "unknown op")
        }
    }

    private fun create(f: JsonArray, op: JsonElement, flush: Flush) {
        val id = f.int(1) ?: return flush.problem(op.toString(), "c without id")
        val type = f.string(2) ?: return flush.problem(op.toString(), "c without type")
        if (nodes.containsKey(id)) return flush.problem(op.toString(), "id already exists")
        // the requested type stays on the node: Render falls back to Placeholder, which shows it in debug
        if (registry.schema(type) == null) flush.problem(op.toString(), "unknown component type, rendering Placeholder")
        nodes[id] = UINode(id, type).also { flush.born += it }
    }

    private fun setProp(f: JsonArray, op: JsonElement, flush: Flush) {
        val node = child(f, op, flush, at = 1) ?: return
        val key = f.string(2) ?: return flush.problem(op.toString(), "p without key")
        val value = f.getOrNull(3) ?: JsonNull
        val schema = registry.schema(node.type) ?: return
        if (key.length > 2 && key.startsWith("on") && key[2].isUpperCase()) {
            if (key !in schema.events) return flush.problem(op.toString(), "event not in schema of ${node.type}")
            val flag = (value as? JsonPrimitive)?.booleanOrNull ?: return flush.problem(op.toString(), "event flag must be a boolean")
            if (flag) node.events[key] = true else node.events.remove(key)
            return
        }
        val spec = schema.prop(key) ?: return flush.problem(op.toString(), "prop not in schema of ${node.type}")
        if (spec.initial && node.created) return flush.problem(op.toString(), "$key is an initial prop of ${node.type}: writes after creation are ignored")
        if (value is JsonNull) {
            if (spec.required && spec.default == null) return flush.problem(op.toString(), "$key is required on ${node.type}; null is not allowed")
            node.props[key] = spec.default
            return
        }
        val primitive = value as? JsonPrimitive ?: return flush.problem(op.toString(), "prop value must be a scalar")
        val converted = spec.convert(primitive) ?: return flush.problem(op.toString(), "cannot convert ${primitive} to ${spec.kind} for ${node.type}.$key")
        node.props[key] = converted
    }

    private fun insert(f: JsonArray, op: JsonElement, flush: Flush) {
        val parent = f.int(1)?.let { nodes[it] } ?: return flush.problem(op.toString(), "i: unknown parent")
        val node = child(f, op, flush) ?: return
        if (node.parent != null) return flush.problem(op.toString(), "i: node already has a parent")
        if (node === parent || isAncestor(node, of = parent)) return flush.problem(op.toString(), "i: would create a cycle")
        val index = clamp(f.int(3), parent.children.size, op, flush) ?: return
        node.parent = parent
        parent.children.add(index, node)
    }

    private fun move(f: JsonArray, op: JsonElement, flush: Flush) {
        val parent = f.int(1)?.let { nodes[it] } ?: return flush.problem(op.toString(), "m: unknown parent")
        val node = child(f, op, flush) ?: return
        if (node.parent !== parent) return flush.problem(op.toString(), "m: node is not a child of parent")
        val index = clamp(f.int(3), parent.children.size - 1, op, flush) ?: return
        parent.children.remove(node)
        parent.children.add(index, node)
    }

    private fun remove(f: JsonArray, op: JsonElement, flush: Flush) {
        val node = child(f, op, flush, at = 1) ?: return
        node.parent?.children?.remove(node)
        node.parent = null
        forget(node, flush)
    }

    /** Ops that act on a node as a child never target the root container. */
    private fun child(f: JsonArray, op: JsonElement, flush: Flush, at: Int = 2): UINode? {
        val id = f.int(at) ?: run { flush.problem(op.toString(), "op without node id"); return null }
        val node = nodes[id] ?: run { flush.problem(op.toString(), "unknown node $id"); return null }
        if (node.id == ROOT_ID) { flush.problem(op.toString(), "the root container is not a child"); return null }
        return node
    }

    private fun isAncestor(node: UINode, of: UINode): Boolean {
        var n: UINode? = of.parent
        while (n != null) { if (n === node) return true; n = n.parent }
        return false
    }

    private fun command(f: JsonArray, op: JsonElement, flush: Flush) {
        val node = child(f, op, flush, at = 1) ?: return
        val name = f.string(2) ?: return flush.problem(op.toString(), "x without name")
        val schema = registry.schema(node.type) ?: return
        val fields = schema.commands[name] ?: return flush.problem(op.toString(), "command not in schema of ${node.type}")
        val given = f.getOrNull(3) as? JsonObject ?: JsonObject(emptyMap())
        val args = HashMap<String, Any?>()
        for ((field, spec) in fields) {
            val v = given[field] as? JsonPrimitive ?: return flush.problem(op.toString(), "command $name needs $field")
            if (!spec.accepts(v)) return flush.problem(op.toString(), "command $name: $field must be $spec")
            args[field] = v.scalar()
        }
        flush.sent += node to Command(name, args)
    }

    private fun forget(node: UINode, flush: Flush) {
        nodes.remove(node.id)
        // deleted for good (ids are never reused): its queue is cancelled once the flush is applied
        flush.removed += node
        for (child in node.children) forget(child, flush)
    }

    private fun clamp(index: Int?, size: Int, op: JsonElement, flush: Flush): Int? {
        if (index == null) { flush.problem(op.toString(), "missing index"); return null }
        if (index < 0 || index > size) {
            flush.problem(op.toString(), "index $index out of [0, $size], clamped")
            return index.coerceIn(0, size)
        }
        return index
    }

    private fun JsonArray.int(i: Int): Int? = (getOrNull(i) as? JsonPrimitive)?.intOrNull
    private fun JsonArray.string(i: Int): String? = (getOrNull(i) as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonElement.string(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonPrimitive.scalar(): Any? = when {
        this is JsonNull -> null
        isString -> content
        booleanOrNull != null -> booleanOrNull
        else -> doubleOrNull
    }
}

/** What one flush does outside the snapshot, once it is applied; the ops only run with one in hand. */
private class Flush {
    val problems = ArrayList<PatchProblem>()
    val born = ArrayList<UINode>()
    val sent = ArrayList<Pair<UINode, Command>>()
    val removed = ArrayList<UINode>()

    fun problem(op: String, reason: String) {
        problems += PatchProblem(op, reason)
    }
}

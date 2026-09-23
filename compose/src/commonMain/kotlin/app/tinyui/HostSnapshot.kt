package app.tinyui

import app.tinyui.schema.ComponentSchema
import app.tinyui.schema.FieldSpec
import app.tinyui.schema.PropSpec

/** The host snapshot `tinyui-host/<hostVersion>.txt` (docs/updates.md §4.1, format §6.4). */
object HostSnapshot {
    fun render(host: TinyUIHost, hostVersion: String): String {
        require(hostVersion.matches(Regex("[1-9][0-9]*"))) { "hostVersion must be a positive integer, got \"$hostVersion\"" }
        val components = host.components.schemas.filter { '.' in it.type }.associate { it.type to describe(it) }
        return buildString {
            append("hostVersion ").append(hostVersion).append('\n')
            append("tinyui ").append(TinyUI.version).append("\n\n")
            append("components\n")
            section(components)
            append("\ncapabilities\n")
            section(host.capabilities.names.associateWith { "" })
        }
    }

    private fun StringBuilder.section(items: Map<String, String>) {
        val width = items.keys.maxOfOrNull { it.length } ?: 0
        for ((name, rest) in items.entries.sortedBy { it.key }) {
            append("  ").append(if (rest.isEmpty()) name else name.padEnd(width + 2) + rest).append('\n')
        }
    }

    private fun describe(schema: ComponentSchema): String = listOfNotNull(
        schema.props.entries.sortedBy { it.key }.joinToString(", ") { (name, spec) -> prop(name, spec) }.ifEmpty { null },
        calls("events", schema.events),
        calls("commands", schema.commands),
        "children".takeIf { schema.children },
        "layout".takeIf { schema.layout },
    ).joinToString("; ")

    private fun prop(name: String, spec: PropSpec): String = buildString {
        append(name)
        if (!spec.required) append('?')
        append(": ").append(if (spec is PropSpec.Enum) spec.values.sorted().joinToString("|", "enum(", ")") else spec.kind)
        spec.declared?.let { append(" = ").append(literal(it)) }
        if (spec.initial) append(" (initial)")
    }

    private fun calls(label: String, calls: Map<String, Map<String, FieldSpec>>): String? {
        if (calls.isEmpty()) return null
        return calls.entries.sortedBy { it.key }.joinToString(", ", "$label ") { (name, fields) ->
            fields.entries.sortedBy { it.key }.joinToString(", ", "$name(", ")") { (field, type) -> "$field: ${type.typeName}" }
        }
    }

    private val FieldSpec.typeName: String
        get() = when (this) {
            FieldSpec.Str -> "string"
            FieldSpec.Num -> "number"
            FieldSpec.Bool -> "boolean"
        }

    private fun literal(value: Any): String = when (value) {
        is Double -> if (value % 1.0 == 0.0 && value > -1e15 && value < 1e15) value.toLong().toString() else value.toString()
        else -> value.toString()
    }
}

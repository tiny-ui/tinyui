package app.tinyui

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * What one page takes and hands back, as `tinyui routes --schema` read it off the page's types and `tinyui build`
 * put it into the manifest (docs/build-chain.md §10): the shape of its props and, when it declares one, of the
 * result it pops with.
 *
 * It is how the two sides of a page's interface are held together when they ship apart: a page mounts only with
 * props that fit ([TinyUIPage] checks, docs/native-api.md §3), and a host that opens pages from its own code can
 * check what it builds against the embedded package in a test, before a device does.
 */
class PageContract internal constructor(private val params: JsonObject, private val result: JsonObject?) {
    /** Whether the page declares what it pops with. */
    val hasResult: Boolean get() = result != null

    /**
     * Where [paramsJson] does not fit the page's props, one line per place (`$.options[0].method: expected one of …`);
     * empty when it fits. A missing required field or a value of the wrong kind is a problem; a field the page does
     * not list is not, so a newer caller may send more than an older page reads. An optional field is left out,
     * not sent as `null`, unless the page's type allows null: the page would receive a value outside its type
     * (a Kotlin caller encodes with `explicitNulls = false`). The lines name paths and kinds, never values: props
     * carry tokens.
     */
    fun problems(paramsJson: String): List<String> = check(params, paramsJson)

    /** As [problems], for what the page popped with; a page that declares no result is not checked. */
    fun resultProblems(resultJson: String): List<String> = result?.let { check(it, resultJson) } ?: emptyList()

    private fun check(schema: JsonObject, json: String): List<String> {
        val value = try { Json.parseToJsonElement(json) } catch (e: Exception) { return listOf("$: not JSON") }
        return Misfits().also { problems(schema, value, "$", it) }.lines
    }

    companion object {
        /** One entry of the manifest's `contracts`; null when it is not the shape `tinyui build` writes. */
        fun parse(entry: JsonElement): PageContract? {
            val o = entry as? JsonObject ?: return null
            return PageContract(o["params"] as? JsonObject ?: return null, o["result"] as? JsonObject)
        }

        /** What did not fit; [literals] counts the misfits against a fixed value, the ones that tell a union's shapes apart. */
        private class Misfits {
            val lines = ArrayList<String>()
            var literals = 0
            operator fun plusAssign(line: String) { lines += line }
        }

        /**
         * The checks of the schema subset the CLI writes. A keyword this library does not know (a newer CLI) passes,
         * and so does a keyword whose value is not of the kind it should be: a manifest is never a reason to throw.
         */
        private fun problems(schema: JsonObject, value: JsonElement, path: String, out: Misfits) {
            schema["const"]?.let { if (!same(it, value)) { out += "$path: expected ${it}"; out.literals++ }; return }
            (schema["enum"] as? JsonArray)?.let { options ->
                if (options.none { same(it, value) }) { out += "$path: expected one of ${options.joinToString(", ")}"; out.literals++ }
                return
            }
            (schema["anyOf"] as? JsonArray)?.let { branches ->
                // the closest shape says what is wrong. A union is told apart by a field with a fixed value (`status`),
                // so the shape whose fixed values all match is the one meant, however much else is wrong with it
                val tries = branches.mapNotNull { it as? JsonObject }.map { branch -> Misfits().also { problems(branch, value, path, it) } }
                val best = tries.minWithOrNull(compareBy({ it.literals }, { it.lines.size })) ?: return
                out.lines += best.lines
                out.literals += best.literals
                return
            }
            when ((schema["type"] as? JsonPrimitive)?.content) {
                "string" -> if (value !is JsonPrimitive || !value.isString) out += "$path: expected a string"
                "number" -> if (value !is JsonPrimitive || value.isString || value.doubleOrNull == null) out += "$path: expected a number"
                "boolean" -> if (value !is JsonPrimitive || value.isString || value.booleanOrNull == null) out += "$path: expected a boolean"
                "null" -> if (value !is JsonNull) out += "$path: expected null"
                "array" -> {
                    if (value !is JsonArray) { out += "$path: expected an array"; return }
                    val items = schema["items"] as? JsonObject ?: return
                    value.forEachIndexed { i, item -> problems(items, item, "$path[$i]", out) }
                }
                "object" -> {
                    if (value !is JsonObject) { out += "$path: expected an object"; return }
                    val properties = schema["properties"] as? JsonObject
                    val required = (schema["required"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }?.toSet() ?: emptySet()
                    properties?.forEach { (name, property) ->
                        val field = value[name]
                        if (field == null) { if (name in required) out += "$path.$name: missing" }
                        else (property as? JsonObject)?.let { problems(it, field, "$path.$name", out) }
                    }
                    // the keys the shape does not name are held to its index signature, when it has one
                    (schema["additionalProperties"] as? JsonObject)?.let { each ->
                        for ((key, field) in value) if (properties == null || key !in properties) problems(each, field, "$path.$key", out)
                    }
                }
            }
        }

        private fun same(expected: JsonElement, actual: JsonElement): Boolean {
            if (expected !is JsonPrimitive || actual !is JsonPrimitive || expected is JsonNull || actual is JsonNull) return false
            if (expected.isString != actual.isString) return false
            if (expected.isString) return expected.content == actual.content
            expected.booleanOrNull?.let { return actual.booleanOrNull == it }
            return expected.doubleOrNull != null && expected.doubleOrNull == actual.doubleOrNull
        }
    }
}

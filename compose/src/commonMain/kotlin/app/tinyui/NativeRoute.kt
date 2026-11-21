package app.tinyui

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.elementDescriptors
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * A native screen pages may navigate to by name (docs/native-api.md §3): `navigation.push("account/choose", { … })`
 * reaches the host's [Navigator] like any page name, and the host opens its own screen. Declaring it makes the name
 * and the shape of its params part of the host contract: they go into the host snapshot, page projects get their
 * types from it, and a package that navigates to a name the host does not declare is caught before it ships.
 *
 * [name] is the host's to choose; it must not start with the name of a package of the App, whose pages own those
 * names. [params] is the descriptor of the class the host decodes the params into
 * (`serializer<ChooseAccountParams>().descriptor`), so the shape is written once.
 */
class NativeRoute(val name: String, val params: SerialDescriptor) {
    init {
        require(name.matches(Regex("[A-Za-z0-9._-]+(/[A-Za-z0-9._-]+)*"))) { "native route \"$name\" must be path segments of [A-Za-z0-9._-], as page names are" }
    }

    /**
     * The params as the shape pages are checked against: the schema subset of docs/build-chain.md §10. Worked out
     * now, so params that cannot be described stop the host where it is built, not where a snapshot is rendered.
     */
    val schema: JsonObject = schemaOf(params, name)
}

/**
 * A descriptor as a shape. A field with a default is optional; a nullable one may also be null. What has no fixed
 * shape (a polymorphic or contextual type) cannot be part of a contract and is refused when the host is built.
 */
@OptIn(ExperimentalSerializationApi::class)
private fun schemaOf(descriptor: SerialDescriptor, at: String): JsonObject {
    val shape = when (val kind = descriptor.kind) {
        PrimitiveKind.STRING, PrimitiveKind.CHAR -> type("string")
        PrimitiveKind.BOOLEAN -> type("boolean")
        is PrimitiveKind -> type("number")
        SerialKind.ENUM -> buildJsonObject { putJsonArray("enum") { descriptor.elementNames.sorted().forEach { add(JsonPrimitive(it)) } } }
        StructureKind.LIST -> buildJsonObject { put("type", "array"); put("items", schemaOf(descriptor.getElementDescriptor(0), "$at[]")) }
        StructureKind.MAP -> {
            require(descriptor.getElementDescriptor(0).kind == PrimitiveKind.STRING) { "native route $at: a map's keys must be strings to be a JSON object" }
            buildJsonObject { put("type", "object"); put("additionalProperties", schemaOf(descriptor.getElementDescriptor(1), "$at[key]")) }
        }
        StructureKind.CLASS, StructureKind.OBJECT -> buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                descriptor.elementNames.forEachIndexed { i, name -> put(name, schemaOf(descriptor.getElementDescriptor(i), "$at.$name")) }
            }
            putJsonArray("required") {
                descriptor.elementNames.forEachIndexed { i, name -> if (!descriptor.isElementOptional(i)) add(JsonPrimitive(name)) }
            }
        }
        is PolymorphicKind, SerialKind.CONTEXTUAL -> throw IllegalArgumentException("native route $at: ${descriptor.serialName} has no fixed shape (${kind}); params must be plain data classes")
        else -> throw IllegalArgumentException("native route $at: ${descriptor.serialName} ($kind) cannot be described")
    }
    return if (descriptor.isNullable) JsonObject(mapOf("anyOf" to JsonArray(listOf(type("null"), shape)))) else shape
}

private fun type(name: String) = JsonObject(mapOf("type" to JsonPrimitive(name)))

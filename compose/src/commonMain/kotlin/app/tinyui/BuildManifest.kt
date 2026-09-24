package app.tinyui

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** `manifest.json` as `tinyui build` writes it (docs/updates.md §1.1): module names, output paths, package identity. */
class BuildManifest(
    val runtime: List<String>,
    val pages: List<String>,
    val files: Map<String, String>,
    val buildIds: Map<String, String>,
    val name: String,
    val publicKey: String,
    val version: String,
    val createdAt: String,
    val engine: String,
    val hashes: Map<String, String>,
    /** The tinyui-core version the runtime modules came from. */
    val tinyui: String = "",
    /** Page module name → the host things it uses (docs/updates.md §1.1). */
    val requires: Map<String, PageRequires> = emptyMap(),
    /** Present only in a manifest `tinyui bundle` wrote: the embedded package has none. */
    val hostVersion: String? = null,
) {
    fun buildId(module: String): String = buildIds[module] ?: ""

    /** Output path of [module] without extension (`runtime/core`, `pages/home`): append `.bin` or `.js.map`. */
    fun file(module: String): String = files[module] ?: module

    companion object {
        /** Throws [IllegalArgumentException] when the package identity (`name`, `publicKey`) is missing. */
        fun parse(json: String): BuildManifest {
            val root = Json.parseToJsonElement(json).jsonObject
            val names = { key: String -> root[key]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList() }
            val strings = { key: String -> root[key]?.jsonObject?.mapValues { it.value.jsonPrimitive.content } ?: emptyMap() }
            // a JSON null is an absent value, not the string "null"
            val string = { key: String -> root[key]?.jsonPrimitive?.contentOrNull ?: "" }
            val name = string("name")
            val publicKey = string("publicKey")
            require(name.isNotEmpty() && publicKey.isNotEmpty()) { "manifest.json has no package identity (name / publicKey); rebuild with a current tinyui-cli" }
            return BuildManifest(
                runtime = names("runtime"),
                pages = names("pages"),
                files = strings("files"),
                buildIds = strings("buildIds"),
                name = name,
                publicKey = publicKey,
                version = string("version"),
                createdAt = string("createdAt"),
                engine = string("engine"),
                hashes = strings("hashes"),
                tinyui = string("tinyui"),
                requires = root["requires"]?.jsonObject?.mapValues { (_, v) ->
                    val page = v.jsonObject
                    PageRequires(
                        components = page["components"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                        capabilities = page["capabilities"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                    )
                } ?: emptyMap(),
                hostVersion = root["hostVersion"]?.jsonPrimitive?.contentOrNull,
            )
        }
    }
}

/** Dotted host components and `host.call` capability names one page uses. */
class PageRequires(val components: List<String>, val capabilities: List<String>)

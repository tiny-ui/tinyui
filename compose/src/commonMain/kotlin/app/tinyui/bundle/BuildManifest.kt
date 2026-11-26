package app.tinyui.bundle

import app.tinyui.PageContract
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** `manifest.json` as `tinyui build` writes it (docs/updates.md §1.1): module names, output paths, package identity. */
class BuildManifest(
    val pages: List<String>,
    val files: Map<String, String>,
    val buildIds: Map<String, String>,
    val name: String,
    val publicKey: String,
    val version: String,
    val createdAt: String,
    val engine: String,
    val hashes: Map<String, String>,
    /** The tinyui-core version the pages were built against: the oldest runtime they run on (docs/updates.md §1.1). */
    val tinyui: String = "",
    /** Page module name → the host things it uses (docs/updates.md §1.1). */
    val requires: Map<String, PageRequires> = emptyMap(),
    /** Present only in a manifest `tinyui bundle` wrote: the embedded package has none. */
    val hostVersion: String? = null,
    /** The package's strings (docs/updates.md §1.1); null when it has none. */
    val i18n: I18nFiles? = null,
    /** Page module name → how the page asks to be shown, as written; only pages that are not a plain screen. Read it through [pageInfo]. */
    val presentation: Map<String, String> = emptyMap(),
    /** Page module name → what the page takes and hands back (docs/build-chain.md §10); empty for a package built without contracts. */
    val contracts: Map<String, PageContract> = emptyMap(),
    /** The images the pages import, as paths inside the package (`assets/<hash>.png`, docs/build-chain.md §12); each is a key of [hashes]. */
    val assets: List<String> = emptyList(),
) {
    /** What [page] declares about itself; null when the package has no such page. */
    fun pageInfo(page: String): PageInfo? = if (page in pages) PageInfo(Presentation.of(presentation[page]), contracts[page]) else null

    /** Every file of the package besides the manifest, as (path, key into [hashes]). */
    val payload: List<Pair<String, String>>
        get() = pages.map { file(it) + ".jsb" to it } + (i18n?.files?.values?.map { it to it } ?: emptyList()) + assets.map { it to it }

    fun buildId(module: String): String = buildIds[module] ?: ""

    /** Output path of [module] without extension (`pages/home`): append `.jsb` or `.js.map`. */
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
                        channels = page["channels"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                        routes = page["routes"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                    )
                } ?: emptyMap(),
                hostVersion = root["hostVersion"]?.jsonPrimitive?.contentOrNull,
                presentation = strings("presentation"),
                assets = names("assets"),
                contracts = root["contracts"]?.jsonObject?.mapNotNull { (page, entry) -> PageContract.parse(entry)?.let { page to it } }?.toMap() ?: emptyMap(),
                i18n = root["i18n"]?.jsonObject?.let { i ->
                    I18nFiles(
                        defaultLocale = i["default"]?.jsonPrimitive?.contentOrNull ?: "",
                        files = i["files"]?.jsonObject?.mapValues { it.value.jsonPrimitive.content } ?: emptyMap(),
                    )
                },
            )
        }
    }
}

/**
 * How a page asks to be shown (docs/native-api.md §3): the page declares it, `tinyui build` writes it into the
 * manifest, and the host reads it before opening the page, whoever asked for it. A value this library does not know
 * (a package built with a newer CLI) is a [Screen]: the page still works, full screen.
 */
enum class Presentation {
    /** A page of its own in the stack; what a page is unless it says otherwise. */
    Screen,
    /** A bottom sheet over what opened it, as tall as its content. */
    Sheet;

    companion object {
        fun of(declared: String?): Presentation = if (declared == "sheet") Sheet else Screen
    }
}

/**
 * What a page declares about itself in the manifest, for a host deciding whether and how to open it; [contract] is
 * null when the package was built without contracts.
 */
class PageInfo(val presentation: Presentation, val contract: PageContract? = null)

/**
 * Dotted host components, capability names (`host.call`, `session.signIn`) and http channels one page uses, and the
 * page names outside its own package it navigates to ([routes]: native routes, or pages of another package).
 */
class PageRequires(
    val components: List<String>,
    val capabilities: List<String>,
    val channels: List<String> = emptyList(),
    val routes: List<String> = emptyList(),
)

/** `manifest.i18n`: the default language and each language's file inside the package. */
class I18nFiles(val defaultLocale: String, val files: Map<String, String>)

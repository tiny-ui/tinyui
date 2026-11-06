package app.tinyui

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.update
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** A package's `i18n/<locale>.json` files (docs/native-api.md §8, build-chain.md §8). */
class PackageI18n(val defaultLocale: String, private val dictionaries: Map<String, Map<String, String>>) {
    /**
     * The string for [key] in [locale], falling back by dropping subtags (`zh-Hant-TW` → `zh-Hant` → `zh`) and then to
     * [defaultLocale]; null when no dictionary has it.
     */
    fun lookup(key: String, locale: String): String? {
        for (candidate in chain(locale)) dictionaries[candidate]?.get(key)?.let { return it }
        return null
    }

    private fun chain(locale: String): List<String> {
        val parts = locale.replace('_', '-').split('-').filter { it.isNotEmpty() }
        return (parts.size downTo 1).map { parts.take(it).joinToString("-") } + defaultLocale
    }

    companion object {
        val EMPTY = PackageI18n("", emptyMap())

        /** [files] maps each locale to its JSON text; flat string values only. */
        fun parse(defaultLocale: String, files: Map<String, String>): PackageI18n =
            PackageI18n(defaultLocale, files.mapValues { (_, json) -> Json.parseToJsonElement(json).jsonObject.mapValues { it.value.jsonPrimitive.content } })
    }
}

/** Each missing key is reported once per host. */
@OptIn(ExperimentalAtomicApi::class)
internal class MissingKeys {
    private val seen = AtomicReference<Set<String>>(emptySet())

    fun firstTime(key: String): Boolean {
        var added = false
        seen.update { if (key in it) it else { added = true; it + key } }
        return added
    }
}

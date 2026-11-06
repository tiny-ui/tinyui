package app.tinyui.components.internal

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal fun payload(key: String, value: String) = buildJsonObject { put(key, value) }.toString()

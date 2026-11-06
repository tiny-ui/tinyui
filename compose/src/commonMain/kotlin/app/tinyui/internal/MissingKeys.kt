package app.tinyui.internal

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.update

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

package app.tinyui.updates

/** Where the package a process runs came from. */
enum class Source { EMBEDDED, INSTALLED }

enum class SkipReason { INCOMPATIBLE, FAILED_BEFORE, OLDER_THAN_EMBEDDED, ROLLOUT }

/** Which signed manifest field disagreed with this host (docs/updates.md §4.3). */
enum class Mismatch { NAME, VERSION, ENGINE, TINYUI, HOST_VERSION }

enum class FailStage { POINTER, MANIFEST, SIGNATURE, DOWNLOAD, INTEGRITY, STORAGE }

/** What started a `check()` (docs/updates.md §4.6); [MANUAL] when the host calls `check()` itself. */
enum class CheckTrigger { STARTUP, RETRY, FOREGROUND, MANUAL }

/** Outcome of `check()` for one package (docs/updates.md §4.2). */
sealed class CheckResult {
    data class UpToDate(val version: String) : CheckResult()
    data class Installed(val version: String) : CheckResult()
    data class Skipped(val version: String, val reason: SkipReason, val mismatch: Set<Mismatch> = emptySet()) : CheckResult()
    data class Failed(val version: String?, val stage: FailStage, val message: String) : CheckResult()
}

/**
 * What the host logs and reports; every event names its package (docs/updates.md §4.2). The outcomes of a `check()`
 * carry its [CheckTrigger]; a [Failed] from outside one (startup integrity, a rollback's state) has none.
 */
sealed class UpdateEvent {
    abstract val pkg: String

    /**
     * At construction, the package this process runs, sent before the installed bytes are checked. A corrupted install
     * is followed by [Failed] (integrity) and a second [Running] for embedded, still at construction: the last one counts.
     */
    data class Running(override val pkg: String, val version: String, val source: Source) : UpdateEvent()
    data class UpToDate(override val pkg: String, val version: String, val trigger: CheckTrigger = CheckTrigger.MANUAL) : UpdateEvent()
    data class Skipped(override val pkg: String, val version: String, val reason: SkipReason, val mismatch: Set<Mismatch> = emptySet(), val trigger: CheckTrigger = CheckTrigger.MANUAL) : UpdateEvent()
    data class Installed(override val pkg: String, val version: String, val trigger: CheckTrigger = CheckTrigger.MANUAL) : UpdateEvent()
    /** At construction: the embedded package cannot run on this host (an engine or tinyui [mismatch]); its pages fail until an update installs. */
    data class EmbeddedIncompatible(override val pkg: String, val version: String, val mismatch: Set<Mismatch>) : UpdateEvent()
    data class Failed(override val pkg: String, val version: String?, val stage: FailStage, val message: String, val trigger: CheckTrigger? = null) : UpdateEvent()
    data class RolledBack(override val pkg: String, val version: String, val page: String, val kind: String, val buildId: String, val message: String) : UpdateEvent()
}

internal fun CheckResult.toEvent(pkg: String, trigger: CheckTrigger): UpdateEvent = when (this) {
    is CheckResult.UpToDate -> UpdateEvent.UpToDate(pkg, version, trigger)
    is CheckResult.Installed -> UpdateEvent.Installed(pkg, version, trigger)
    is CheckResult.Skipped -> UpdateEvent.Skipped(pkg, version, reason, mismatch, trigger)
    is CheckResult.Failed -> UpdateEvent.Failed(pkg, version, stage, message, trigger)
}

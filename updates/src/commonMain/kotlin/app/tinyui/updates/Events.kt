package app.tinyui.updates

/** Where the package a process runs came from. */
enum class Source { EMBEDDED, INSTALLED }

enum class SkipReason { INCOMPATIBLE, FAILED_BEFORE, OLDER_THAN_EMBEDDED, ROLLOUT }

/** Which signed manifest field disagreed with this host (docs/updates.md §4.3). */
enum class Mismatch { NAME, VERSION, ENGINE, PROTOCOL, RUNTIME_VERSION }

enum class FailStage { POINTER, MANIFEST, SIGNATURE, DOWNLOAD, INTEGRITY, STORAGE }

/** Outcome of `check()` for one package (docs/updates.md §4.2). */
sealed class CheckResult {
    data class UpToDate(val version: String) : CheckResult()
    data class Installed(val version: String) : CheckResult()
    data class Skipped(val version: String, val reason: SkipReason, val mismatch: Set<Mismatch> = emptySet()) : CheckResult()
    data class Failed(val version: String?, val stage: FailStage, val message: String) : CheckResult()
}

/** What the host logs and reports; every event names its package (docs/updates.md §4.2). */
sealed class UpdateEvent {
    abstract val pkg: String

    data class Running(override val pkg: String, val version: String, val source: Source) : UpdateEvent()
    data class UpToDate(override val pkg: String, val version: String) : UpdateEvent()
    data class Skipped(override val pkg: String, val version: String, val reason: SkipReason, val mismatch: Set<Mismatch> = emptySet()) : UpdateEvent()
    data class Installed(override val pkg: String, val version: String) : UpdateEvent()
    data class Failed(override val pkg: String, val version: String?, val stage: FailStage, val message: String) : UpdateEvent()
    data class RolledBack(override val pkg: String, val version: String, val page: String, val kind: String, val buildId: String, val message: String) : UpdateEvent()
}

internal fun CheckResult.toEvent(pkg: String): UpdateEvent = when (this) {
    is CheckResult.UpToDate -> UpdateEvent.UpToDate(pkg, version)
    is CheckResult.Installed -> UpdateEvent.Installed(pkg, version)
    is CheckResult.Skipped -> UpdateEvent.Skipped(pkg, version, reason, mismatch)
    is CheckResult.Failed -> UpdateEvent.Failed(pkg, version, stage, message)
}

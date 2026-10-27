package app.tinyui.updates

import kotlin.concurrent.Volatile
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * When to [Updates.check], the same for every host (docs/updates.md §4.6): once at [start]; once more [retryAfter]
 * later when that failed on the network and the app is in the foreground; and on coming to the foreground when the
 * last check failed on the network (at most once a minute) or the last good one is [interval] old. Installs still take
 * effect at the next start: checking in the foreground only gets the files there before it.
 *
 * Wire the foreground with [attachToAppLifecycle], or call [onForeground] / [onBackground] yourself.
 *
 * @param scope process-wide: a check is not cancelled with a screen
 */
class UpdatesScheduler(
    private val updates: Updates,
    private val scope: CoroutineScope,
    private val interval: Duration = 1.hours,
    private val retryAfter: Duration = 10.seconds,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    private val lock = Mutex()
    private var started = false
    private var lastAttempt: TimeMark? = null
    private var lastGood: TimeMark? = null
    private var failed = false

    @Volatile
    private var foreground = false

    /** Call once, right after constructing [Updates] and before any page mounts (docs/updates.md §4.1). */
    fun start() {
        scope.launch {
            if (lock.withLock { started.also { started = true } }) return@launch
            if (run(CheckTrigger.STARTUP) { true }) return@launch
            delay(retryAfter)
            if (foreground) run(CheckTrigger.RETRY) { failed }
        }
    }

    fun onForeground() {
        foreground = true
        scope.launch { run(CheckTrigger.FOREGROUND, ::due) }
    }

    fun onBackground() {
        foreground = false
    }

    /** Checks now whatever the last result, e.g. after the host switched channel or server. */
    fun checkNow() {
        scope.launch { run(CheckTrigger.MANUAL) { true } }
    }

    /** Before [start] has run nothing is due: the startup check comes first. */
    private fun due(): Boolean {
        val attempt = lastAttempt ?: return false
        return if (failed) attempt.elapsedNow() >= FAILED_GAP else lastGood?.let { it.elapsedNow() >= interval } ?: true
    }

    /** One check at a time, so a trigger arriving during one decides on its result; true when there is nothing to retry. */
    private suspend fun run(trigger: CheckTrigger, due: () -> Boolean): Boolean = lock.withLock {
        if (!due()) return@withLock !failed
        val mark = timeSource.markNow()
        lastAttempt = mark
        val ok = try {
            updates.check(trigger).values.none { it is CheckResult.Failed && it.stage in RETRYABLE }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
        failed = !ok
        if (ok) lastGood = mark
        ok
    }

    private companion object {
        /** Failures the network may get over; a bad signature or a full disk does not go away by asking again. */
        val RETRYABLE = setOf(FailStage.POINTER, FailStage.MANIFEST, FailStage.DOWNLOAD)
        val FAILED_GAP = 1.minutes
    }
}

/**
 * Calls [UpdatesScheduler.onForeground] / [UpdatesScheduler.onBackground] as the app comes and goes: Android's
 * `ProcessLifecycleOwner`, iOS's `didBecomeActive` / `didEnterBackground`. From any thread, once per process.
 */
expect fun UpdatesScheduler.attachToAppLifecycle()

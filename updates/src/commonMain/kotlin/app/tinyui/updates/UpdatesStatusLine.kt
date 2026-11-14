package app.tinyui.updates

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tinyui.bundle.BuildManifest

/** The words of [UpdatesStatusLine]: a host shows it to its own testers, in their language. */
class UpdatesStatusLineLabels(
    val embedded: String = "embedded",
    val checking: String = "checking…",
    val restart: String = "tap to restart",
    val incompatible: String = "the new version needs a new app build",
    val failedBefore: String = "the new version failed on this device",
    val failed: String = "check failed, tap to retry",
)

/**
 * One line telling a tester which version of [pkg] this process runs and whether a newer one is waiting for a
 * restart, for development builds (docs/updates.md §4.7). A tap checks, or restarts once a version is waiting: the
 * mechanism stays the one of §4.4, the line only says when a restart is worth it.
 *
 * @param onCheck check now, typically `UpdatesScheduler.checkNow()`
 * @param onRestart end the process (or restart it, where the platform lets an app do that)
 * @param note shown after the package name, e.g. the channel when it is not the build's default
 * @param versionLabel how a version reads; the default is [shortVersion]
 */
@Composable
fun UpdatesStatusLine(
    updates: Updates,
    pkg: String,
    onCheck: () -> Unit,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier,
    note: String? = null,
    labels: UpdatesStatusLineLabels = UpdatesStatusLineLabels(),
    versionLabel: (BuildManifest) -> String = ::shortVersion,
) {
    val all by updates.status.collectAsState()
    val status = all[pkg] ?: return
    val line = statusLineOf(pkg, status, note, labels, versionLabel)
    val tap = when (line.action) {
        StatusLineAction.CHECK -> onCheck
        StatusLineAction.RESTART -> onRestart
        StatusLineAction.NONE -> null
    }
    BasicText(
        text = line.text,
        style = TextStyle(color = if (line.highlighted) Highlight else Color.White, fontSize = 10.sp),
        maxLines = 2,
        modifier = modifier
            .background(Scrim, RoundedCornerShape(4.dp))
            .then(if (tap == null) Modifier else Modifier.clickable(onClick = tap))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/**
 * `MMdd-HHmmZ · <7 of the git sha>` for a version in the default form of `tinyui build` (docs/updates.md §1.1), the
 * version itself otherwise. The time is `createdAt`, in UTC.
 */
fun shortVersion(manifest: BuildManifest): String {
    val at = manifest.createdAt
    val sha = manifest.version.substringAfterLast('-', "")
    if (at.length < 16 || at[10] != 'T' || sha.length != 12 || !sha.all { it in '0'..'9' || it in 'a'..'f' }) return manifest.version
    // exactly what `tinyui build` derives: a version the host chose is not ours to shorten, whatever it ends with
    if (manifest.version != "${at.filter { it != '-' && it != ':' }}-$sha") return manifest.version
    return "${at.substring(5, 7)}${at.substring(8, 10)}-${at.substring(11, 13)}${at.substring(14, 16)}Z · ${sha.take(7)}"
}

internal enum class StatusLineAction { NONE, CHECK, RESTART }

internal class StatusLine(val text: String, val highlighted: Boolean, val action: StatusLineAction)

internal fun statusLineOf(pkg: String, status: PackageStatus, note: String?, labels: UpdatesStatusLineLabels, versionLabel: (BuildManifest) -> String): StatusLine {
    val running = listOfNotNull(pkg, note, versionLabel(status.running), labels.embedded.takeIf { status.source == Source.EMBEDDED }).joinToString(" · ")
    val pending = status.pending
    val last = status.lastCheck
    return when {
        // a version waiting for the restart is what the tester came for, whatever a later check said
        pending != null -> StatusLine("$running → ${versionLabel(pending)} · ${labels.restart}", true, StatusLineAction.RESTART)
        status.checking -> StatusLine("$running · ${labels.checking}", false, StatusLineAction.NONE)
        last is CheckResult.Failed -> StatusLine("$running · ${labels.failed}", false, StatusLineAction.CHECK)
        last is CheckResult.Skipped && last.reason == SkipReason.INCOMPATIBLE && last.mismatch.all { it in OTHER_HOST } ->
            StatusLine("$running · ${labels.incompatible}", true, StatusLineAction.CHECK)
        last is CheckResult.Skipped && last.reason == SkipReason.FAILED_BEFORE -> StatusLine("$running · ${labels.failedBefore}", true, StatusLineAction.CHECK)
        else -> StatusLine(running, false, StatusLineAction.CHECK)
    }
}

/** The package is fine, for another host. One that also names the wrong package or version was published to the wrong place: no app build fixes that. */
private val OTHER_HOST = setOf(Mismatch.HOST_VERSION, Mismatch.ENGINE, Mismatch.TINYUI)
private val Scrim = Color(0x99000000)
private val Highlight = Color(0xFFFFC107)

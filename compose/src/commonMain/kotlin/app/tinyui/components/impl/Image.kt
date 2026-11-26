package app.tinyui.components.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.ContentScale
import app.tinyui.components.AssetImage
import app.tinyui.components.ImageSrc
import app.tinyui.components.LocalPageAssetSource
import app.tinyui.components.NodeScope
import app.tinyui.components.SharedImageLoader
import app.tinyui.components.srcForReport
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext

/**
 * A picture from the page's package or an https URL (docs/components.md §3). The box is the layout props' (`width` /
 * `height`, `cornerRadius` clips it, `background` shows until the picture arrives); a src that is neither, or one that
 * fails to load, leaves the box empty and is reported (E5).
 */
@Composable
internal fun ImageComponent(scope: NodeScope) {
    val src = scope.get<String>("src") ?: ""
    val parsed = remember(src) { ImageSrc.parse(src) }
    val source = LocalPageAssetSource.current
    val model: Any? = when (parsed) {
        is ImageSrc.Asset -> source?.let { remember(it, parsed) { AssetImage(it.pkg, parsed.path, it.assets) } }
        is ImageSrc.Remote -> parsed.url
        is ImageSrc.Invalid -> null
    }
    if (model == null) {
        val reason = (parsed as? ImageSrc.Invalid)?.reason ?: "src \"${srcForReport(src)}\" names a packaged image, but the page was not loaded from a package"
        LaunchedEffect(src) { scope.reportProblem(reason) }
        Box(scope.modifier())
        return
    }
    AsyncImage(
        model = model,
        contentDescription = scope.get<String>("label"),
        imageLoader = SharedImageLoader.get(LocalPlatformContext.current),
        modifier = scope.modifier(),
        contentScale = when (scope.get<String>("contentScale")) {
            "crop" -> ContentScale.Crop
            "fill" -> ContentScale.FillBounds
            else -> ContentScale.Fit
        },
        onError = { state -> scope.reportProblem("image ${srcForReport(src)} failed to load: ${state.result.throwable.message}") },
    )
}

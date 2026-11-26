package app.tinyui.components

import androidx.compose.runtime.compositionLocalOf
import app.tinyui.PageAssets
import app.tinyui.defaultHttpClient
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.disk.DiskCache
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.memory.MemoryCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.Options
import okio.Buffer
import okio.FileSystem

// How `Image` gets its picture (docs/components.md §3): what a `src` names, where an asset is read from, and the one
// coil loader every page shares.

/** Where the page being rendered reads its assets: its package's name (the cache key) and reader. Remembered per page. */
internal class PageAssetSource(val pkg: String, val assets: PageAssets)

internal val LocalPageAssetSource = compositionLocalOf<PageAssetSource?> { null }

/** Longest part of a `src` that goes into a reported problem: a URL can be long and carry query parameters. */
private const val SRC_IN_REPORT = 80

/** A `src` as it appears in a reported problem. */
internal fun srcForReport(src: String): String = if (src.length <= SRC_IN_REPORT) src else src.take(SRC_IN_REPORT) + "…"

/** What an `Image`'s `src` names (docs/components.md §3). */
internal sealed interface ImageSrc {
    /** An image the page imported: `asset:assets/<hash>.png`, read from the page's own package. */
    data class Asset(val path: String) : ImageSrc

    data class Remote(val url: String) : ImageSrc

    /** Nothing is drawn and [reason] is reported. */
    data class Invalid(val reason: String) : ImageSrc

    companion object {
        private const val ASSET = "asset:"

        fun parse(src: String): ImageSrc = when {
            src.startsWith(ASSET) -> {
                val path = src.removePrefix(ASSET)
                if (path.startsWith("assets/") && path.split('/').all { it.isNotEmpty() && it != "." && it != ".." }) Asset(path)
                else Invalid("src \"${srcForReport(src)}\" is not an image imported from the package")
            }
            src.startsWith("https://") -> Remote(src)
            src.startsWith("http://") -> Invalid("src \"${srcForReport(src)}\" is plain http; only https is loaded")
            else -> Invalid("src \"${srcForReport(src)}\" is neither an imported image nor an https URL")
        }
    }
}

/**
 * The data coil loads for an [ImageSrc.Asset]: the bytes come from the package, never from the network. Equal by
 * package and path (the reader is the package's), so a recomposition does not start the same request again.
 */
internal class AssetImage(val pkg: String, val path: String, val assets: PageAssets) {
    override fun equals(other: Any?): Boolean = other is AssetImage && other.pkg == pkg && other.path == path
    override fun hashCode(): Int = pkg.hashCode() * 31 + path.hashCode()
}

private class AssetImageFetcher(private val image: AssetImage, private val options: Options) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val bytes = image.assets.read(image.path) ?: throw IllegalStateException("package ${image.pkg} has no ${image.path}")
        return SourceFetchResult(
            source = ImageSource(Buffer().write(bytes), options.fileSystem),
            mimeType = null,
            dataSource = DataSource.DISK,
        )
    }

    class Factory : Fetcher.Factory<AssetImage> {
        override fun create(data: AssetImage, options: Options, imageLoader: ImageLoader): Fetcher = AssetImageFetcher(data, options)
    }
}

/** The file name is the content hash, so package + path is a stable memory-cache key across package versions. */
private class AssetImageKeyer : Keyer<AssetImage> {
    override fun key(data: AssetImage, options: Options): String = "tinyui:${data.pkg}/${data.path}"
}

/**
 * The one loader every page shares: assets from packages, and https images through the client the `default` channel
 * uses (one connection pool; no host headers or session on the request), with a memory cache and a disk cache for the
 * network ones. Built on first use; only composition calls [get], so on the main thread, and it is built once.
 */
internal object SharedImageLoader {
    private var loader: ImageLoader? = null

    fun get(context: PlatformContext): ImageLoader = loader ?: ImageLoader.Builder(context)
        .components {
            // Ktor follows redirects itself (both engines leave them to it) and never from https down to http
            add(KtorNetworkFetcherFactory(httpClient = { defaultHttpClient }))
            add(AssetImageFetcher.Factory())
            add(AssetImageKeyer())
        }
        .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.15).build() }
        .diskCache {
            DiskCache.Builder()
                .directory(FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "tinyui-images")
                .maxSizeBytes(64L * 1024 * 1024)
                .build()
        }
        .build()
        .also { loader = it }
}

package app.tinyui.components

import androidx.compose.runtime.compositionLocalOf
import app.tinyui.PageAssets
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

/** Where an `Image` of the page being rendered reads the images its package carries (docs/build-chain.md §12). */
class PageImages(val pkg: String, val assets: PageAssets)

internal val LocalPageImages = compositionLocalOf<PageImages?> { null }

/** What an `Image`'s `src` names (docs/components.md §3). */
internal sealed interface ImageSrc {
    /** An image the page imported: `asset:assets/<hash>.png`, read from the page's own package. */
    data class Packaged(val path: String) : ImageSrc

    data class Remote(val url: String) : ImageSrc

    /** Nothing is drawn and [reason] is reported. */
    data class Invalid(val reason: String) : ImageSrc

    companion object {
        private const val PACKAGED = "asset:"

        fun parse(src: String): ImageSrc = when {
            src.startsWith(PACKAGED) -> {
                val path = src.removePrefix(PACKAGED)
                if (path.startsWith("assets/") && path.split('/').all { it.isNotEmpty() && it != "." && it != ".." }) Packaged(path)
                else Invalid("src \"$src\" is not an image imported from the package")
            }
            src.startsWith("https://") -> Remote(src)
            src.startsWith("http://") -> Invalid("src \"$src\" is plain http; only https is loaded")
            else -> Invalid("src \"${src.take(80)}\" is neither an imported image nor an https URL")
        }
    }
}

/** The data coil loads for an [ImageSrc.Packaged]: the bytes come from the package, never from the network. */
internal class PackagedImage(val pkg: String, val path: String, val assets: PageAssets)

private class PackagedImageFetcher(private val image: PackagedImage, private val options: Options) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val bytes = image.assets.read(image.path) ?: throw IllegalStateException("package ${image.pkg} has no ${image.path}")
        return SourceFetchResult(
            source = ImageSource(Buffer().write(bytes), options.fileSystem),
            mimeType = null,
            dataSource = DataSource.DISK,
        )
    }

    class Factory : Fetcher.Factory<PackagedImage> {
        override fun create(data: PackagedImage, options: Options, imageLoader: ImageLoader): Fetcher = PackagedImageFetcher(data, options)
    }
}

/** The file name is the content hash, so package + path is a stable memory-cache key across package versions. */
private class PackagedImageKeyer : Keyer<PackagedImage> {
    override fun key(data: PackagedImage, options: Options): String = "tinyui:${data.pkg}/${data.path}"
}

/**
 * One loader for every page: images from packages, and https images through the core's ktor client, with a memory
 * cache and a disk cache for the network ones. Requests carry no host headers or session (docs/components.md §3).
 */
internal object TinyUIImages {
    private var loader: ImageLoader? = null

    fun loader(context: PlatformContext): ImageLoader = loader ?: ImageLoader.Builder(context)
        .components {
            add(KtorNetworkFetcherFactory())
            add(PackagedImageFetcher.Factory())
            add(PackagedImageKeyer())
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

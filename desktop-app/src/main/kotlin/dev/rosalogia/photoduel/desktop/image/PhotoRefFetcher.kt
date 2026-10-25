package dev.rosalogia.photoduel.desktop.image

import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import coil3.size.Dimension
import dev.rosalogia.photoduel.core.ui.PhotoRef

/**
 * Loads a [PhotoRef] for Coil: from the thumbnail cache when the requested size fits a
 * thumbnail, otherwise decoded from the file at the size asked for.
 */
class PhotoRefFetcher(
    private val ref: PhotoRef,
    private val options: Options,
    private val decoder: DesktopPhotoDecoder,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val want = maxOf(options.size.width.px(), options.size.height.px())
        val bitmap = if (want <= DesktopPhotoDecoder.THUMB_EDGE) {
            decoder.thumbnail(ref.mediaId)
        } else {
            decoder.decodeBitmap(ref.mediaId, minOf(want, MAX_EDGE))
        }
        return ImageFetchResult(bitmap.asImage(), isSampled = true, dataSource = DataSource.DISK)
    }

    private fun Dimension.px() = (this as? Dimension.Pixels)?.px ?: MAX_EDGE

    class Factory(private val decoder: DesktopPhotoDecoder) : Fetcher.Factory<PhotoRef> {
        override fun create(data: PhotoRef, options: Options, imageLoader: ImageLoader): Fetcher = PhotoRefFetcher(data, options, decoder)
    }

    private companion object {
        const val MAX_EDGE = 2560
    }
}

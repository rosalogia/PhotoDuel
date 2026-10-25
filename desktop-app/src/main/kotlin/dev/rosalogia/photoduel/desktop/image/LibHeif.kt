package dev.rosalogia.photoduel.desktop.image

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import dev.rosalogia.photoduel.core.common.AppLog

/** The few libheif C functions needed to decode a HEIC photo's primary image, via JNA. */
@Suppress("FunctionName")
internal interface LibHeifApi : Library {
    fun heif_init(params: Pointer?): HeifError.ByValue
    fun heif_context_alloc(): Pointer
    fun heif_context_free(ctx: Pointer)
    fun heif_context_read_from_file(ctx: Pointer, filename: String, options: Pointer?): HeifError.ByValue
    fun heif_context_get_primary_image_handle(ctx: Pointer, out: PointerByReference): HeifError.ByValue
    fun heif_image_handle_release(handle: Pointer)
    fun heif_decode_image(handle: Pointer, out: PointerByReference, colorspace: Int, chroma: Int, options: Pointer?): HeifError.ByValue
    fun heif_image_get_width(image: Pointer, channel: Int): Int
    fun heif_image_get_height(image: Pointer, channel: Int): Int
    fun heif_image_get_plane_readonly(image: Pointer, channel: Int, stride: IntByReference): Pointer?
    fun heif_image_release(image: Pointer)
}

@Structure.FieldOrder("code", "subcode", "message")
internal open class HeifError : Structure() {
    @JvmField var code = 0
    @JvmField var subcode = 0
    @JvmField var message: String? = null

    class ByValue : HeifError(), Structure.ByValue
}

/** An RGBA image, row-major and tightly packed. */
class RgbaPixels(val width: Int, val height: Int, val bytes: ByteArray)

/**
 * HEIC/HEIF decoding through libheif (with libde265 for HEVC). The library is bundled with the
 * app's installers; during development the system's copy is used.
 */
object LibHeif {
    private const val COLORSPACE_RGB = 1
    private const val CHROMA_INTERLEAVED_RGBA = 11
    private const val CHANNEL_INTERLEAVED = 10

    private val api: LibHeifApi? by lazy {
        runCatching {
            Native.load(if (System.getProperty("os.name").startsWith("Windows")) "libheif" else "heif", LibHeifApi::class.java)
                .also { it.heif_init(null) }
        }.onFailure { AppLog.w("LibHeif", "libheif not available; HEIC photos can't be shown", it) }.getOrNull()
    }

    val available: Boolean get() = api != null

    /** Decodes the primary image with its rotation and mirroring applied, as libheif does by default. */
    fun decode(path: String): RgbaPixels {
        val lib = api ?: error("libheif isn't available")
        val ctx = lib.heif_context_alloc()
        try {
            lib.heif_context_read_from_file(ctx, path, null).check()
            val handleRef = PointerByReference()
            lib.heif_context_get_primary_image_handle(ctx, handleRef).check()
            val handle = handleRef.value
            try {
                val imageRef = PointerByReference()
                lib.heif_decode_image(handle, imageRef, COLORSPACE_RGB, CHROMA_INTERLEAVED_RGBA, null).check()
                val image = imageRef.value
                try {
                    val w = lib.heif_image_get_width(image, CHANNEL_INTERLEAVED)
                    val h = lib.heif_image_get_height(image, CHANNEL_INTERLEAVED)
                    val stride = IntByReference()
                    val plane = lib.heif_image_get_plane_readonly(image, CHANNEL_INTERLEAVED, stride) ?: error("No pixels in $path")
                    val out = ByteArray(w * h * 4)
                    for (y in 0 until h) plane.read(y.toLong() * stride.value, out, y * w * 4, w * 4)
                    return RgbaPixels(w, h, out)
                } finally {
                    lib.heif_image_release(image)
                }
            } finally {
                lib.heif_image_handle_release(handle)
            }
        } finally {
            lib.heif_context_free(ctx)
        }
    }

    private fun HeifError.check() {
        if (code != 0) error("libheif error $code/$subcode: $message")
    }
}

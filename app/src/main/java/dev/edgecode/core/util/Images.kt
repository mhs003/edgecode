package dev.edgecode.core.util

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Decodes a downscaled bitmap from a content URI.
 *
 * Deliberately hand-rolled rather than pulling in an image loading library:
 * attachments are a small, bounded set and are never loaded from the network,
 * so a full async image pipeline would be weight without benefit.
 */
suspend fun loadThumbnail(
    context: Context,
    uriString: String,
    maxEdge: Int = 512,
): ImageBitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val uri = Uri.parse(uriString)
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
            android.graphics.ImageDecoder.decodeBitmap(
                source,
                android.graphics.ImageDecoder.OnHeaderDecodedListener { decoder, info, _ ->
                    val longest = maxOf(info.size.width, info.size.height)
                    if (longest > maxEdge) {
                        val scale = maxEdge.toFloat() / longest
                        decoder.setTargetSize(
                            (info.size.width * scale).toInt().coerceAtLeast(1),
                            (info.size.height * scale).toInt().coerceAtLeast(1),
                        )
                    }
                },
            )
        } else {
            decodeWithBounds(context, uri, maxEdge)
        }
        bitmap?.asImageBitmap()
    }.getOrNull()
}

private fun decodeWithBounds(context: Context, uri: Uri, maxEdge: Int): android.graphics.Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, bounds)
    }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sample = 1
    val longest = maxOf(bounds.outWidth, bounds.outHeight)
    while (longest / sample > maxEdge * 2) sample *= 2

    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, options)
    }
}

/** Reads display metadata for a picked file without loading its bytes. */
fun queryFileMetadata(context: Context, uriString: String): Triple<String, String, Long>? =
    runCatching {
        val uri = Uri.parse(uriString)
        var name = uri.lastPathSegment ?: "attachment"
        var size = 0L
        var mime = context.contentResolver.getType(uri) ?: "application/octet-stream"

        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) cursor.getString(nameIndex)?.let { name = it }
                val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }
        Triple(name, mime, size)
    }.getOrNull()

/** Unused placeholder retained for API symmetry with future thumbnail sizes. */
internal val DEFAULT_THUMBNAIL_SIZE = Size(512, 512)

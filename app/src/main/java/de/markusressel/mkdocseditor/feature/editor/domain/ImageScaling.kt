package de.markusressel.mkdocseditor.feature.editor.domain

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream

/** photos from phones are huge; larger JPEGs are scaled down to this width or height */
const val MAX_UPLOAD_IMAGE_SIZE = 2560

/**
 * Scales a large JPEG down (like the web client does before an upload); everything else, and
 * a JPEG that would not get smaller, stays as it is
 */
fun shrinkImage(name: String, bytes: ByteArray, maxSize: Int = MAX_UPLOAD_IMAGE_SIZE): ByteArray {
    val ext = name.substringAfterLast('.', "").lowercase()
    if (ext != "jpg" && ext != "jpeg") return bytes
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val largest = maxOf(bounds.outWidth, bounds.outHeight)
        if (largest <= maxSize) return bytes
        var sample = 1
        while (largest / (sample * 2) >= maxSize) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return bytes
        val scale = maxSize.toFloat() / maxOf(decoded.width, decoded.height)
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
        } else {
            decoded
        }
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
        out.toByteArray().takeIf { it.size < bytes.size } ?: bytes
    } catch (e: Exception) {
        bytes
    }
}

/**
 * Decodes an image to a small bitmap of at least [size] pixels on its shorter side, or null
 * (SVG and other formats Android can't decode)
 */
fun decodeThumbnail(bytes: ByteArray, size: Int): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= size) sample *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        ?.asImageBitmap()
}

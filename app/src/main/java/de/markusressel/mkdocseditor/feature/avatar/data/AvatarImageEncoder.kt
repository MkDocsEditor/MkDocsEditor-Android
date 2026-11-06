package de.markusressel.mkdocseditor.feature.avatar.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Color
import androidx.core.net.toUri
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Turns a picked picture into what the profile picture upload takes
 */
internal fun interface AvatarImageEncoder {

    /**
     * @param uri the content URI of the picture
     * @return a square JPEG, small enough for the upload limit
     */
    suspend fun encode(uri: String): ByteArray

    companion object {
        const val CONTENT_TYPE = "image/jpeg"
    }
}

/**
 * The server crops and scales on its own, this only keeps the upload small: a photo of the
 * camera has several megabytes, the stored picture is 256 pixels wide.
 */
internal class AndroidAvatarImageEncoder @Inject constructor(
    @ApplicationContext private val context: Context,
) : AvatarImageEncoder {

    override suspend fun encode(uri: String): ByteArray = withContext(Dispatchers.IO) {
        val source = ImageDecoder.createSource(context.contentResolver, uri.toUri())
        // ImageDecoder applies the EXIF orientation
        val decoded = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val shortSide = min(info.size.width, info.size.height)
            val scale = min(1f, SIZE.toFloat() / shortSide)
            decoder.setTargetSize(
                (info.size.width * scale).roundToInt().coerceAtLeast(1),
                (info.size.height * scale).roundToInt().coerceAtLeast(1),
            )
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val side = min(decoded.width, decoded.height)
        val square = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        Canvas(square).apply {
            // JPEG has no transparency
            drawColor(Color.WHITE)
            drawBitmap(decoded, -(decoded.width - side) / 2f, -(decoded.height - side) / 2f, null)
        }
        decoded.recycle()
        ByteArrayOutputStream().use { out ->
            square.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
            square.recycle()
            out.toByteArray()
        }
    }

    private companion object {
        const val SIZE = 512
        const val QUALITY = 90
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class AvatarImageEncoderModule {
    @Binds
    abstract fun bindAvatarImageEncoder(encoder: AndroidAvatarImageEncoder): AvatarImageEncoder
}

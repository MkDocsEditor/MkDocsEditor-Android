package de.markusressel.mkdocseditor.feature.auth.common.ui.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Encodes [text] as a QR code matrix with a 4 module quiet zone
 */
internal fun encodeQrCode(text: String): BitMatrix = QRCodeWriter().encode(
    text,
    BarcodeFormat.QR_CODE,
    0,
    0,
    mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to 4,
    ),
)

/**
 * A QR code, always black on white so every scanner reads it, also in dark mode
 */
@Composable
internal fun QrCode(
    text: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 220.dp,
) {
    val matrix = remember(text) { encodeQrCode(text) }
    Canvas(
        modifier = modifier
            .size(size)
            .background(Color.White)
            .semantics { this.contentDescription = contentDescription },
    ) {
        val module = this.size.minDimension / matrix.width
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix[x, y]) {
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(x * module, y * module),
                        // a hair larger, so neighbouring modules leave no gaps
                        size = Size(module + 0.5f, module + 0.5f),
                    )
                }
            }
        }
    }
}

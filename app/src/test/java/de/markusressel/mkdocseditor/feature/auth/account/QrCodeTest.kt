package de.markusressel.mkdocseditor.feature.auth.account

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import de.markusressel.mkdocseditor.feature.auth.common.ui.compose.encodeQrCode
import kotlin.test.Test
import kotlin.test.assertEquals

class QrCodeTest {

    @Test
    fun `the otpauth URI survives a QR round trip`() {
        val uri = "otpauth://totp/MkDocsEditor:markus?secret=JBSWY3DPEHPK3PXP&issuer=MkDocsEditor&algorithm=SHA1&digits=6&period=30"
        val matrix = encodeQrCode(uri)

        // scale up, scanners need more than one pixel per module
        val scale = 4
        val width = matrix.width * scale
        val height = matrix.height * scale
        val pixels = IntArray(width * height) { i ->
            if (matrix[(i % width) / scale, (i / width) / scale]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(width, height, pixels)))

        assertEquals(uri, QRCodeReader().decode(bitmap).text)
    }
}

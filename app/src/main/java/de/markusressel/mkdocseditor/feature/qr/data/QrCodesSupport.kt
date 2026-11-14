package de.markusressel.mkdocseditor.feature.qr.data

import de.markusressel.mkdocseditor.auth.api.ServerFeature
import de.markusressel.mkdocseditor.feature.common.data.ServerFeatures
import javax.inject.Inject

/** Whether the selected server has QR codes (`qr-codes` in its features), see [ServerFeatures] */
internal class QrCodesSupport @Inject constructor(
    private val features: ServerFeatures,
) {
    /** false while the server can't be asked and has not been before */
    suspend fun isSupported(): Boolean = features.has(ServerFeature.QR_CODES)
}

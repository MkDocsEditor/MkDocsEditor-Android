package de.markusressel.mkdocseditor.feature.qr.data

import de.markusressel.mkdocseditor.auth.api.ServerFeature
import de.markusressel.mkdocseditor.auth.domain.AuthRepository
import de.markusressel.mkdocseditor.feature.auth.common.data.AuthBackends
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.toBackendRef
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the selected server has QR codes (`qr-codes` in its features). The answer is kept for the app's lifetime,
 * so a page opened offline still shows the menu entry once the server was reached.
 */
@Singleton
internal class QrCodesSupport @Inject constructor(
    private val backends: AuthBackends,
    private val authRepository: AuthRepository,
) {
    private val known = ConcurrentHashMap<String, Boolean>()

    /** false while the server can't be asked and has not been before */
    suspend fun isSupported(): Boolean {
        val backend = backends.selected()?.toBackendRef() ?: return false
        known[backend.baseUrl]?.let { return it }
        return try {
            (ServerFeature.QR_CODES in authRepository.serverInfo(backend).features).also { known[backend.baseUrl] = it }
        } catch (e: Exception) {
            Timber.w(e, "Could not read the server features")
            false
        }
    }
}

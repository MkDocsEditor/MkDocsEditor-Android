package de.markusressel.mkdocseditor.feature.common.ui

import androidx.annotation.StringRes
import com.github.kittinunf.fuel.core.FuelError
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocsrestclient.BackendNotConfiguredException
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * What to tell the user about a failure. Exception texts are written for developers, and aren't translated, so they
 * only go to the log.
 */
@StringRes
internal fun Throwable.userMessage(): Int {
    val chain = generateSequence(this) { it.cause }.take(MAX_CAUSES).toList()
    val status = chain.firstNotNullOfOrNull { (it as? FuelError)?.response?.statusCode }
    return when {
        chain.any { it is BackendNotConfiguredException } -> R.string.error_no_backend
        status == 401 || status == 403 -> R.string.error_not_allowed
        status == 404 -> R.string.error_not_found
        status != null && status >= 500 -> R.string.error_server
        chain.any { it.isConnectionProblem() } -> R.string.error_unreachable
        else -> R.string.unknown_error
    }
}

private fun Throwable.isConnectionProblem() = this is UnknownHostException || this is ConnectException ||
    this is NoRouteToHostException || this is SocketTimeoutException || this is SSLException

/** guards against an exception that causes itself in a loop */
private const val MAX_CAUSES = 10

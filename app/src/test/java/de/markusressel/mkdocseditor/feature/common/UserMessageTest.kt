package de.markusressel.mkdocseditor.feature.common

import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.HttpException
import com.github.kittinunf.fuel.core.Response
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.common.ui.userMessage
import de.markusressel.mkdocsrestclient.BackendNotConfiguredException
import java.net.URL
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals

class UserMessageTest {

    private fun httpError(status: Int) = FuelError.wrap(
        HttpException(status, "raw text"),
        Response(url = URL("http://localhost/"), statusCode = status),
    )

    @Test
    fun `http failures are told apart by their status`() {
        assertEquals(R.string.error_not_allowed, httpError(401).userMessage())
        assertEquals(R.string.error_not_allowed, httpError(403).userMessage())
        assertEquals(R.string.error_not_found, httpError(404).userMessage())
        assertEquals(R.string.error_server, httpError(503).userMessage())
    }

    @Test
    fun `a connection problem is found behind the wrapper of fuel`() {
        assertEquals(R.string.error_unreachable, FuelError.wrap(UnknownHostException("host")).userMessage())
    }

    @Test
    fun `a missing backend has its own message`() {
        assertEquals(R.string.error_no_backend, FuelError.wrap(BackendNotConfiguredException()).userMessage())
    }

    @Test
    fun `anything else does not show the text of the exception`() {
        assertEquals(R.string.unknown_error, IllegalStateException("Expected BEGIN_OBJECT").userMessage())
    }
}

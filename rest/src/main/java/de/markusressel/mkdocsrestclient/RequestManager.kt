/*
 * Copyright (c) 2018 Markus Ressel
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons
 * to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the
 * Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package de.markusressel.mkdocsrestclient

import com.github.kittinunf.fuel.core.BlobDataPart
import com.github.kittinunf.fuel.core.Deserializable
import com.github.kittinunf.fuel.core.FoldableRequestInterceptor
import com.github.kittinunf.fuel.core.FoldableResponseInterceptor
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.FuelManager
import com.github.kittinunf.fuel.core.Method
import com.github.kittinunf.fuel.core.Parameters
import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.core.Response
import com.github.kittinunf.fuel.core.awaitResponseResult
import com.github.kittinunf.fuel.core.requests.UploadRequest
import com.github.kittinunf.fuel.core.requests.upload
import com.github.kittinunf.fuel.coroutines.awaitByteArrayResult
import com.github.kittinunf.fuel.coroutines.awaitStringResult
import com.github.kittinunf.result.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException

/**
 * A request was made while no backend is configured
 */
class BackendNotConfiguredException : IOException("No backend is configured")

/**
 * Created by Markus on 08.02.2018.
 *
 * @param baseUrl the base URL of the backend (with a trailing slash), or null when none is
 * configured. It is asked for every request, because Android can restore any screen after
 * process death: nothing has to run before a request can go to the right host, and there is
 * no default host a request could go to instead.
 * @param authorizationProvider supplies the Authorization header of every request
 * @param unauthorizedListener called (on a network thread) whenever the server answers 401
 */
class RequestManager(
    private val baseUrl: () -> String? = { null },
    private val authorizationProvider: AuthorizationProvider = AuthorizationProvider.NONE,
    private val unauthorizedListener: () -> Unit = {},
) {

    private val fuelManager = FuelManager()

    private val loggingInterceptor: FoldableResponseInterceptor =
        { next: (Request, Response) -> Response ->
            { req: Request, res: Response ->
                Timber.v(req.toString())
                Timber.v(res.toString())
                next(req, res)
            }
        }


    private val unauthorizedInterceptor: FoldableResponseInterceptor =
        { next: (Request, Response) -> Response ->
            { req: Request, res: Response ->
                if (res.statusCode == 401) {
                    unauthorizedListener()
                }
                next(req, res)
            }
        }

    private val notConfiguredInterceptor: FoldableRequestInterceptor =
        { next: (Request) -> Request ->
            { req: Request ->
                if (req.url.host == NOT_CONFIGURED_HOST) throw FuelError.wrap(BackendNotConfiguredException())
                next(req)
            }
        }

    init {
        fuelManager.addRequestInterceptor(notConfiguredInterceptor)
        fuelManager.addResponseInterceptor(unauthorizedInterceptor)
    }

    /**
     * Adds loggers to Fuel requests
     */
    fun addLogger() {
        fuelManager.addResponseInterceptor(loggingInterceptor)
    }

    /**
     * Removes loggers from Fuel requests
     */
    fun removeLogger() {
        fuelManager.removeResponseInterceptor(loggingInterceptor)
    }

    /**
     * @return the absolute URL of an API path. Fuel cannot build a request without a host, so
     * without a backend it is a reserved name (RFC 6761), and [notConfiguredInterceptor] fails
     * the request before it is sent.
     */
    private fun absoluteUrl(path: String): String {
        val base = baseUrl() ?: "https://$NOT_CONFIGURED_HOST/"
        return base + path.removePrefix("/")
    }

    /**
     * Creates an (authenticated) request
     *
     * @param url the url
     * @param urlParameters query parameters
     * @param method the request type (f.ex. GET)
     */
    fun createRequest(
        url: String,
        urlParameters: Parameters = emptyList(),
        method: Method,
        timeout: Int = DEFAULT_TIMEOUT
    ): Request {
        return getAuthenticatedRequest(fuelManager.request(method, absoluteUrl(url), urlParameters))
            .timeout(timeout = timeout)
    }

    /**
     * Applies the Authorization header to a request
     */
    private fun getAuthenticatedRequest(request: Request): Request {
        // API client: no WWW-Authenticate challenges, passes the CSRF check of the backend
        request.header(HEADER_REQUESTED_WITH)
        authorizationProvider.authorization()?.let {
            return request.header("Authorization" to it)
        }

        return request
    }

    /**
     * Do a simple status code request
     *
     * @param url the URL
     * @param method the request type (f.ex. GET)
     */
    suspend fun doStatusRequest(url: String, method: Method): Result<String, FuelError> {
        return withContext(Dispatchers.IO) {
            createRequest(url = url, method = method).awaitStringResult()
        }
    }

    /**
     * Do a simple request that expects a json response body
     *
     * @param url the URL
     * @param method the request type (f.ex. GET)
     */
    suspend inline fun <reified T : Any> doRequest(
        url: String,
        method: Method,
    ): Result<T, FuelError> {
        return withContext(Dispatchers.IO) {
            val deserializer: Deserializable<T> = deserializer()
            val request = createRequest(url = url, method = method)
            @Suppress("UNCHECKED_CAST")
            when (T::class) {
                String::class -> request.awaitStringResult() as Result<T, FuelError>
                else -> request.awaitResponseResult(deserializer).third
            }
        }
    }

    /**
     * Do a request with query parameters that expects a json response body
     *
     * @param url the URL
     * @param urlParameters url query parameters
     * @param method the request type (f.ex. GET)
     */
    suspend inline fun <reified T : Any> doRequest(
        url: String,
        urlParameters: Parameters,
        method: Method
    ): Result<T, FuelError> {
        return withContext(Dispatchers.IO) {
            val deserializer: Deserializable<T> = deserializer()
            val request = createRequest(url = url, urlParameters = urlParameters, method = method)
            @Suppress("UNCHECKED_CAST")
            when (T::class) {
                String::class -> request.awaitStringResult() as Result<T, FuelError>
                else -> request.awaitResponseResult(deserializer).third
            }
        }
    }

    /**
     * Do a request with a json body that expects a json response body
     *
     * @param url the URL
     * @param method the request type (f.ex. GET)
     * @param jsonData an Object that will be serialized to json
     */
    suspend inline fun <reified K : Any, reified T : Any> doJsonRequest(
        url: String,
        method: Method,
        jsonData: K,
        timeout: Int = DEFAULT_TIMEOUT,
    ): Result<T, FuelError> {
        return withContext(Dispatchers.IO) {
            val json = jsonData.toJson()
            val deserializer: Deserializable<T> = deserializer()
            val request =
                createRequest(url = url, method = method, timeout = timeout)
                    .body(json)
                    .header(HEADER_CONTENT_TYPE_JSON)
            @Suppress("UNCHECKED_CAST")
            when (T::class) {
                String::class -> request.awaitStringResult() as Result<T, FuelError>
                else -> request.awaitResponseResult(deserializer).third
            }
        }
    }

    /**
     * Creates an (authenticated) request
     *
     * @param url the url
     * @param urlParameters query parameters
     * @param timeout the request timeout
     */
    fun createUploadRequest(
        url: String,
        urlParameters: Parameters = emptyList(),
        timeout: Int = DEFAULT_TIMEOUT
    ): UploadRequest {
        return getAuthenticatedRequest(
            fuelManager.upload(
                absoluteUrl(url),
                method = Method.POST,
                urlParameters
            )
        ).timeout(timeout = timeout).upload()
    }

    suspend inline fun <reified T : Any> upload(
        url: String,
        blob: ByteArray,
    ): Result<T, FuelError> {
        return withContext(Dispatchers.IO) {
            val request = createUploadRequest(url = url)
            request.add(BlobDataPart(blob.inputStream().buffered(), name = "file"))
            val deserializer: Deserializable<T> = deserializer()
            @Suppress("UNCHECKED_CAST")
            when (String::class) {
                String::class -> request.awaitStringResult() as Result<T, FuelError>
                else -> request.awaitResponseResult(deserializer).third
            }
        }
    }

    suspend inline fun download(
        url: String
    ): Result<ByteArray, FuelError> {
        return withContext(Dispatchers.IO) {
            val request = createRequest(url = url, method = Method.GET)
            request.awaitByteArrayResult()
        }
    }

    companion object {
        val HEADER_CONTENT_TYPE_JSON = "Content-Type" to "application/json"
        val HEADER_REQUESTED_WITH = "X-Requested-With" to "MkDocsEditor-Android"
        const val DEFAULT_TIMEOUT = 3000
        private const val NOT_CONFIGURED_HOST = "backend-not-configured.invalid"
    }

}

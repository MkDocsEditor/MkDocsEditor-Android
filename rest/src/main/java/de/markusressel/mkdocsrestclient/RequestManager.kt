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

import com.github.kittinunf.fuel.core.Deserializable
import com.github.kittinunf.fuel.core.FoldableResponseInterceptor
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.FuelManager
import com.github.kittinunf.fuel.core.Method
import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.core.Response
import com.github.kittinunf.fuel.core.awaitResponseResult
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.coroutines.awaitStringResult
import com.github.kittinunf.result.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Created by Markus on 08.02.2018.
 */
class RequestManager(
    hostname: String = "localhost",
    port: Int = 8080,
    ssl: Boolean = true,
    var basicAuthConfig: BasicAuthConfig? = null
) {

    /**
     * The hostname of the server
     */
    var hostname: String = hostname
        set(value) {
            field = value
            updateBaseUrl()
        }

    /**
     * The port to use
     */
    var port: Int = port
        set(value) {
            field = value
            updateBaseUrl()
        }

    /**
     * Whether to use https instead of http
     */
    var ssl: Boolean = ssl
        set(value) {
            field = value
            updateBaseUrl()
        }

    private val fuelManager = FuelManager()

    private val loggingInterceptor: FoldableResponseInterceptor =
        { next: (Request, Response) -> Response ->
            { req: Request, res: Response ->
                Timber.v(req.toString())
                Timber.v(res.toString())
                next(req, res)
            }
        }


    init {
        updateBaseUrl()
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
     * Updates the base URL in Fuel client according to configuration parameters
     */
    private fun updateBaseUrl() {
        val protocol = if (ssl) "https" else "http"
        val sanitized = hostname.removePrefix("http://").removePrefix("https://")
        val basePath = "$protocol://${sanitized.substringBefore("/")}:$port/${
            sanitized.substringAfter(
                "/",
                ""
            )
        }"
        fuelManager.basePath = basePath
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
        urlParameters: List<Pair<String, Any?>> = emptyList(),
        method: Method,
        timeout: Int = DEFAULT_TIMEOUT
    ): Request {
        return getAuthenticatedRequest(fuelManager.request(method, url, urlParameters))
            .timeout(timeout = timeout)
    }

    /**
     * Applies basic authentication parameters to a request
     */
    private fun getAuthenticatedRequest(request: Request): Request {
        basicAuthConfig?.let {
            return request.authentication().basic(username = it.username, password = it.password)
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
        urlParameters: List<Pair<String, Any?>>,
        method: Method
    ): Result<T, FuelError> {
        return withContext(Dispatchers.IO) {
            val deserializer: Deserializable<T> = deserializer()
            val request = createRequest(url = url, urlParameters = urlParameters, method = method)
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
    ): Result<T, FuelError> {
        return withContext(Dispatchers.IO) {
            val json = jsonData.toJson()
            val deserializer: Deserializable<T> = deserializer()
            val request =
                createRequest(url = url, method = method)
                    .body(json)
                    .header(HEADER_CONTENT_TYPE_JSON)
            when (T::class) {
                String::class -> request.awaitStringResult() as Result<T, FuelError>
                else -> request.awaitResponseResult(deserializer).third
            }
        }
    }

    companion object {
        val HEADER_CONTENT_TYPE_JSON = "Content-Type" to "application/json"
        const val DEFAULT_TIMEOUT = 3000
    }

}

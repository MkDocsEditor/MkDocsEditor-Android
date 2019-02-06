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

import android.util.Log
import com.github.kittinunf.fuel.core.*
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.rx.rxObject
import com.github.kittinunf.fuel.rx.rxResponsePair
import com.google.gson.Gson
import io.reactivex.Single

/**
 * Created by Markus on 08.02.2018.
 */
class RequestManager(hostname: String = "localhost", apiResource: String = "", var basicAuthConfig: BasicAuthConfig? = null) {

    var hostname: String = hostname
        set(value) {
            field = value
            updateBaseUrl()
        }

    var apiResource: String = apiResource
        set(value) {
            field = value
            updateBaseUrl()
        }

    private val fuelManager = FuelManager()

    init {
        addLogger()
        updateBaseUrl()
    }

    /**
     * Adds loggers to Fuel requests
     */
    private fun addLogger() {
        fuelManager
                .addResponseInterceptor { next: (Request, Response) -> Response ->
                    { req: Request, res: Response ->
                        Log.v("Fuel-Request", req.toString())
                        Log.v("Fuel-Response", res.toString())
                        next(req, res)
                    }
                }
    }

    /**
     * Updates the base URL in Fuel client according to configuration parameters
     */
    private fun updateBaseUrl() {
        fuelManager
                .basePath = "http://$hostname"
        if (apiResource.isNotEmpty()) {
            fuelManager
                    .basePath = fuelManager.basePath + "/$apiResource/"
        }
    }

    /**
     * Creates an (authenticated) request
     *
     * @param url the url
     * @param urlParameters query parameters
     * @param method the request type (f.ex. GET)
     */
    private fun createRequest(url: String, urlParameters: List<Pair<String, Any?>> = emptyList(), method: Method, timeout: Int = DEFAULT_TIMEOUT): Request {
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
     * Do a generic request
     *
     * @param url the URL
     * @param method the request type (f.ex. GET)
     */
    fun doRequest(url: String, method: Method): Single<Pair<Response, ByteArray>> {
        return createRequest(url = url, method = method)
                .rxResponsePair()
    }

    /**
     * Do a simple request that expects a json response body
     *
     * @param url the URL
     * @param method the request type (f.ex. GET)
     * @param deserializer a deserializer for the response json body
     */
    fun <T : Any> doRequest(url: String, method: Method, deserializer: Deserializable<T>): Single<T> {
        return createRequest(url = url, method = method)
                .rxObject(deserializer)
                .map {
                    it.component1() ?: throw it.component2() ?: throw Exception()
                }
    }

    /**
     * Do a request with query parameters that expects a json response body
     *
     * @param url the URL
     * @param urlParameters url query parameters
     * @param method the request type (f.ex. GET)
     * @param deserializer a deserializer for the <b>response</b> json body
     */
    fun <T : Any> doRequest(url: String, urlParameters: List<Pair<String, Any?>>, method: Method, deserializer: Deserializable<T>): Single<T> {
        return createRequest(url = url, urlParameters = urlParameters, method = method)
                .rxObject(deserializer)
                .map {
                    it.component1() ?: throw it.component2() ?: throw Exception()
                }
    }

    /**
     * Do a request with a json body that expects a json response body
     *
     * @param url the URL
     * @param method the request type (f.ex. GET)
     * @param jsonData an Object that will be serialized to json
     * @param deserializer a deserializer for the <b>response</b> json body
     */
    fun <T : Any> doJsonRequest(url: String, method: Method, jsonData: Any, deserializer: Deserializable<T>): Single<T> {
        val json = Gson()
                .toJson(jsonData)

        return createRequest(url = url, method = method)
                .body(json)
                .header(HEADER_CONTENT_TYPE_JSON)
                .rxObject(deserializer)
                .map {
                    it.component1() ?: throw it.component2() ?: throw Exception()
                }
    }

    /**
     * Do a request with a json body
     *
     * @param url the URL
     * @param method the request type (f.ex. GET)
     * @param jsonData an Object that will be serialized to json
     */
    fun doJsonRequest(url: String, method: Method, jsonData: Any): Single<Pair<Response, ByteArray>> {
        val json = Gson()
                .toJson(jsonData)

        return createRequest(url = url, method = method)
                .body(json)
                .header(HEADER_CONTENT_TYPE_JSON)
                .rxResponsePair()
    }

    companion object {
        val HEADER_CONTENT_TYPE_JSON = "Content-Type" to "application/json"
        const val DEFAULT_TIMEOUT = 3000
    }

}
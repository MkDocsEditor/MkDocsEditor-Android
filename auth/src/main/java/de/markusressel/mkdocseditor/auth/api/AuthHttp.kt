package de.markusressel.mkdocseditor.auth.api

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * An error answer of the backend, see docs/auth.md section 10.
 *
 * @param status the HTTP status code
 * @param code the machine-readable error code ([AuthErrorCode]), if the body had one
 * @param details the optional details object of the error
 * @param retryAfterSeconds the Retry-After header of a 429 answer
 */
class AuthApiException(
    val status: Int,
    val code: String?,
    message: String?,
    val details: Map<String, Any?> = emptyMap(),
    val retryAfterSeconds: Long? = null,
) : IOException(message ?: "HTTP $status") {

    /**
     * true if the session (or app token) is not valid (anymore) and the user must sign in again
     */
    val isUnauthenticated: Boolean
        get() = status == 401 && (code == null || code == AuthErrorCode.UNAUTHENTICATED)
}

/**
 * Small JSON-over-HTTP helper for the account endpoints of one backend.
 *
 * @param baseUrl the backend URL, like `https://wiki.example.com:8443/`
 */
internal class AuthHttp(
    baseUrl: String,
    private val client: OkHttpClient,
    val moshi: Moshi,
) {
    private val base: HttpUrl = (baseUrl.trimEnd('/') + "/").toHttpUrl()

    private val errorAdapter = moshi.adapter(ErrorBody::class.java)

    fun url(path: String): HttpUrl = requireNotNull(base.resolve(path.trimStart('/'))) {
        "invalid path $path"
    }

    /**
     * Sends a request and returns the parsed answer.
     *
     * @param token the bearer session token, or null for the unauthenticated endpoints
     * @param body the request body, null for none
     * @throws AuthApiException for error answers
     * @throws IOException for network errors
     */
    suspend fun <T> call(
        method: String,
        path: String,
        token: String? = null,
        body: RequestBody? = null,
        response: JsonAdapter<T>? = null,
    ): T? = withContext(Dispatchers.IO) {
        val needsBody = method == "POST" || method == "PUT" || method == "PATCH"
        val request = Request.Builder()
            .url(url(path))
            .method(method, body ?: if (needsBody) EMPTY_JSON_OBJECT.toRequestBody(JSON) else null)
            .header("Accept", "application/json")
            // required on unauthenticated POSTs (CSRF check), harmless on the others
            .header("X-Requested-With", X_REQUESTED_WITH)
            .apply { token?.let { header("Authorization", "Bearer $it") } }
            .build()

        client.newCall(request).execute().use { answer ->
            val text = answer.body.string()
            if (!answer.isSuccessful) {
                throw parseError(answer.code, text, answer.header("Retry-After"))
            }
            if (response == null || text.isBlank()) null else response.fromJson(text)
        }
    }

    fun <B> json(adapter: JsonAdapter<B>, value: B): RequestBody =
        adapter.toJson(value).toRequestBody(JSON)

    private fun parseError(status: Int, text: String, retryAfter: String?): AuthApiException {
        val error = try {
            errorAdapter.fromJson(text)
        } catch (_: Exception) {
            null
        }
        val details = error?.details.orEmpty()
        val retryAfterSeconds = retryAfter?.trim()?.toLongOrNull()
            ?: (details["retryAfter"] as? Number)?.toLong()
        return AuthApiException(
            status = status,
            code = error?.code,
            message = error?.message,
            details = details,
            retryAfterSeconds = retryAfterSeconds,
        )
    }

    @JsonClass(generateAdapter = true)
    internal data class ErrorBody(
        val name: String? = null,
        val code: String? = null,
        val message: String? = null,
        val details: Map<String, Any?>? = null,
    )

    companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
        const val X_REQUESTED_WITH = "MkDocsEditor-Android"
        private const val EMPTY_JSON_OBJECT = "{}"
    }
}

package de.markusressel.mkdocseditor.auth.api

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import java.time.Instant
import java.time.OffsetDateTime

/**
 * RFC 3339 timestamps
 */
internal object InstantJsonAdapter : JsonAdapter<Instant>() {
    override fun fromJson(reader: JsonReader): Instant? {
        if (reader.peek() == JsonReader.Token.NULL) return reader.nextNull()
        return OffsetDateTime.parse(reader.nextString()).toInstant()
    }

    override fun toJson(writer: JsonWriter, value: Instant?) {
        if (value == null) writer.nullValue() else writer.value(value.toString())
    }
}

internal fun authMoshi(): Moshi = Moshi.Builder()
    .add(Instant::class.java, InstantJsonAdapter)
    .build()

/**
 * Reads a JSON value as its raw text, so WebAuthn options reach Credential Manager unchanged
 */
internal fun JsonReader.readRawJson(): String = nextSource().use { it.readUtf8() }

/**
 * Writes already serialized JSON as a value
 */
internal fun JsonWriter.rawJsonValue(json: String) {
    jsonValue(JsonReader.of(Buffer().writeUtf8(json)).use { it.readJsonValue() })
}

/**
 * Builds a JSON object request body. Null values are left out.
 */
internal fun jsonObject(block: JsonObjectBuilder.() -> Unit): RequestBody {
    val buffer = Buffer()
    JsonWriter.of(buffer).use { writer ->
        writer.beginObject()
        JsonObjectBuilder(writer).block()
        writer.endObject()
    }
    return buffer.readByteArray().toRequestBody(AuthHttp.JSON)
}

internal class JsonObjectBuilder(private val writer: JsonWriter) {
    fun put(name: String, value: String?) {
        if (value != null) writer.name(name).value(value)
    }

    fun put(name: String, value: Boolean?) {
        if (value != null) writer.name(name).value(value)
    }

    fun put(name: String, value: Number?) {
        if (value != null) writer.name(name).value(value)
    }

    /**
     * Adds a value that is already serialized JSON
     */
    fun putRaw(name: String, json: String) {
        writer.name(name).rawJsonValue(json)
    }
}

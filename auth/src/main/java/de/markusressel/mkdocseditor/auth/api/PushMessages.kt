package de.markusressel.mkdocseditor.auth.api

import com.squareup.moshi.JsonReader
import okio.Buffer

/**
 * The decrypted payload of a push from the backend, like `{"type": "feed-changed", "entryId": "42"}`, see
 * docs/push.md of MkDocsEditor-Backend
 */
object PushMessages {
    /** something in the feed changed, which the client then reads itself */
    const val FEED_CHANGED = "feed-changed"

    /** @return the type of the message, null if it is no JSON object with one */
    fun type(payload: ByteArray): String? = try {
        JsonReader.of(Buffer().write(payload)).use { reader ->
            var type: String? = null
            reader.beginObject()
            while (reader.hasNext()) {
                if (reader.nextName() == "type" && reader.peek() == JsonReader.Token.STRING) type = reader.nextString()
                else reader.skipValue()
            }
            reader.endObject()
            type
        }
    } catch (_: Exception) {
        null
    }
}

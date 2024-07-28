package de.markusressel.mkdocsrestclient.sync.automerge.adapter

import com.github.kittinunf.fuel.util.decodeBase64
import com.github.kittinunf.fuel.util.encodeBase64
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import org.automerge.SyncState
import java.io.IOException
import java.nio.charset.Charset

class SyncStateJsonAdapter : JsonAdapter<SyncState>() {

    @Throws(IOException::class)
    @Synchronized
    override fun fromJson(reader: JsonReader): SyncState? {
        return if (reader.peek() == JsonReader.Token.NULL) {
            reader.nextNull()
        } else {
            val encodedSyncState = reader.nextString().decodeBase64()
            SyncState.decode(encodedSyncState)
        }
    }

    @Throws(IOException::class)
    @Synchronized
    override fun toJson(writer: JsonWriter, value: SyncState?) {
        if (value == null) {
            writer.nullValue()
        } else {
            val string: String = value.encode().encodeBase64().toString(Charset.forName("UTF-8"))
            writer.value(string)
        }
    }
}

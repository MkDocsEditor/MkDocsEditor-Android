package de.markusressel.mkdocsrestclient.sync.automerge.adapter

import com.github.kittinunf.fuel.util.decodeBase64
import com.github.kittinunf.fuel.util.encodeBase64
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import de.markusressel.mkdocsrestclient.sync.automerge.SyncMessageWrapper
import java.io.IOException
import java.nio.charset.Charset

class SyncMessageJsonAdapter : JsonAdapter<SyncMessageWrapper>() {

    @Throws(IOException::class)
    @Synchronized
    override fun fromJson(reader: JsonReader): SyncMessageWrapper? {
        return if (reader.peek() == JsonReader.Token.NULL) {
            reader.nextNull()
        } else {
            reader.nextString().decodeBase64()?.let {
                SyncMessageWrapper(syncMessage = it)
            }
        }
    }

    @Throws(IOException::class)
    @Synchronized
    override fun toJson(writer: JsonWriter, value: SyncMessageWrapper?) {
        if (value == null) {
            writer.nullValue()
        } else {
            val string: String = value.syncMessage.encodeBase64().toString(Charset.forName("UTF-8"))
            writer.value(string)
        }
    }
}

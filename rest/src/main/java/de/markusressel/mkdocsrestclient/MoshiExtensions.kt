package de.markusressel.mkdocsrestclient

import com.github.kittinunf.fuel.core.Deserializable
import com.github.kittinunf.fuel.core.ResponseDeserializable
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.Rfc3339DateJsonAdapter
import de.markusressel.mkdocsrestclient.sync.automerge.SyncMessageWrapper
import de.markusressel.mkdocsrestclient.sync.automerge.adapter.DocumentJsonAdapter
import de.markusressel.mkdocsrestclient.sync.automerge.adapter.SyncMessageJsonAdapter
import de.markusressel.mkdocsrestclient.sync.automerge.adapter.SyncStateJsonAdapter
import okio.buffer
import okio.source
import org.automerge.Document
import org.automerge.SyncState
import java.io.InputStream
import java.util.Date

val moshi: Moshi = Moshi.Builder()
    .add(Date::class.java, Rfc3339DateJsonAdapter())
    .add(Document::class.java, DocumentJsonAdapter())
    .add(SyncState::class.java, SyncStateJsonAdapter())
    .add(SyncMessageWrapper::class.java, SyncMessageJsonAdapter())
    .build()

inline fun <reified T : Any> deserializer(): Deserializable<T> =
    object : ResponseDeserializable<T> {

        private val adapter = moshi.adapter(T::class.java)

        override fun deserialize(inputStream: InputStream): T? =
            adapter.lenient().fromJson(
                inputStream.source().buffer()
            )

        override fun deserialize(content: String): T? = adapter.fromJson(content)
    }

inline fun <reified T> T.toJson(): String {
    // TODO: this is cool and all, but probably not very efficient?
    return moshi.adapter(T::class.java).toJson(this)
}

inline fun <reified T> String.toEntity(): T {
    // TODO: this is cool and all, but probably not very efficient?
    return moshi.adapter(T::class.java).lenient().nonNull().fromJson(this)!!
}

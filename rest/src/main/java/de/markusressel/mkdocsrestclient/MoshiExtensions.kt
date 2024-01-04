package de.markusressel.mkdocsrestclient

import com.github.kittinunf.fuel.core.Deserializable
import com.github.kittinunf.fuel.core.ResponseDeserializable
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.Rfc3339DateJsonAdapter
import okio.buffer
import okio.source
import java.io.InputStream
import java.util.Date

val moshi: Moshi = Moshi.Builder()
    .add(Date::class.java, Rfc3339DateJsonAdapter())
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

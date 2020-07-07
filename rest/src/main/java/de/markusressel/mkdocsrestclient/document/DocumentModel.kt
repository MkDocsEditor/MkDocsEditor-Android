package de.markusressel.mkdocsrestclient.document

import com.github.kittinunf.fuel.core.ResponseDeserializable
import com.github.salomonbrys.kotson.fromJson
import com.google.gson.Gson
import java.util.*

/**
 * Created by Markus on 03.06.2018.
 */
data class DocumentModel(
        val type: String,
        val id: String,
        val name: String, val filesize: Long, val modtime: Date, val url: String
) {

    class SingleDeserializer : ResponseDeserializable<DocumentModel> {
        override fun deserialize(content: String): DocumentModel? {
            return Gson().fromJson(content)
        }
    }

    class ListDeserializer : ResponseDeserializable<List<DocumentModel>> {

        override fun deserialize(content: String): List<DocumentModel>? {
            if (content.isEmpty()) {
                return emptyList()
            }

            return Gson().fromJson(content)
        }

    }

}
package de.markusressel.mkdocsrestclient.resource

import com.github.kittinunf.fuel.core.ResponseDeserializable
import com.github.salomonbrys.kotson.fromJson
import com.google.gson.Gson
import java.util.*

/**
 * Created by Markus on 03.06.2018.
 */
data class ResourceModel(
        val type: String,
        val id: String,
        val name: String,
        val filesize: Long,
        val modtime: Date
) {

    class SingleDeserializer : ResponseDeserializable<ResourceModel> {
        override fun deserialize(content: String): ResourceModel? {
            return Gson()
                    .fromJson(content)
        }
    }

    class ListDeserializer : ResponseDeserializable<List<ResourceModel>> {

        override fun deserialize(content: String): List<ResourceModel>? {
            if (content.isEmpty()) {
                return emptyList()
            }

            return Gson()
                    .fromJson(content)
        }

    }

}
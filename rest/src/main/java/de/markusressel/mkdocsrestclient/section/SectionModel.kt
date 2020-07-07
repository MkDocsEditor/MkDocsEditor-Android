package de.markusressel.mkdocsrestclient.section

import com.github.kittinunf.fuel.core.ResponseDeserializable
import com.github.salomonbrys.kotson.fromJson
import com.google.gson.Gson
import de.markusressel.mkdocsrestclient.document.DocumentModel
import de.markusressel.mkdocsrestclient.resource.ResourceModel

/**
 * Created by Markus on 03.06.2018.
 */
data class SectionModel(
        val id: String,
        val name: String,
        val subsections: List<SectionModel>,
        val documents: List<DocumentModel>,
        val resources: List<ResourceModel>
) {

    class SingleDeserializer : ResponseDeserializable<SectionModel> {
        override fun deserialize(content: String): SectionModel? {
            return Gson().fromJson(content)
        }
    }

    class ListDeserializer : ResponseDeserializable<List<SectionModel>> {

        override fun deserialize(content: String): List<SectionModel>? {
            if (content.isEmpty()) {
                return emptyList()
            }

            return Gson().fromJson(content)
        }

    }

}
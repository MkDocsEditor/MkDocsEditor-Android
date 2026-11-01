package de.markusressel.mkdocsrestclient.reference

import com.github.kittinunf.fuel.core.FuelError
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import java.util.Date

/**
 * Types of items in the tree, as the backend names them
 */
object ItemType {
    const val SECTION = "section"
    const val DOCUMENT = "document"
    const val RESOURCE = "resource"
}

/**
 * A reference to an item of the tree (docs/references.md of the backend)
 *
 * @param type [ItemType]
 * @param path path relative to the docs folder, `""` for the root section
 */
@JsonClass(generateAdapter = true)
data class ItemRefModel(
    val type: String,
    val id: String,
    val path: String,
    val name: String,
)

/**
 * A link, image, reference definition or HTML reference in a document
 *
 * @param kind `link`, `image`, `definition` or `html`
 * @param line 1-based
 * @param column 1-based, in code points, of the first character of the destination
 * @param status `ok` or `broken`
 * @param target the resolved item, null when broken
 * @param reason `not-found` or `outside-docs`, only when broken
 * @param path the resolved path that does not exist, only when broken
 * @param movedTo only when broken: the item that was at [path] before it moved
 * @param suggestedDestination only with [movedTo]: the destination that fixes the reference
 */
@JsonClass(generateAdapter = true)
data class ReferenceModel(
    val kind: String,
    val line: Int,
    val column: Int,
    val destination: String,
    val anchor: String,
    val status: String,
    val target: ItemRefModel?,
    val reason: String? = null,
    val path: String? = null,
    val movedTo: ItemRefModel? = null,
    val suggestedDestination: String? = null,
)

@JsonClass(generateAdapter = true)
data class DocumentReferencesModel(
    val document: ItemRefModel,
    val references: List<ReferenceModel>,
)

/**
 * A reference to an item from a document
 *
 * @param internal section backlinks only: the source document is inside the section
 */
@JsonClass(generateAdapter = true)
data class BacklinkModel(
    val source: ItemRefModel,
    val kind: String,
    val line: Int,
    val column: Int,
    val destination: String,
    val anchor: String,
    val target: ItemRefModel,
    val internal: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class BacklinksModel(
    val item: ItemRefModel,
    val backlinks: List<BacklinkModel>,
)

@JsonClass(generateAdapter = true)
data class LinkResultModel(
    val destination: String,
    val markdown: String,
    val target: ItemRefModel,
)

/**
 * Request of `POST /move/`
 *
 * @param parent id of the target section, null to rename in place
 * @param name new name (documents without `.md`), null to keep the name
 */
@JsonClass(generateAdapter = true)
data class MoveRequestModel(
    val type: String,
    val id: String,
    val parent: String? = null,
    val name: String? = null,
    val dryRun: Boolean,
    val updateReferences: Boolean = true,
)

@JsonClass(generateAdapter = true)
data class MovedItemModel(
    val type: String,
    val oldId: String,
    val newId: String,
    val oldPath: String,
    val newPath: String,
)

/**
 * @param line 1-based line number in the text before the move
 */
@JsonClass(generateAdapter = true)
data class LineEditModel(
    val line: Int,
    val before: String,
    val after: String,
)

@JsonClass(generateAdapter = true)
data class MovedDocumentModel(
    val documentId: String,
    val newDocumentId: String,
    val path: String,
    val newPath: String,
    val edits: List<LineEditModel>,
)

@JsonClass(generateAdapter = true)
data class ConfigEditModel(
    val file: String,
    val line: Int,
    val before: String,
    val after: String,
)

/**
 * Answer of `POST /move/`: what changed, or what would change in a dry run
 */
@JsonClass(generateAdapter = true)
data class MoveResultModel(
    val dryRun: Boolean,
    val type: String,
    val oldId: String,
    val newId: String,
    val oldPath: String,
    val newPath: String,
    val moved: List<MovedItemModel>,
    val documents: List<MovedDocumentModel>,
    val config: List<ConfigEditModel>,
    val commit: String? = null,
)

/**
 * Answer of `GET /moved/{id}/`: where a moved item is now
 */
@JsonClass(generateAdapter = true)
data class MovedModel(
    val oldId: String,
    val type: String,
    val item: ItemRefModel,
    val movedAt: Date,
)

/**
 * Details of an error answer
 *
 * @param reason for example `exists` or `invalid-name` (docs/references.md section 11)
 */
@JsonClass(generateAdapter = true)
data class ApiErrorDetailsModel(
    val reason: String? = null,
    val path: String? = null,
)

/**
 * The body of an error answer of the backend: `{"code", "message", "details"}`
 */
@JsonClass(generateAdapter = true)
data class ApiErrorModel(
    val code: String? = null,
    val message: String? = null,
    val details: ApiErrorDetailsModel? = null,
) {
    companion object {
        private val adapter by lazy { Moshi.Builder().build().adapter(ApiErrorModel::class.java) }

        /** the error in a response body, null when the body is not one */
        fun parse(body: ByteArray): ApiErrorModel? = try {
            adapter.fromJson(String(body, Charsets.UTF_8))
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * True when the server does not have the endpoint at all: an older backend answers 404 (or
 * 405) with the web framework's default body, which has no error `code`. Errors of the backend
 * itself, like an unknown id, always carry a `code`.
 */
fun FuelError.isUnsupportedEndpoint(): Boolean =
    response.statusCode in UNSUPPORTED_STATUS && ApiErrorModel.parse(response.data)?.code == null

private val UNSUPPORTED_STATUS = setOf(404, 405)

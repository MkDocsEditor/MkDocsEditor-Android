package de.markusressel.mkdocsrestclient.qr

import com.squareup.moshi.JsonClass
import de.markusressel.mkdocsrestclient.moshi
import de.markusressel.mkdocsrestclient.reference.ItemRefModel
import java.util.Date

/**
 * Answer of `GET /qr/{key}/`: the one page with the key (docs/qr.md of the backend)
 *
 * @param label where the sticker is, null without one
 * @param title the page title
 */
@JsonClass(generateAdapter = true)
data class QrCodeModel(
    val key: String,
    val label: String? = null,
    val item: ItemRefModel,
    val title: String,
    val lastScannedAt: Date? = null,
)

/** A page that has a key */
@JsonClass(generateAdapter = true)
data class QrPageModel(
    val item: ItemRefModel,
    val title: String,
)

/**
 * The details of the errors of `GET /qr/{key}/`: the pages of a 409 `qr-conflict`, or where the key was for a 410
 * `qr-gone`
 */
@JsonClass(generateAdapter = true)
data class QrErrorDetailsModel(
    val key: String? = null,
    val items: List<QrPageModel> = emptyList(),
    val label: String? = null,
    val lastPath: String? = null,
    val lastScannedAt: Date? = null,
)

/** An error answer of `GET /qr/{key}/` */
@JsonClass(generateAdapter = true)
data class QrErrorModel(
    val code: String? = null,
    val message: String? = null,
    val details: QrErrorDetailsModel? = null,
) {
    companion object {
        const val CONFLICT = "qr-conflict"
        const val GONE = "qr-gone"

        private val adapter by lazy { moshi.adapter(QrErrorModel::class.java) }

        /** the error in a response body, null when the body is not one */
        fun parse(body: ByteArray): QrErrorModel? = try {
            adapter.fromJson(String(body, Charsets.UTF_8))
        } catch (e: Exception) {
            null
        }
    }
}

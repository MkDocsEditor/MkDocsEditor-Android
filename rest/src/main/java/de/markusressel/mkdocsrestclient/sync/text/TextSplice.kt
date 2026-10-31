package de.markusressel.mkdocsrestclient.sync.text

/**
 * Replaces [deleteCount] characters at [index] with [insert].
 *
 * Indices and counts are UTF-16 code units, like [String] indices in Kotlin. A list of
 * splices is always applied in order, so the index of each splice refers to the text
 * after all previous splices of the list have been applied.
 */
data class TextSplice(
    val index: Int,
    val deleteCount: Int,
    val insert: String,
) {
    init {
        require(index >= 0) { "index must not be negative: $index" }
        require(deleteCount >= 0) { "deleteCount must not be negative: $deleteCount" }
    }

    fun applyTo(text: String): String {
        return text.substring(0, index) + insert + text.substring(index + deleteCount)
    }
}

/**
 * Applies the given splices to this text, in order.
 */
fun String.applySplices(splices: List<TextSplice>): String {
    return splices.fold(this) { text, splice -> splice.applyTo(text) }
}

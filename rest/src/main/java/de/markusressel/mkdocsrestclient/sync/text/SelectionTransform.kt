package de.markusressel.mkdocsrestclient.sync.text

/**
 * Moves a position in a text through a list of splices (applied in order), so that it
 * stays at the same character when text is inserted or deleted elsewhere.
 *
 * - Text inserted at or after the position does not move it.
 * - Text inserted or deleted before the position moves it by the length difference.
 * - A position inside a deleted range moves to the start of that range.
 *
 * @param index a UTF-16 index into the text before the splices
 * @return the UTF-16 index into the text after the splices
 */
fun transformIndex(index: Int, splices: List<TextSplice>): Int {
    var result = index
    for (splice in splices) {
        result = when {
            result <= splice.index -> result
            result >= splice.index + splice.deleteCount ->
                result + splice.insert.length - splice.deleteCount

            else -> splice.index
        }
    }
    return result
}

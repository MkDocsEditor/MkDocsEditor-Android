package de.markusressel.mkdocseditor.feature.history.domain

import de.markusressel.mkdocsrestclient.history.DiffLineModel
import de.markusressel.mkdocsrestclient.history.DiffOp

/** A changed part of a line, as character indices, end exclusive */
internal data class Highlight(val start: Int, val end: Int)

/** The changed words of a removed line and of the added line that replaced it */
internal data class WordChanges(val removed: List<Highlight>, val added: List<Highlight>)

/** Longer lines are marked as a whole: the comparison takes the product of both lengths */
private const val MAX_TOKENS = 400

private val token = Regex("""\w+|\s+|[^\w\s]""")

/** Below this share of unchanged text, two lines are different lines and not one changed line */
private const val MIN_UNCHANGED_SHARE = 0.4

/**
 * Which words of [old] were removed and which of [new] were added. Words, spaces and single signs count as units, so a
 * change marks whole words. Lines with little in common get no marks: the line color says enough, and marks on nearly
 * every word only hide the few that stayed.
 */
internal fun wordChanges(old: String, new: String): WordChanges {
    val a = token.findAll(old).toList()
    val b = token.findAll(new).toList()
    if (a.size > MAX_TOKENS || b.size > MAX_TOKENS) {
        return WordChanges(listOfNotNull(whole(old)), listOfNotNull(whole(new)))
    }
    // longest common subsequence of the tokens, from the end
    val lcs = Array(a.size + 1) { IntArray(b.size + 1) }
    for (i in a.indices.reversed()) {
        for (j in b.indices.reversed()) {
            lcs[i][j] = if (a[i].value == b[j].value) lcs[i + 1][j + 1] + 1 else maxOf(lcs[i + 1][j], lcs[i][j + 1])
        }
    }
    val removed = mutableListOf<IntRange>()
    val added = mutableListOf<IntRange>()
    var i = 0
    var j = 0
    while (i < a.size || j < b.size) {
        when {
            i < a.size && j < b.size && a[i].value == b[j].value -> { i++; j++ }
            j < b.size && (i == a.size || lcs[i][j + 1] >= lcs[i + 1][j]) -> added += b[j++].range
            else -> removed += a[i++].range
        }
    }
    val changes = WordChanges(removed.merged(), added.merged())
    val unchanged = old.length - changes.removed.sumOf { it.end - it.start }
    val longer = maxOf(old.length, new.length)
    return if (longer > 0 && old.isNotEmpty() && new.isNotEmpty() && unchanged < MIN_UNCHANGED_SHARE * longer) {
        WordChanges(emptyList(), emptyList())
    } else {
        changes
    }
}

private fun whole(text: String) = if (text.isEmpty()) null else Highlight(0, text.length)

/** neighbouring tokens become one highlight */
private fun List<IntRange>.merged(): List<Highlight> {
    val result = mutableListOf<Highlight>()
    for (range in this) {
        val last = result.lastOrNull()
        if (last != null && last.end == range.first) {
            result[result.size - 1] = last.copy(end = range.last + 1)
        } else {
            result += Highlight(range.first, range.last + 1)
        }
    }
    return result
}

/**
 * Pairs each removed line with the added line that took its place, so their changed words can be marked. A block of
 * removed lines followed by a block of added lines pairs up in order; lines left over have no partner.
 *
 * @return the index of the partner of each line in [lines], null without one
 */
internal fun pairChangedLines(lines: List<DiffLineModel>): List<Int?> {
    val partner = arrayOfNulls<Int>(lines.size)
    var index = 0
    while (index < lines.size) {
        if (lines[index].op != DiffOp.REMOVED) {
            index++
            continue
        }
        val removedStart = index
        while (index < lines.size && lines[index].op == DiffOp.REMOVED) index++
        val addedStart = index
        while (index < lines.size && lines[index].op == DiffOp.ADDED) index++
        val pairs = minOf(addedStart - removedStart, index - addedStart)
        for (k in 0 until pairs) {
            partner[removedStart + k] = addedStart + k
            partner[addedStart + k] = removedStart + k
        }
    }
    return partner.toList()
}

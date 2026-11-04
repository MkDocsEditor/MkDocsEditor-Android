package de.markusressel.mkdocseditor.feature.editor.domain

import de.markusressel.mkdocsrestclient.reference.AuthorshipModel

/**
 * @param start the first UTF-16 index, inclusive
 * @param end the last UTF-16 index, exclusive
 * @param author the username, null for imported text and for text that the next answer of the backend will name
 */
internal data class AuthorRun(val start: Int, val end: Int, val author: String?)

/**
 * Who wrote which part of the text. It moves with each edit, so it always fits the text on screen. An edit has no
 * author here: the backend knows who made it, and names it in its next answer.
 */
internal data class AuthorRuns(val runs: List<AuthorRun> = emptyList()) {

    /** @return these runs after replacing [removed] characters at [start] with [inserted] */
    fun applied(start: Int, removed: Int, inserted: Int): AuthorRuns {
        if (runs.isEmpty()) return this
        val removedEnd = start + removed
        val shift = inserted - removed
        val result = ArrayList<AuthorRun>(runs.size + 2)
        var insertedRun = inserted == 0
        fun addInserted() {
            if (!insertedRun) result.add(AuthorRun(start, start + inserted, null))
            insertedRun = true
        }
        runs.forEach { run ->
            if (run.start < start) result.add(run.copy(end = minOf(run.end, start)))
            if (run.end > removedEnd || run.start >= start) addInserted()
            if (run.end > removedEnd) {
                result.add(run.copy(start = maxOf(run.start, removedEnd) + shift, end = run.end + shift))
            }
        }
        addInserted()
        return AuthorRuns(result.merged())
    }
}

private fun List<AuthorRun>.merged(): List<AuthorRun> {
    val result = ArrayList<AuthorRun>(size)
    forEach { run ->
        val last = result.lastOrNull()
        when {
            run.start >= run.end -> Unit
            last != null && last.author == run.author && last.end == run.start ->
                result[result.lastIndex] = last.copy(end = run.end)

            else -> result.add(run)
        }
    }
    return result
}

/**
 * @param text the text of the state that this model describes
 * @return the runs in UTF-16 indices, null if they don't fit the text
 */
internal fun AuthorshipModel.toAuthorRuns(text: String): AuthorRuns? {
    var codePoint = 0
    var index = 0
    val result = runs.map { run ->
        if (run.start < codePoint || run.end < run.start) return null
        val start = text.advancedBy(index, run.start - codePoint) ?: return null
        val end = text.advancedBy(start, run.end - run.start) ?: return null
        codePoint = run.end
        index = end
        // the backend imports text under the names ":disk" and ":server", with a normal actor id
        AuthorRun(start, end, run.username.takeUnless { it.startsWith(":") || it.isBlank() })
    }
    return AuthorRuns(result.merged())
}

/** @return the UTF-16 index [codePoints] after [index], null behind the end of the text */
private fun String.advancedBy(index: Int, codePoints: Int): Int? =
    try {
        offsetByCodePoints(index, codePoints)
    } catch (_: IndexOutOfBoundsException) {
        null
    }

/** The part of a line that one author wrote */
internal data class LineShare(val author: String, val fraction: Float)

/** @return for each line of [text], who wrote what share of it. Text without an author is left out. */
internal fun lineShares(text: String, runs: List<AuthorRun>): List<List<LineShare>> {
    val result = ArrayList<List<LineShare>>()
    var lineStart = 0
    var runIndex = 0
    while (true) {
        val newline = text.indexOf('\n', lineStart)
        val lineEnd = if (newline < 0) text.length else newline
        val length = lineEnd - lineStart
        val shares = ArrayList<LineShare>()
        while (runIndex < runs.size && runs[runIndex].end <= lineStart) runIndex++
        var i = runIndex
        while (i < runs.size && runs[i].start < lineEnd) {
            val run = runs[i]
            val overlap = minOf(run.end, lineEnd) - maxOf(run.start, lineStart)
            if (run.author != null && overlap > 0) shares.add(LineShare(run.author, overlap.toFloat() / length))
            i++
        }
        result.add(shares)
        if (newline < 0) return result
        lineStart = newline + 1
    }
}

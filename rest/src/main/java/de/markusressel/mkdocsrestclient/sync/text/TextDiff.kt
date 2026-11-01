package de.markusressel.mkdocsrestclient.sync.text

/**
 * Computes the splices that turn one text into another.
 *
 * The diff works on Unicode code points, so a splice never splits a surrogate pair.
 * Common prefix and suffix are trimmed first, which makes the usual case (a single edit
 * while typing) cheap. The remaining middle part is diffed with the Myers algorithm so
 * that unrelated text between several edits (e.g. formatting markers around a selection)
 * is kept instead of being deleted and inserted again. If the middle part differs too
 * much, it is replaced with a single splice.
 */
object TextDiff {

    /**
     * Upper bound for the edit distance computed by the Myers algorithm.
     * Memory usage grows with the square of this value.
     */
    const val MAX_EDIT_DISTANCE = 1000

    /** An edit at the caret that replaces more than this much text is not treated as typing */
    private const val MAX_CARET_REPLACE = 64

    /**
     * The edit a user made by typing, where the caret is known. A text alone does not tell
     * where an edit was: typing "a" into "a|a" looks like typing it at the end. With other
     * users typing at the same time the place matters, and cursors follow it.
     *
     * @param caret the caret in [new], right behind the edit
     * @return one splice for an edit in front of the caret, or the result of [diff] if the
     * change is something else
     */
    fun diffAtCaret(old: String, new: String, caret: Int): List<TextSplice> {
        if (old == new) {
            return emptyList()
        }
        if (caret !in 0..new.length || new.length - caret > old.length ||
            !old.regionMatches(old.length - (new.length - caret), new, caret, new.length - caret)
        ) {
            return diff(old, new)
        }
        var suffix = new.length - caret
        var prefix = 0
        val maxPrefix = minOf(old.length - suffix, new.length - suffix)
        while (prefix < maxPrefix && old[prefix] == new[prefix]) {
            prefix++
        }
        // never split a surrogate pair
        if (prefix > 0 && old[prefix - 1].isHighSurrogate()) {
            prefix--
        }
        if (suffix > 0 && old[old.length - suffix].isLowSurrogate()) {
            suffix--
        }
        val deleted = old.length - suffix - prefix
        val inserted = new.length - suffix - prefix
        if (minOf(deleted, inserted) > MAX_CARET_REPLACE) {
            // not typing, keep the text that stayed the same
            return diff(old, new)
        }
        return listOf(TextSplice(prefix, deleted, new.substring(prefix, new.length - suffix)))
    }

    /**
     * @return the splices (UTF-16 indices, applied in order) that turn [old] into [new]
     */
    fun diff(old: String, new: String): List<TextSplice> {
        if (old == new) {
            return emptyList()
        }

        val a = old.codePoints().toArray()
        val b = new.codePoints().toArray()

        var prefix = 0
        while (prefix < a.size && prefix < b.size && a[prefix] == b[prefix]) {
            prefix++
        }
        var suffix = 0
        while (suffix < a.size - prefix && suffix < b.size - prefix &&
            a[a.size - 1 - suffix] == b[b.size - 1 - suffix]
        ) {
            suffix++
        }

        val aMid = a.copyOfRange(prefix, a.size - suffix)
        val bMid = b.copyOfRange(prefix, b.size - suffix)

        val hunks = myers(aMid, bMid)?.let(::mergeShortEqualities)
            ?: listOf(Hunk(0, aMid.size, 0, bMid.size))

        // UTF-16 offset of every code point index in the old text
        val oldOffsets = IntArray(a.size + 1)
        for (i in a.indices) {
            oldOffsets[i + 1] = oldOffsets[i] + Character.charCount(a[i])
        }

        var delta = 0
        return hunks.map { hunk ->
            val start = oldOffsets[prefix + hunk.aStart]
            val end = oldOffsets[prefix + hunk.aEnd]
            val insert = String(bMid, hunk.bStart, hunk.bEnd - hunk.bStart)
            TextSplice(
                index = start + delta,
                deleteCount = end - start,
                insert = insert,
            ).also {
                delta += insert.length - (end - start)
            }
        }
    }

    /**
     * A changed region: code points [aStart, aEnd) of the old text are replaced
     * with code points [bStart, bEnd) of the new text.
     */
    private data class Hunk(val aStart: Int, val aEnd: Int, val bStart: Int, val bEnd: Int) {
        val size: Int
            get() = maxOf(aEnd - aStart, bEnd - bStart)
    }

    /**
     * Merges neighbouring hunks when the unchanged text between them is not longer than
     * the changes on both sides (like the semantic cleanup of diff-match-patch).
     * This avoids splices that keep single matching characters inside replaced words.
     */
    private fun mergeShortEqualities(hunks: List<Hunk>): List<Hunk> {
        val result = ArrayList<Hunk>(hunks.size)
        hunks.forEach { hunk ->
            val previous = result.lastOrNull()
            if (previous != null) {
                val gap = hunk.aStart - previous.aEnd
                if (gap <= previous.size && gap <= hunk.size) {
                    result[result.size - 1] = Hunk(previous.aStart, hunk.aEnd, previous.bStart, hunk.bEnd)
                    return@forEach
                }
            }
            result.add(hunk)
        }
        return result
    }

    /**
     * Myers' O((N+M)D) diff.
     *
     * @return the changed regions in ascending order, or null if the edit distance
     * exceeds [MAX_EDIT_DISTANCE]
     */
    private fun myers(a: IntArray, b: IntArray): List<Hunk>? {
        val n = a.size
        val m = b.size
        if (n == 0 || m == 0) {
            return listOf(Hunk(0, n, 0, m))
        }

        val limit = minOf(n + m, MAX_EDIT_DISTANCE)
        val offset = limit + 1
        val v = IntArray(2 * limit + 3)
        // trace[d] holds v[-d-1..d+1] as it was before step d
        val trace = ArrayList<IntArray>()

        for (d in 0..limit) {
            trace.add(v.copyOfRange(offset - d - 1, offset + d + 2))
            var k = -d
            while (k <= d) {
                var x = if (k == -d || (k != d && v[offset + k - 1] < v[offset + k + 1])) {
                    v[offset + k + 1]
                } else {
                    v[offset + k - 1] + 1
                }
                var y = x - k
                while (x < n && y < m && a[x] == b[y]) {
                    x++
                    y++
                }
                v[offset + k] = x
                if (x >= n && y >= m) {
                    return backtrack(trace, n, m)
                }
                k += 2
            }
        }
        return null
    }

    private fun backtrack(trace: List<IntArray>, n: Int, m: Int): List<Hunk> {
        // edits from the end to the start: true = changed position, collected as ranges
        val hunks = ArrayList<Hunk>()
        var x = n
        var y = m

        // current hunk, built backwards
        var hunkAEnd = -1
        var hunkBEnd = -1

        fun closeHunk(aStart: Int, bStart: Int) {
            if (hunkAEnd >= 0) {
                hunks.add(Hunk(aStart, hunkAEnd, bStart, hunkBEnd))
                hunkAEnd = -1
                hunkBEnd = -1
            }
        }

        for (d in trace.size - 1 downTo 0) {
            val row = trace[d]
            // row index of k is k + d + 1
            fun vAt(k: Int) = row[k + d + 1]

            val k = x - y
            val prevK = if (k == -d || (k != d && vAt(k - 1) < vAt(k + 1))) k + 1 else k - 1
            val prevX = if (d == 0) 0 else vAt(prevK)
            val prevY = prevX - prevK

            // diagonal: equal elements
            if (x > prevX && y > prevY) {
                val snake = minOf(x - prevX, y - prevY)
                closeHunk(x, y)
                x -= snake
                y -= snake
            }

            if (d > 0) {
                // a single insertion or deletion from (prevX, prevY) to (x, y)
                if (hunkAEnd < 0) {
                    hunkAEnd = x
                    hunkBEnd = y
                }
                x = prevX
                y = prevY
            }
        }
        closeHunk(x, y)
        hunks.reverse()
        return hunks
    }
}

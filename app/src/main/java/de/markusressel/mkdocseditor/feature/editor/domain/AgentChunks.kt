package de.markusressel.mkdocseditor.feature.editor.domain

import de.markusressel.mkdocsrestclient.sync.text.TextSplice

/** text that an agent inserted, to be revealed as if typed, at a UTF-16 position of the text after the change */
internal data class AgentChunk(val clientId: String, val position: Int, val text: String)

/**
 * A remote change, split into what shows at once and what agents typed. Applying [splices] and then inserting [chunks]
 * in their order gives the text after the change.
 *
 * @param splices the change without the text of the agents, each one refers to the text after the one before it
 * @param chunks the text of the agents, in the order of the text
 */
internal data class SplitChange(val splices: List<TextSplice>, val chunks: List<AgentChunk>)

/**
 * Takes the text that agents inserted out of a remote change, so it can be revealed instead of shown at once.
 *
 * The splices of a sync can hold the change of an agent together with changes of people, and each splice refers to
 * the text after the one before it. So the text of the agents is marked in the text after the change, the marks are
 * taken back through the splices to find the characters of each insert they cover, and the splices are applied
 * again without them.
 *
 * @param text the text after the change
 * @param splices the change
 * @param inserted what each agent inserted, by client id, as ranges of [text]
 * @return the change unchanged, without chunks, if the splices do not lead to [text]
 */
internal fun splitAgentText(text: String, splices: List<TextSplice>, inserted: Map<String, List<IntRange>>): SplitChange {
    val unchanged = SplitChange(splices, emptyList())
    val agents = inserted.keys.toList()
    if (agents.isEmpty()) return unchanged

    // the index of the agent that wrote each character of the text after the change, NONE for everything else
    val after = IntArray(text.length) { NONE }
    agents.forEachIndexed { agent, clientId ->
        inserted.getValue(clientId).forEach { range ->
            for (i in range.first.coerceAtLeast(0)..range.last.coerceAtMost(text.length - 1)) after[i] = agent
        }
    }

    // back through the splices: which characters of each insert are the agents'
    var marks = after
    val insertMarks = arrayOfNulls<IntArray>(splices.size)
    for (i in splices.indices.reversed()) {
        val splice = splices[i]
        val end = splice.index + splice.insert.length
        if (splice.index < 0 || end > marks.size) return unchanged
        insertMarks[i] = marks.copyOfRange(splice.index, end)
        marks = marks.copyOfRange(0, splice.index) + IntArray(splice.deleteCount) { NONE } +
            marks.copyOfRange(end, marks.size)
    }

    // text of an agent that no splice inserted was there before: the ranges do not fit the change
    if (marks.any { it != NONE }) return unchanged

    // forward again without them
    var current = marks
    val stripped = ArrayList<TextSplice>(splices.size)
    splices.forEachIndexed { i, splice ->
        val mine = insertMarks[i]!!
        if (splice.index + splice.deleteCount > current.size) return unchanged
        val skipped = (0 until splice.index).count { current[it] != NONE }
        val deleted = (splice.index until splice.index + splice.deleteCount).count { current[it] == NONE }
        val insert = buildString { splice.insert.forEachIndexed { j, c -> if (mine[j] == NONE) append(c) } }
        // a splice of nothing but agent text would still touch the hidden text next to it
        if (deleted > 0 || insert.isNotEmpty()) stripped += TextSplice(splice.index - skipped, deleted, insert)
        current = current.copyOfRange(0, splice.index) + mine + current.copyOfRange(splice.index + splice.deleteCount, current.size)
    }

    val chunks = ArrayList<AgentChunk>()
    var start = 0
    while (start < after.size) {
        val agent = after[start]
        var end = start + 1
        while (end < after.size && after[end] == agent) end++
        if (agent != NONE) chunks += AgentChunk(agents[agent], start, text.substring(start, end))
        start = end
    }
    return SplitChange(stripped, chunks)
}

private const val NONE = -1

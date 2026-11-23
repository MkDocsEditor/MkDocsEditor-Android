package de.markusressel.mkdocseditor.feature.editor.domain

import kotlin.math.max

/**
 * The text that agents inserted with live changes and that is still hidden, to be typed out piece by piece. The text
 * is in the editor at once; only its drawing waits. The rules are those of "Client animation" in
 * docs/agent-presence.md of the backend, the same as in the web client.
 *
 * Positions are UTF-16 offsets of the editor text. Not thread safe: use it from the main thread.
 */
internal class AgentReveals {

    /** a piece of text that just became visible */
    data class Revealed(val start: Int, val end: Int, val tag: String)

    private class Reveal(
        val clientId: String,
        val instanceId: String,
        /** hidden ranges as [start, end), in the order of the text */
        val hidden: MutableList<IntArray>,
        /** UTF-16 units per millisecond */
        val speed: Double,
    ) {
        val tag = "agent:$clientId"
        var carry = 0.0
    }

    private val reveals = mutableListOf<Reveal>()

    val isEmpty: Boolean get() = reveals.isEmpty()

    /** the hidden ranges of all reveals */
    val hiddenRanges: List<IntRange> get() = reveals.flatMap { reveal -> reveal.hidden.map { it[0] until it[1] } }

    /**
     * Where each agent's reveal is now, by client id: the start of what is still hidden. Its cursor is drawn there, and
     * a follower follows it, else they would jump to the end while the text is still being typed.
     */
    val heads: Map<String, Int> get() = reveals.associate { it.clientId to it.hidden.first()[0] }

    /**
     * Starts to type out the text of a live change. A running reveal of the same agent finishes first.
     *
     * @param ranges what the change inserted, in the text after it
     * @return false if the text shows at once: too long, or there is nothing
     */
    fun start(clientId: String, instanceId: String, ranges: List<IntRange>): Boolean {
        finish(clientId)
        val hidden = ranges.filter { !it.isEmpty() }.sortedBy { it.first }.map { intArrayOf(it.first, it.last + 1) }
        val length = hidden.sumOf { it[1] - it[0] }
        if (length == 0 || length > MAX_LENGTH) return false
        val speed = max(MIN_SPEED_PER_S, length / MAX_DURATION_S) / 1000.0
        reveals += Reveal(clientId, instanceId, hidden.toMutableList(), speed)
        return true
    }

    /** shows the rest of an agent's text at once */
    fun finish(clientId: String) {
        reveals.removeAll { it.clientId == clientId }
    }

    fun finishAll() = reveals.clear()

    /**
     * Moves the hidden ranges with an edit. An edit inside or at the edge of a hidden range shows that range at once, so
     * nobody types into invisible text.
     *
     * @return true if a range was shown because of it
     */
    fun onEdit(start: Int, removed: Int, inserted: Int): Boolean {
        val end = start + removed
        var shown = false
        reveals.forEach { reveal ->
            shown = reveal.hidden.removeAll { it[0] <= end && start <= it[1] } || shown
            reveal.hidden.forEach { range ->
                if (range[0] >= end) {
                    range[0] += inserted - removed
                    range[1] += inserted - removed
                }
            }
        }
        reveals.removeAll { it.hidden.isEmpty() }
        return shown
    }

    /**
     * A tap at [position]: on the text that is being revealed, or right at its head, it shows the rest of that agent's
     * text at once.
     *
     * @return true if it did
     */
    fun onTap(position: Int): Boolean {
        val tapped = reveals.filter { reveal -> reveal.hidden.any { position in it[0]..it[1] } }
        tapped.forEach { finish(it.clientId) }
        return tapped.isNotEmpty()
    }

    /**
     * Moves each reveal on by the time that passed. Fractions of a character carry over, and a character is never cut
     * in half.
     *
     * @param text the text of the editor
     * @return what became visible
     */
    fun step(elapsedMs: Long, text: String): List<Revealed> {
        val revealed = mutableListOf<Revealed>()
        reveals.forEach { reveal ->
            reveal.carry += reveal.speed * elapsedMs
            var count = reveal.carry.toInt()
            reveal.carry -= count
            while (count > 0 && reveal.hidden.isNotEmpty()) {
                val range = reveal.hidden.first()
                var until = minOf(range[1], range[0] + count)
                if (until < range[1] && until in 1 until text.length && text[until - 1].isHighSurrogate()) until++
                count -= until - range[0]
                revealed += Revealed(range[0], until, reveal.tag)
                range[0] = until
                if (range[0] >= range[1]) reveal.hidden.removeAt(0)
            }
        }
        reveals.removeAll { it.hidden.isEmpty() }
        return revealed
    }

    companion object {
        /** a longer change shows at once */
        const val MAX_LENGTH = 2048

        const val MIN_SPEED_PER_S = 60.0

        /** no reveal takes longer */
        const val MAX_DURATION_S = 4.0

        /** how often the reveal moves on */
        const val STEP_MS = 30L
    }
}

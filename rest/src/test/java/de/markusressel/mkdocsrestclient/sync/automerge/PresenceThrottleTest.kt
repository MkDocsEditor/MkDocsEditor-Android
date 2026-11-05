package de.markusressel.mkdocsrestclient.sync.automerge

import kotlin.test.Test
import kotlin.test.assertEquals

/** a cursor and a viewport as sent, null for one that did not change */
private typealias Sent = Pair<Pair<Int?, Int?>?, Pair<Int?, Int?>?>

class PresenceThrottleTest {

    private var time = 0L
    private val posted = mutableMapOf<Runnable, Long>()
    private val sent = mutableListOf<Sent>()

    private val throttle = PresenceThrottle(
        post = { runnable, delayMs -> posted[runnable] = time + delayMs },
        cancel = { posted.remove(it) },
        isPosted = { it in posted },
        send = { selection, viewport -> sent += selection to viewport },
    )

    private fun advance(ms: Long) {
        time += ms
        while (true) {
            val (runnable, _) = posted.entries.filter { it.value <= time }.minByOrNull { it.value }?.toPair() ?: return
            posted.remove(runnable)
            runnable.run()
        }
    }

    @Test
    fun `a cursor goes out with the newest viewport, not the one from before`() {
        throttle.onViewport(0, 10)
        advance(500)
        throttle.onViewport(100, 110)
        throttle.onSelection(105, 105)
        advance(100)

        assertEquals(listOf<Sent>(null to (0 to 10), (105 to 105) to (100 to 110)), sent)
    }

    @Test
    fun `a cursor takes along a viewport that was waiting, which then is not sent again`() {
        throttle.onViewport(0, 10)
        advance(50)
        throttle.onSelection(5, 5)
        advance(1000)

        assertEquals(listOf<Sent>((5 to 5) to (0 to 10)), sent)
    }

    @Test
    fun `scrolling sends the viewport at most every 500 ms, the newest one`() {
        (0 until 10).forEach {
            throttle.onViewport(it, it + 10)
            advance(100)
        }

        assertEquals(listOf<Sent>(null to (4 to 14), null to (9 to 19)), sent)
    }

    @Test
    fun `cursor moves go out after 100 ms, the newest one`() {
        throttle.onSelection(1, 1)
        throttle.onSelection(2, 2)
        advance(100)

        assertEquals(listOf<Sent>((2 to 2) to null), sent)
    }
}

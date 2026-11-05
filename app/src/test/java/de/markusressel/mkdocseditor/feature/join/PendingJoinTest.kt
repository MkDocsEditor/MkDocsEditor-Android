package de.markusressel.mkdocseditor.feature.join

import de.markusressel.mkdocseditor.auth.token.KeyValueStore
import de.markusressel.mkdocseditor.feature.join.data.JoinTarget
import de.markusressel.mkdocseditor.feature.join.data.PendingJoin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PendingJoinTest {

    private val values = mutableMapOf<String, String>()
    private val storage = object : KeyValueStore {
        override fun get(key: String) = values[key]
        override fun put(key: String, value: String) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
    }
    private val target = JoinTarget(1, "doc", "sender")

    @Test
    fun `opens after signing in, also after the process was gone`() {
        PendingJoin(storage).remember(target)

        val restored = PendingJoin(storage)
        assertNull(restored.open.value)
        restored.release(1)

        assertEquals(target, restored.open.value)
    }

    @Test
    fun `signing in to another server does not open it`() {
        val pending = PendingJoin(storage).apply { remember(target) }

        pending.release(2)
        assertNull(pending.open.value)

        pending.release(1)
        assertEquals(target, pending.open.value)
    }

    @Test
    fun `opens only once`() {
        val pending = PendingJoin(storage).apply { remember(target) }
        pending.release(1)
        pending.opened()

        pending.release(1)

        assertNull(pending.open.value)
    }
}

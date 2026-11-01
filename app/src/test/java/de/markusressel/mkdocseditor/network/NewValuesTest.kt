package de.markusressel.mkdocseditor.network

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class NewValuesTest {

    @Test
    fun `the network from the start is not new`() = runTest {
        assertEquals(emptyList(), flowOf("wifi", "wifi").newValues().toList())
    }

    @Test
    fun `a network coming back, or another one, is new`() = runTest {
        assertEquals(listOf("wifi", "mobile"), flowOf("wifi", null, "wifi", "mobile", "mobile").newValues().toList())
    }

    @Test
    fun `the first network after having none is new`() = runTest {
        assertEquals(listOf("wifi"), flowOf(null, "wifi").newValues().toList())
    }
}

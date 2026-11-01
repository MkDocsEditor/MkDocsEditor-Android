package de.markusressel.mkdocseditor.service

import de.markusressel.mkdocseditor.service.UploadUnsyncedChanges.Outcome
import de.markusressel.mkdocseditor.service.UploadUnsyncedChanges.Result
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class UploadUnsyncedChangesTest {

    private val pending = linkedSetOf<String>()
    private val origins = mutableMapOf<String, String?>()
    private val uploaded = mutableListOf<String>()
    private var moves = 0
    private var origin: String? = "server"
    private var outcome: (String) -> Outcome = { Outcome.Done }

    private fun documents(vararg ids: String) {
        ids.forEach {
            pending += it
            origins[it] = "server"
        }
    }

    private val upload = UploadUnsyncedChanges(
        pending = { pending.toSet() },
        followMoves = { moves++ },
        currentOrigin = { origin },
        originOf = { origins[it] },
        upload = {
            uploaded += it
            outcome(it)
        },
    )

    @Test
    fun `all documents are uploaded, after following moves`() = runTest {
        documents("a", "b")
        assertEquals(Result.Finished, upload())
        assertEquals(listOf("a", "b"), uploaded)
        assertEquals(1, moves)
    }

    @Test
    fun `a copy of another server, or of an unknown one, stays`() = runTest {
        documents("mine", "other", "old")
        origins["other"] = "another server"
        origins["old"] = null
        assertEquals(Result.Finished, upload())
        assertEquals(listOf("mine"), uploaded)
    }

    @Test
    fun `an unreachable server ends the run, for a later try`() = runTest {
        documents("a", "b")
        outcome = { Outcome.Unreachable }
        assertEquals(Result.TryAgainLater, upload())
        assertEquals(listOf("a"), uploaded)
    }

    @Test
    fun `without a session the run ends, signing in starts it again`() = runTest {
        documents("a", "b")
        outcome = { Outcome.SignedOut }
        assertEquals(Result.Finished, upload())
        assertEquals(listOf("a"), uploaded)
    }

    @Test
    fun `a refused copy does not loop, and changes made meanwhile are taken along`() = runTest {
        documents("refused")
        outcome = { id ->
            if (id == "refused") documents("new")
            Outcome.Done
        }
        assertEquals(Result.Finished, upload())
        assertEquals(listOf("refused", "new"), uploaded)
    }

    @Test
    fun `without a backend nothing happens`() = runTest {
        documents("a")
        origin = null
        assertEquals(Result.Finished, upload())
        assertEquals(emptyList(), uploaded)
    }
}

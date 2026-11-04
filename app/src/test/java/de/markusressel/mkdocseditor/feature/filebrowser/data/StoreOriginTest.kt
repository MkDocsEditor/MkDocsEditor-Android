package de.markusressel.mkdocseditor.feature.filebrowser.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.test.runTest
import org.mobilenativefoundation.store.store5.Fetcher
import org.mobilenativefoundation.store.store5.SourceOfTruth
import org.mobilenativefoundation.store.store5.StoreBuilder
import org.mobilenativefoundation.store.store5.StoreReadRequest
import org.mobilenativefoundation.store.store5.StoreReadResponse
import org.mobilenativefoundation.store.store5.StoreReadResponseOrigin
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The sync status counts a load as an answer from the backend by its origin, so this checks that a store with a source
 * of truth really reports it that way.
 */
class StoreOriginTest {

    @Test
    fun `a fresh load that succeeds is reported as coming from the fetcher`() = runTest {
        val disk = MutableStateFlow<String?>("stored")
        val store = StoreBuilder.from<String, String, String>(
            fetcher = Fetcher.of { "fetched" },
            sourceOfTruth = SourceOfTruth.of(
                reader = { disk },
                writer = { _, value -> disk.value = value },
            ),
        ).build()

        val responses = store.stream(StoreReadRequest.fresh("key")).transformWhile { response ->
            emit(response)
            !(response is StoreReadResponse.Data && response.value == "fetched")
        }.toList()

        val last = responses.last() as StoreReadResponse.Data
        assertTrue(last.origin is StoreReadResponseOrigin.Fetcher, "$responses")
    }
}

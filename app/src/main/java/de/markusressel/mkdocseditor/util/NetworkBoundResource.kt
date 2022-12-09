package de.markusressel.mkdocseditor.util

import kotlinx.coroutines.flow.*

class NetworkBoundResource<ResultType, RequestType>(
    val query: () -> Flow<ResultType>,
    val fetch: suspend () -> RequestType,
    val saveFetchResult: suspend (RequestType) -> Unit,
    val shouldFetch: (ResultType) -> Boolean = { true }
) {

    private val queryFlow = query()
    val state = MutableStateFlow<Resource<ResultType>?>(null)

    private suspend fun fetch(shouldFetch: Boolean) {
        val data = queryFlow.first()

        val flow = if (shouldFetch) {
            state.emit(Resource.Loading(data))

            try {
                saveFetchResult(fetch())
                queryFlow.map { Resource.Success(it) }
            } catch (throwable: Throwable) {
                queryFlow.map { Resource.Error(throwable, it) }
            }
        } else {
            queryFlow.map { Resource.Success(it) }
        }

        state.emitAll(flow)
    }

    /**
     * Force an immediate fetch
     */
    suspend fun fetchNow() {
        fetch(true)
    }

}

inline fun <ResultType, RequestType> networkBoundResource(
    crossinline query: () -> Flow<ResultType>,
    crossinline fetch: suspend () -> RequestType,
    crossinline saveFetchResult: suspend (RequestType) -> Unit,
    crossinline shouldFetch: (ResultType) -> Boolean = { true }
) = flow {
    val data = query().first()

    val flow = if (shouldFetch(data)) {
        emit(Resource.Loading(data))

        try {
            saveFetchResult(fetch())
            query().map { Resource.Success(it) }
        } catch (throwable: Throwable) {
            query().map { Resource.Error(throwable, it) }
        }
    } else {
        query().map { Resource.Success(it) }
    }

    emitAll(flow)
}
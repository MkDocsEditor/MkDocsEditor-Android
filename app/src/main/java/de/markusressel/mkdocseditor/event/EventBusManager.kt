package de.markusressel.mkdocseditor.event

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventBusManager @Inject constructor() {

    val eventBus = MutableSharedFlow<BusEvent>()

    inline fun <reified T : BusEvent> observe(): Flow<T> {
        return eventBus.filterIsInstance<T>()
    }

    fun send(event: BusEvent) {
        MainScope().launch {
            eventBus.emit(event)
        }
    }

    fun unregister(obs: Flow<BusEvent.FilePickerResult>) {
        TODO("Not yet implemented")
    }
}

fun <T> Flow<T>.subscribe(scope: CoroutineScope = MainScope(), consumer: FlowCollector<T>): Job {
    return scope.launch {
        collect(consumer)
    }
}
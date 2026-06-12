package com.pulse.android.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

internal class InteractionEventQueue(
    defaultDispatcher: CoroutineDispatcher,
) : CoroutineScope by CoroutineScope(SupervisorJob() + defaultDispatcher) {
    private val serialEventDispatcher =
        defaultDispatcher.limitedParallelism(1, "Pulse Interaction event dispatchers")
    private val serialMarkerEventDispatcher =
        defaultDispatcher.limitedParallelism(1, "Pulse Interaction marker event dispatchers")
    private val mutableLocalEventsFlow = MutableSharedFlow<InteractionLocalEvent>()
    val localEventsFlow: SharedFlow<InteractionLocalEvent>
        get() = mutableLocalEventsFlow.asSharedFlow()

    private val mutableLocalMarkerEventsFlow = MutableSharedFlow<InteractionLocalEvent>()
    val localMarkerEventsFlow: SharedFlow<InteractionLocalEvent>
        get() = mutableLocalMarkerEventsFlow.asSharedFlow()

    fun addEvent(event: InteractionLocalEvent) {
        launch(serialEventDispatcher) { mutableLocalEventsFlow.emit(event) }
    }

    fun addMarkerEvent(event: InteractionLocalEvent) {
        launch(serialMarkerEventDispatcher) { mutableLocalMarkerEventsFlow.emit(event) }
    }
}

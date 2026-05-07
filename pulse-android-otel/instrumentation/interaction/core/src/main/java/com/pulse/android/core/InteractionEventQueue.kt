package com.pulse.android.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

internal class InteractionEventQueue(
    defaultDispatcher: CoroutineDispatcher,
) : CoroutineScope by CoroutineScope(SupervisorJob() + defaultDispatcher) {
    // Avoid limitedParallelism(1): Kotlin targets the (Int, String) JVM overload, missing on older
    // kotlinx-coroutines at runtime (e.g. RN apps pinning coroutines for Kotlin 1.9).
    private val serialEventDispatcher =
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "pulse-interaction-events").apply { isDaemon = true }
        }.asCoroutineDispatcher()
    private val serialMarkerEventDispatcher =
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "pulse-interaction-marker-events").apply { isDaemon = true }
        }.asCoroutineDispatcher()
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

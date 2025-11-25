package com.pulse.android.core

import com.pulse.android.remote.InteractionApiService
import com.pulse.android.remote.InteractionRetrofitClient
import com.pulse.android.remote.models.InteractionConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

public class InteractionManager internal constructor(
    private val interactionApiService: InteractionApiService = InteractionRetrofitClient(
        "https://www.google.com/",
    ).apiService,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : CoroutineScope by CoroutineScope(SupervisorJob() + defaultDispatcher) {
    private var interactionConfigs: List<InteractionConfig>? = null
    internal var interactionTrackers: List<InteractionEventsTracker>? = null
    private val eventQueue: InteractionEventQueue = InteractionEventQueue(defaultDispatcher)

    private val interactionTrackerStatesMutableState: MutableStateFlow<List<InteractionRunningStatus>> =
        MutableStateFlow(emptyList())
    public val interactionTrackerStatesState: StateFlow<List<InteractionRunningStatus>>
        get() = interactionTrackerStatesMutableState.asStateFlow()

    public fun init(): Job {
        return launch(ioDispatcher) {
            val interactionConfigs = interactionConfigs ?: runCatching {
                 interactionApiService.getInteractions()
            }.onFailure {
                return@launch
            }.getOrNull() ?: return@launch
            interactionTrackers = interactionConfigs
                .map { interactionConfig ->
                    InteractionEventsTracker(interactionConfig)
                }
            interactionTrackers
                .orEmpty()
                .map { interactionEventsTracker ->
                    launch(defaultDispatcher + CoroutineName("interactionTracker=${interactionEventsTracker.name}")) {
                        eventQueue.localEventsFlow.collect {
                            logDebug { "calling checkAndAdd with ${it.name}" }
                            interactionEventsTracker.checkAndAdd(it, this)
                        }
                    }
                    launch(defaultDispatcher + CoroutineName("interactionMarkerTracker=${interactionEventsTracker.name}")) {
                        eventQueue.localMarkerEventsFlow.collect {
                            logDebug { "calling checkAndAdd with ${it.name}" }
                            interactionEventsTracker.addMarker(it)
                        }
                    }
                }
            launch(defaultDispatcher) {
                combine(
                    interactionTrackers
                        .orEmpty()
                        .map {
                            it.interactionRunningStatusState
                        }
                ) {
                    it.toList()
                }.collect {
                    interactionTrackerStatesMutableState.value = it
                }

            }
        }
    }

    /**
     * Add event to track for interaction as per [InteractionConfig]
     */
    @JvmOverloads
    public fun addEvent(
        eventName: String,
        params: Map<String, Any?> = emptyMap(),
        eventTimeInNano: Long = System.currentTimeMillis() * 1_000_000
    ) {
        val event = InteractionLocalEvent(
            name = eventName,
            timeInNano = eventTimeInNano,
            props = params.mapValues { it.value.toString() }
        )
        eventQueue.addEvent(event)
    }

    /**
     * Add event which will be marked in the timeline of the interaction. These will not contribute
     * to the interaction matching logic. Also see [addEvent]
     */
    @JvmOverloads
    public fun addMarkerEvent(
        eventName: String,
        params: Map<String, Any?> = emptyMap(),
        eventTimeInNano: Long = System.currentTimeMillis() * 1_000_000
    ) {
        val event = InteractionLocalEvent(
            name = eventName,
            timeInNano = eventTimeInNano,
            props = params.mapValues { it.value.toString() }
        )
        eventQueue.addMarkerEvent(event)
    }

    public companion object Companion {
        @JvmStatic
        public val instance: InteractionManager by lazy {
            InteractionManager()
        }
    }

    public fun interface OnInteractionCreatedListener {
        public fun onCreated(interactions: List<Interaction>)
    }
}
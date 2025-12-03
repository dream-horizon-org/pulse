package com.pulse.android.core

import com.pulse.android.core.config.InteractionConfigFetcher
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

public class InteractionManager
    @JvmOverloads
    constructor(
        private val interactionFetcher: InteractionConfigFetcher,
        private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
        private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.Default) {
        private var interactionConfigs: List<InteractionConfig>? = null
        internal var interactionTrackers: List<InteractionEventsTracker>? = null
        private val eventQueue: InteractionEventQueue = InteractionEventQueue(defaultDispatcher)

        private val interactionTrackerStatesMutableState: MutableStateFlow<List<InteractionRunningStatus>> =
            MutableStateFlow(emptyList())
        public val interactionTrackerStatesState: StateFlow<List<InteractionRunningStatus>>
            get() = interactionTrackerStatesMutableState.asStateFlow()

        public fun init(): Job {
            return launch(ioDispatcher) {
                logDebug { "[InteractionManager] Initializing with endpoint: $interactionFetcher" }

                val interactionConfigs =
                    interactionConfigs ?: runCatching {
                        interactionFetcher.getConfigs()
                    }.onFailure { error ->
                        logDebug { "[InteractionManager] Failed to fetch interactions: ${error.message}" }
                        return@launch
                    }.getOrNull() ?: run {
                        logDebug { "[InteractionManager] No interaction configs received" }
                        return@launch
                    }

                logDebug { "[InteractionManager] Loaded ${interactionConfigs.size} interaction(s)" }

                interactionTrackers =
                    interactionConfigs
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
                                logDebug { "calling addMarker with ${it.name}" }
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
                            },
                    ) {
                        it.toList()
                    }.collect {
                        interactionTrackerStatesMutableState.value = it
                    }
                }

                logDebug { "[InteractionManager] Initialization complete" }
            }
        }

        /**
         * Add event to track for interaction as per [InteractionConfig]
         */
        @JvmOverloads
        public fun addEvent(
            eventName: String,
            params: Map<String, Any?> = emptyMap(),
            eventTimeInNano: Long = System.currentTimeMillis() * 1_000_000,
        ) {
            val event =
                InteractionLocalEvent(
                    name = eventName,
                    timeInNano = eventTimeInNano,
                    props = params.mapValues { it.value.toString() },
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
            eventTimeInNano: Long = System.currentTimeMillis() * 1_000_000,
        ) {
            val event =
                InteractionLocalEvent(
                    name = eventName,
                    timeInNano = eventTimeInNano,
                    props = params.mapValues { it.value.toString() },
                )
            eventQueue.addMarkerEvent(event)
        }
    }

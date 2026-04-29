package com.pulse.android.core

import android.os.SystemClock
import com.pulse.android.core.config.InteractionConfigFetcher
import com.pulse.android.remote.models.InteractionConfig
import com.pulse.utils.PulseLogger
import com.pulse.utils.PulseNetworkingUtils
import com.pulse.utils.RedactionUtils
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
    ) : CoroutineScope by CoroutineScope(SupervisorJob() + defaultDispatcher) {
        private val interactionConfigs: List<InteractionConfig>? = null
        internal var interactionTrackers: List<InteractionEventsTracker>? = null
        private val eventQueue: InteractionEventQueue = InteractionEventQueue(defaultDispatcher)

        private val interactionTrackerStatesMutableState: MutableStateFlow<List<InteractionRunningStatus>> =
            MutableStateFlow(emptyList())
        public val interactionTrackerStatesState: StateFlow<List<InteractionRunningStatus>>
            get() = interactionTrackerStatesMutableState.asStateFlow()

        // catching exception using `currentCoroutineContext().ensureActive()`
        @Suppress("SuspendFunSwallowedCancellation")
        public fun init(): Job {
            return launch(ioDispatcher) {
                logVerbose { "Initializing with endpoint: $interactionFetcher" }

                val t0 = SystemClock.elapsedRealtime()
                val interactionConfigs =
                    interactionConfigs ?: PulseNetworkingUtils
                        .runNetworkCatching(
                            tag = InteractionConstant.LOG_TAG,
                            url = "",
                            okHttpClient = null,
                            removeCacheInFailure = false,
                        ) {
                            interactionFetcher.getConfigs()
                        }.onFailure { error ->
                            currentCoroutineContext().ensureActive()
                            val elapsed = SystemClock.elapsedRealtime() - t0
                            val err = RedactionUtils.classifyError(error)
                            PulseLogger.logWarn(InteractionConstant.LOG_TAG, error) {
                                "sdk.interaction.config_fetch success=false duration_ms=$elapsed error_class=$err"
                            }
                            logVerbose { "Failed to fetch interactions: ${error.message ?: "no-msg"}" }
                            return@launch
                        }.getOrNull() ?: run {
                        val elapsed = SystemClock.elapsedRealtime() - t0
                        PulseLogger.logWarn(InteractionConstant.LOG_TAG) {
                            "sdk.interaction.config_fetch success=false duration_ms=$elapsed error_class=no_data"
                        }
                        logVerbose { "No interaction configs received" }
                        return@launch
                    }

                val elapsed = SystemClock.elapsedRealtime() - t0
                PulseLogger.logInfo(InteractionConstant.LOG_TAG) {
                    "sdk.interaction.config_fetch success=true duration_ms=$elapsed interactions_count=${interactionConfigs.size}"
                }
                logVerbose { "Loaded ${interactionConfigs.size} interaction(s)" }

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
                                logVerbose { "calling checkAndAdd with ${it.name}" }
                                interactionEventsTracker.checkAndAdd(it, this)
                            }
                        }
                        launch(defaultDispatcher + CoroutineName("interactionMarkerTracker=${interactionEventsTracker.name}")) {
                            eventQueue.localMarkerEventsFlow.collect {
                                logVerbose { "calling addMarker with ${it.name}" }
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
                        interactionTrackerStatesMutableState.value = it.flatten()
                    }
                }

                logVerbose { "Initialization complete" }
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
                    props = params.mapValues { it.value?.toString().orEmpty() },
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
                    // todo add convention for null
                    props = params.mapValues { it.value?.toString().orEmpty() },
                )
            eventQueue.addMarkerEvent(event)
        }
    }

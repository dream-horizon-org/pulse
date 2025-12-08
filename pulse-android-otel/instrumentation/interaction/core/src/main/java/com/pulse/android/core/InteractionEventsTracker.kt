package com.pulse.android.core

import com.pulse.android.core.InteractionUtil.matches
import com.pulse.android.core.InteractionUtil.matchesAny
import com.pulse.android.remote.models.InteractionConfig
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import java.util.UUID

internal class InteractionEventsTracker(
    private val interactionConfig: InteractionConfig,
) {
    private val interactionRunningStatusMutableState =
        MutableStateFlow<InteractionRunningStatus>(InteractionRunningStatus.NoOngoingMatch(null))
    val interactionRunningStatusState: StateFlow<InteractionRunningStatus>
        get() = interactionRunningStatusMutableState.asStateFlow()

    private val localMarkers: ArrayList<InteractionLocalEvent> = ArrayList()

    private var isInteractionClosed: Boolean? = null

    val name: String = interactionConfig.name
    private var timerJob: Job? = null

    @Suppress("LongMethod")
    fun checkAndAdd(
        event: InteractionLocalEvent,
        timerScope: CoroutineScope,
    ) {
        val newValue =
            if (
                event matchesAny interactionConfig.events ||
                event matchesAny interactionConfig.globalBlacklistedEvents
            ) {
                logDebug { "match for event = ${event.name}, timeInNano = ${event.timeInNano}" }
                localEvents.add(event)
                val (shouldTakeFirstEvent, shouldResetList, interactionStatus) =
                    InteractionUtil
                        .matchSequence(
                            if (isInteractionClosed == true) {
                                isInteractionClosed = null
                                UUID.randomUUID().toString()
                            } else {
                                (interactionRunningStatusState.value as? InteractionRunningStatus.OngoingMatch)?.interactionId
                                    ?: UUID.randomUUID().toString()
                            },
                            localEvents,
                            localMarkers,
                            interactionConfig,
                        ).also {
                            logDebug { "matchSeq result = ${it ?: "null"}" }
                        } ?: return
                val newInteractionStatus =
                    if (shouldResetList) {
                        logDebug { "resetList called with shouldTakeFirstEvent = $shouldTakeFirstEvent" }
                        if (
                            shouldTakeFirstEvent && localEvents.last() matches interactionConfig.firstEvent
                        ) {
                            val lastEvent = localEvents.last()
                            interactionStatus as? InteractionRunningStatus.OngoingMatch
                                ?: error("This should be ongoing match")
                            assert(interactionStatus.interaction == null || interactionStatus.interaction.isErrored) {
                                "interaction should be null or errored out"
                            }
                            // setting the null to populate the error
                            interactionRunningStatusMutableState.value =
                                interactionStatus.createErrorInteraction(
                                    interactionStatus.interactionId,
                                    interactionConfig,
                                    localEvents,
                                    localMarkers,
                                )
                            localEvents.clear()
                            localEvents.add(lastEvent)

                            interactionStatus.copy(
                                interactionId = UUID.randomUUID().toString(),
                                interaction = null,
                            )
                        } else {
                            isInteractionClosed = true
                            localEvents.clear()
                            interactionStatus
                        }
                    } else {
                        interactionStatus
                    }
                logDebug { "matchSequence newInteractionStatus = $newInteractionStatus" }
                timerScope.launchResetTimer(newInteractionStatus)
                newInteractionStatus
            } else {
                // didn't match with event sequence or gBlacklisted events we should not reset the value
                interactionRunningStatusMutableState.value
            }
        interactionRunningStatusMutableState.value = newValue
    }

    private fun CoroutineScope.launchResetTimer(newValue: InteractionRunningStatus) {
        timerJob?.cancel()
        logDebug { "launchResetTimer newValue = $newValue" }
        if (newValue is InteractionRunningStatus.OngoingMatch && newValue.interaction == null) {
            timerJob =
                launch(CoroutineName("timer#${newValue.index}")) {
                    val timeOfDelay = interactionConfig.thresholdInMs + 10
                    delay(timeOfDelay)
                    isInteractionClosed = true
                    interactionRunningStatusMutableState.value =
                        interactionRunningStatusMutableState.updateAndGet {
                            if (it is InteractionRunningStatus.OngoingMatch && it.interaction == null) {
                                it.createErrorInteraction(
                                    it.interactionId,
                                    interactionConfig,
                                    localEvents,
                                    localMarkers,
                                )
                            } else {
                                interactionRunningStatusMutableState.value
                            }
                        }
                    localEvents.clear()
                }
        } else {
            timerJob?.cancel()
        }
    }

    private fun InteractionRunningStatus.OngoingMatch.createErrorInteraction(
        interactionId: String,
        interactionConfig: InteractionConfig,
        localEvents: List<InteractionLocalEvent>,
        localMarkers: List<InteractionLocalEvent>,
    ): InteractionRunningStatus.OngoingMatch =
        this.copy(
            interaction =
                InteractionUtil.buildPulseInteraction(
                    interactionId,
                    interactionConfig,
                    localEvents,
                    localMarkers,
                    false,
                ),
        )

    fun addMarker(event: InteractionLocalEvent) {
        localMarkers += event
    }

    private val localEvents: SortedList<InteractionLocalEvent> =
        SortedList { e1, e2 -> e1.timeInNano.compareTo(e2.timeInNano) }
}

private class SortedList<T>(
    private val comparator: Comparator<in T>,
) : ArrayList<T>() {
    override fun add(element: T): Boolean {
        val index = binarySearch(element, comparator)
        super.add(if (index < 0) -(index + 1) else index, element)
        return true
    }
}

public sealed class InteractionRunningStatus {
    public class NoOngoingMatch internal constructor(
        public val oldOngoingInteractionRunningStatus: InteractionRunningStatus?,
    ) : InteractionRunningStatus()

    public class OngoingMatch internal constructor(
        public val index: Int,
        public val interactionId: String,
        public val interactionConfig: InteractionConfig,
        public val interaction: Interaction?,
    ) : InteractionRunningStatus() {
        internal fun copy(
            index: Int = this.index,
            interactionId: String = this.interactionId,
            interactionConfig: InteractionConfig = this.interactionConfig,
            interaction: Interaction? = this.interaction,
        ): OngoingMatch = OngoingMatch(index, interactionId, interactionConfig, interaction)
    }
}

// todo can be inlined but AnimalSniffer task failing
//  May be related to https://github.com/mojohaus/animal-sniffer/issues/311
public val List<InteractionRunningStatus>.runningIds: List<String>
    get() =
        this
            .filterIsInstance<InteractionRunningStatus.OngoingMatch>()
            .map { it.interactionId }

// todo can be inlined but AnimalSniffer task failing
//  May be related to https://github.com/mojohaus/animal-sniffer/issues/311
public val List<InteractionRunningStatus>.runningNames: List<String>
    get() =
        this
            .filterIsInstance<InteractionRunningStatus.OngoingMatch>()
            .map { it.interactionConfig.name }

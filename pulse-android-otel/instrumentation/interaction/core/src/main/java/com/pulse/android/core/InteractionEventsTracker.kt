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
        MutableStateFlow<List<InteractionRunningStatus>>(listOf(InteractionRunningStatus.NoOngoingMatch(null)))
    val interactionRunningStatusState: StateFlow<List<InteractionRunningStatus>>
        get() = interactionRunningStatusMutableState.asStateFlow()

    private var isInteractionClosed: Boolean = true

    val name: String = interactionConfig.name
    private var timerJob: Job? = null

    @Suppress("LongMethod")
    fun checkAndAdd(
        event: InteractionLocalEvent,
        timerScope: CoroutineScope,
    ) {
        val (oldValue, newValue) =
            if (
                event matchesAny interactionConfig.events ||
                event matchesAny interactionConfig.globalBlacklistedEvents
            ) {
                logDebug { "match for event = ${event.name}, timeInNano = ${event.timeInNano}" }
                localEvents.add(event)
                val (shouldTakeFirstEvent, shouldResetList, interactionStatus) =
                    InteractionUtil
                        .matchSequence(
                            if (isInteractionClosed) {
                                isInteractionClosed = false
                                UUID.randomUUID().toString()
                            } else {
                                (interactionRunningStatusState.value.lastOrNull() as? InteractionRunningStatus.OngoingMatch)?.interactionId
                                    ?: UUID.randomUUID().toString()
                            },
                            localEvents,
                            localMarkers,
                            interactionConfig,
                        ).also { logDebug { "matchSeq result = ${it ?: "null"}" } } ?: run {
                        isInteractionClosed = true
                        return
                    }
                val (oldInteractionStatus, newInteractionStatus) =
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
                            val oldInteractionError =
                                interactionStatus.createErrorInteraction(
                                    interactionStatus.interactionId,
                                    interactionConfig,
                                    localEvents,
                                    localMarkers,
                                    InteractionErrorType.SEQUENCE_VIOLATION,
                                    null,
                                    interactionStatus.sequenceViolationExpectedEventName,
                                    interactionStatus.sequenceViolationReceivedEventName,
                                )
                            clearStates()
                            localEvents.add(lastEvent)

                            oldInteractionError to
                                interactionStatus.copy(
                                    interactionId = UUID.randomUUID().toString(),
                                    interaction = null,
                                    sequenceViolationExpectedEventName = null,
                                    sequenceViolationReceivedEventName = null,
                                )
                        } else {
                            isInteractionClosed = true
                            clearStates()
                            null to interactionStatus
                        }
                    } else {
                        null to interactionStatus
                    }
                logDebug {
                    "matchSequence newInteractionStatus = $newInteractionStatus, oldInteractionStatus = ${oldInteractionStatus ?: "null"}"
                }
                timerScope.launchResetTimer(newInteractionStatus)
                oldInteractionStatus to newInteractionStatus
            } else {
                // didn't match with event sequence or gBlacklisted events we should not reset the value
                null to interactionRunningStatusMutableState.value.last()
            }
        interactionRunningStatusMutableState.value = if (oldValue != null) listOf(oldValue, newValue) else listOf(newValue)
    }

    private fun CoroutineScope.launchResetTimer(newValue: InteractionRunningStatus) {
        timerJob?.cancel()
        logDebug { "launchResetTimer newValue = $newValue" }
        if (newValue is InteractionRunningStatus.OngoingMatch && newValue.interaction == null) {
            timerJob =
                launch(CoroutineName("timer#${newValue.index}")) {
                    val timeOfDelayInMs = interactionConfig.thresholdInMs + 10
                    logDebug { "launchResetTimer before delay = $timeOfDelayInMs" }
                    delay(timeOfDelayInMs)
                    logDebug { "launchResetTimer after delay = $timeOfDelayInMs" }
                    isInteractionClosed = true
                    interactionRunningStatusMutableState.value =
                        interactionRunningStatusMutableState.updateAndGet {
                            val lastValue = it.lastOrNull()
                            if (lastValue is InteractionRunningStatus.OngoingMatch && lastValue.interaction == null) {
                                listOf(
                                    lastValue.createErrorInteraction(
                                        lastValue.interactionId,
                                        interactionConfig,
                                        localEvents,
                                        localMarkers,
                                        InteractionErrorType.TIMEOUT,
                                        interactionConfig.events.getOrNull(lastValue.index + 1)?.name,
                                    ),
                                )
                            } else {
                                interactionRunningStatusMutableState.value
                            }
                        }
                    clearStates()
                }
        }
    }

    private fun clearStates() {
        localEvents.clear()
        localMarkers.clear()
    }

    private fun InteractionRunningStatus.OngoingMatch.createErrorInteraction(
        interactionId: String,
        interactionConfig: InteractionConfig,
        localEvents: List<InteractionLocalEvent>,
        localMarkers: List<InteractionLocalEvent>,
        errorType: InteractionErrorType,
        timeoutExpectedEventName: String? = null,
        sequenceViolationExpectedEventName: String? = null,
        sequenceViolationReceivedEventName: String? = null,
    ): InteractionRunningStatus.OngoingMatch =
        this.copy(
            interaction =
                InteractionUtil.buildPulseInteraction(
                    interactionId,
                    interactionConfig,
                    localEvents,
                    localMarkers,
                    errorType,
                    timeoutExpectedEventName,
                    sequenceViolationExpectedEventName,
                    sequenceViolationReceivedEventName
                ),
        )

    fun addMarker(event: InteractionLocalEvent) {
        localMarkers += event
    }

    private val localMarkers: ArrayList<InteractionLocalEvent> = ArrayList()

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
    ) : InteractionRunningStatus() {
        override fun toString(): String =
            "NoOngoingMatch(oldOngoingInteractionRunningStatus=${oldOngoingInteractionRunningStatus ?: "null"})"
    }

    public class OngoingMatch internal constructor(
        public val index: Int,
        public val interactionId: String,
        public val interactionConfig: InteractionConfig,
        public val interaction: Interaction?,
        internal val sequenceViolationExpectedEventName: String? = null,
        internal val sequenceViolationReceivedEventName: String? = null,
    ) : InteractionRunningStatus() {
        internal fun copy(
            index: Int = this.index,
            interactionId: String = this.interactionId,
            interactionConfig: InteractionConfig = this.interactionConfig,
            interaction: Interaction? = this.interaction,
            sequenceViolationExpectedEventName: String? = this.sequenceViolationExpectedEventName,
            sequenceViolationReceivedEventName: String? = this.sequenceViolationReceivedEventName,
        ): OngoingMatch =
            OngoingMatch(
                index,
                interactionId,
                interactionConfig,
                interaction,
                sequenceViolationExpectedEventName,
                sequenceViolationReceivedEventName,
            )

        override fun toString(): String =
            "OngoingMatch(index=$index, interactionId='$interactionId', " +
                "interactionConfig=$interactionConfig, interaction=${interaction ?: "null"})"
    }
}

private inline val List<InteractionRunningStatus>.runningInteractions: List<InteractionRunningStatus.OngoingMatch>
    get() =
        this
            .filterIsInstance<InteractionRunningStatus.OngoingMatch>()
            .filter { it.interaction == null }

// todo can be inlined but AnimalSniffer task failing
//  May be related to https://github.com/mojohaus/animal-sniffer/issues/311
public val List<InteractionRunningStatus>.runningIds: List<String>
    get() =
        this
            .runningInteractions
            .map { it.interactionId }

// todo can be inlined but AnimalSniffer task failing
//  May be related to https://github.com/mojohaus/animal-sniffer/issues/311
public val List<InteractionRunningStatus>.runningNames: List<String>
    get() =
        this
            .runningInteractions
            .map { it.interactionConfig.name }

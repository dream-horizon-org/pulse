package com.pulse.android.core

import com.pulse.android.remote.models.InteractionAttrsEntry
import com.pulse.android.remote.models.InteractionConfig
import com.pulse.android.remote.models.InteractionEvent
import com.pulse.utils.PulseOtelUtils
import java.util.Locale

internal object InteractionUtil {
    /**
     * Returns null when there is event of interest but because of ordering it didn't create any
     * change in the matching
     * For example global black listed event came but before or after the first or last event
     * respectively
     */
    @Suppress("LongMethod")
    fun matchSequence(
        ongoingMatchInteractionId: String,
        localEvents: List<InteractionLocalEvent>,
        localMarkers: List<InteractionLocalEvent>,
        interactionConfig: InteractionConfig,
    ): MatchResult? {
        val stepWiseTimeInNano: MutableList<InteractionLocalEvent> = mutableListOf()

        var configEventIndex = 0
        var isMatchOnGoing = false

        fun resetMatching() {
            stepWiseTimeInNano.clear()
            configEventIndex = 0
            isMatchOnGoing = false
        }

        var newInteractionStatus: MatchResult? = null
        logDebug { "localEvents = ${localEvents.joinToString { it.name }}" }
        var localEventIndex = 0
        while (localEventIndex < localEvents.size) {
            val localEvent = localEvents[localEventIndex]

            if (isMatchOnGoing && localEvent matchesAny interactionConfig.globalBlacklistedEvents) {
                logDebug { "blacklisted event(${localEvent.name}) found" }
                return MatchResult(
                    shouldTakeFirstEvent = false,
                    shouldResetList = true,
                    interactionStatus = InteractionRunningStatus.NoOngoingMatch(null),
                    sequenceViolationExpectedEventName = null,
                    sequenceViolationReceivedEventName = null,
                )
            }

            val configEvent = interactionConfig.events[configEventIndex]

            logDebug { "localEvent:${localEvent.name} from localEventIndex = $localEventIndex," }
            val isMatch = localEvent matches configEvent
            newInteractionStatus =
                if (isMatch) {
                    if (configEvent.isBlacklisted) {
                        logDebug { "localEvent:${localEvent.name} is blacklisted" }
                        MatchResult(
                            shouldTakeFirstEvent = false,
                            shouldResetList = true,
                            interactionStatus = InteractionRunningStatus.NoOngoingMatch(null),
                            sequenceViolationExpectedEventName = null,
                            sequenceViolationReceivedEventName = null,
                        )
                    } else {
                        stepWiseTimeInNano.add(localEvent)
                        configEventIndex++
                        logDebug {
                            "localEvent:${localEvent.name} is match and not a blacklisted match, " +
                                "matched at index = ${configEventIndex - 1}, " +
                                "config(w/o blacklisted) = ${interactionConfig.eventsSize}"
                        }

                        if (configEventIndex == interactionConfig.eventsSize) {
                            logDebug { "localEvent:${localEvent.name} is final match" }
                            isMatchOnGoing = false
                            MatchResult(
                                shouldTakeFirstEvent = false,
                                shouldResetList = true,
                                interactionStatus =
                                    InteractionRunningStatus.OngoingMatch(
                                        interactionId = ongoingMatchInteractionId,
                                        interactionConfig = interactionConfig,
                                        index = configEventIndex - 1,
                                        interaction =
                                            buildPulseInteraction(
                                                interactionId = ongoingMatchInteractionId,
                                                interactionConfig = interactionConfig,
                                                events = stepWiseTimeInNano,
                                                localMarkers = localMarkers,
                                                isSuccessInteraction = true,
                                            ),
                                    ),
                                sequenceViolationExpectedEventName = null,
                                sequenceViolationReceivedEventName = null,
                            )
                        } else {
                            isMatchOnGoing = true
                            // ongoing match
                            MatchResult(
                                shouldTakeFirstEvent = false,
                                shouldResetList = false,
                                interactionStatus =
                                    InteractionRunningStatus.OngoingMatch(
                                        index = configEventIndex - 1,
                                        interactionId = ongoingMatchInteractionId,
                                        interactionConfig = interactionConfig,
                                        interaction = null,
                                    ),
                                sequenceViolationExpectedEventName = null,
                                sequenceViolationReceivedEventName = null,
                            )
                        }
                    }
                } else if (configEvent.isBlacklisted) {
                    configEventIndex++
                    continue
                } else if (isMatchOnGoing) {
                    isMatchOnGoing = false
                    MatchResult(
                        shouldTakeFirstEvent = true,
                        shouldResetList = true,
                        interactionStatus =
                            InteractionRunningStatus.OngoingMatch(
                                index = configEventIndex - 1,
                                interactionId = ongoingMatchInteractionId,
                                interactionConfig = interactionConfig,
                                interaction =
                                    buildPulseInteraction(
                                        interactionId = ongoingMatchInteractionId,
                                        interactionConfig = interactionConfig,
                                        events = stepWiseTimeInNano,
                                        localMarkers = localMarkers,
                                        isSuccessInteraction = false,
                                        errorType = InteractionErrorType.SEQUENCE_VIOLATION,
                                        sequenceViolationExpectedEventName = configEvent.name,
                                        sequenceViolationReceivedEventName = localEvent.name,
                                    ),
                            ),
                        sequenceViolationExpectedEventName = configEvent.name,
                        sequenceViolationReceivedEventName = localEvent.name,
                    )
                } else {
                    // no match is ongoing
                    null
                }
            localEventIndex++
        }

        if (newInteractionStatus?.shouldResetList == true) {
            resetMatching()
        }

        return newInteractionStatus
    }

    infix fun InteractionLocalEvent.matches(interactionEvent: InteractionEvent): Boolean {
        if (name != interactionEvent.name) return false
        val propsInteractionConfig = interactionEvent.props ?: return true
        val propsLocalEvent = this.props ?: return false
        return propsInteractionConfig.all { it in propsLocalEvent }
    }

    infix fun InteractionLocalEvent.matchesAny(interactionEvent: Iterable<InteractionEvent>) = interactionEvent.any { this matches it }

    private operator fun Map<String, String>.contains(propInteractionConfig: InteractionAttrsEntry): Boolean {
        val propName = propInteractionConfig.name
        val propValue = propInteractionConfig.value
        val operator = propInteractionConfig.operator

        if (!this.containsKey(propName)) return false

        val actualValue = this[propName]

        return matchPropValue(propValue, operator, actualValue)
    }

    private fun matchPropValue(
        expectedValue: String?,
        operator: String?,
        actualValue: String?,
    ): Boolean {
        if (expectedValue == null || operator == null || actualValue == null) return false
        val actualValueLower = actualValue.lowercase()
        val expectedValueLower = expectedValue.lowercase()

        return when (operator.uppercase(Locale.ROOT)) {
            InteractionConstant.Operators.EQUALS.operatorName -> actualValue == expectedValue
            InteractionConstant.Operators.NOT_EQUALS.operatorName -> actualValue != expectedValue
            InteractionConstant.Operators.CONTAINS.operatorName -> actualValueLower.contains(expectedValueLower)
            InteractionConstant.Operators.NOT_CONTAINS.operatorName -> !actualValueLower.contains(expectedValueLower)
            InteractionConstant.Operators.STARTS_WITH.operatorName -> actualValueLower.startsWith(expectedValueLower)
            InteractionConstant.Operators.ENDS_WITH.operatorName -> actualValueLower.endsWith(expectedValueLower)
            else -> false
        }
    }

    internal fun buildPulseInteraction(
        interactionId: String,
        interactionConfig: InteractionConfig,
        events: List<InteractionLocalEvent>,
        localMarkers: List<InteractionLocalEvent>,
        isSuccessInteraction: Boolean,
        errorType: InteractionErrorType? = null,
        sequenceViolationExpectedEventName: String? = null,
        sequenceViolationReceivedEventName: String? = null,
        timeoutExpectedEventName: String? = null,
    ): Interaction {
        require(events.isNotEmpty()) { "buildPulseInteraction requires at least one event" }
        val interactionName = interactionConfig.name
        val interactionConfigId = interactionConfig.id
        val lastEventTimeInNano = events.last().timeInNano

        if (isSuccessInteraction) {
            require(errorType == null) { "success interaction must not set errorType" }
        } else {
            require(errorType != null) { "error interactions require errorType" }
        }

        val errorMessage: String? =
            if (!isSuccessInteraction && errorType != null) {
                when (errorType) {
                    InteractionErrorType.TIMEOUT ->
                        if (timeoutExpectedEventName != null) {
                            "Timed out while waiting for event \"$timeoutExpectedEventName\"."
                        } else {
                            "Timed out before the next expected event arrived."
                        }
                    InteractionErrorType.SEQUENCE_VIOLATION ->
                        if (sequenceViolationExpectedEventName != null && sequenceViolationReceivedEventName != null) {
                            "Expected event \"$sequenceViolationExpectedEventName\", received \"$sequenceViolationReceivedEventName\"."
                        } else {
                            "An event did not match the next expected event in this interaction."
                        }
                }
            } else {
                null
            }

        val (timeDifferenceInNano, timeCategory, upTimeIndex) =
            if (isSuccessInteraction) {
                val timeDifferenceInNano = lastEventTimeInNano - events.first().timeInNano
                val timeDifferenceInMs = timeDifferenceInNano / 1000_000
                val lowerLimitInMs = interactionConfig.uptimeLowerLimitInMs
                val midLimitInMs = interactionConfig.uptimeMidLimitInMs
                val upperLimitInMs = interactionConfig.uptimeUpperLimitInMs

                val (upTimeIndex, timeCategory) =
                    when {
                        timeDifferenceInMs <= lowerLimitInMs -> {
                            1.0 to InteractionConstant.TimeCategory.EXCELLENT
                        }

                        timeDifferenceInMs <= midLimitInMs -> {
                            getUpTimeIndex(
                                timeDifferenceInMs,
                                lowerLimitInMs,
                                upperLimitInMs,
                            ) to InteractionConstant.TimeCategory.GOOD
                        }

                        timeDifferenceInMs <= upperLimitInMs -> {
                            getUpTimeIndex(
                                timeDifferenceInMs,
                                lowerLimitInMs,
                                upperLimitInMs,
                            ) to InteractionConstant.TimeCategory.AVERAGE
                        }

                        else -> {
                            0.0 to InteractionConstant.TimeCategory.POOR
                        }
                    }

                Triple(timeDifferenceInNano, timeCategory, upTimeIndex)
            } else {
                Triple(null, null, null)
            }
        val timeInMsDiffPair = events.getTimeSpanInNanos(interactionConfig.thresholdInMs)
        val maps =
            mapOf(
                InteractionConstant.NAME to interactionName,
                InteractionConstant.CONFIG_ID to interactionConfigId,
                InteractionConstant.LAST_EVENT_TIME_IN_NANO to lastEventTimeInNano,
                InteractionConstant.LOCAL_EVENTS to events.toList(),
                InteractionConstant.MARKER_EVENTS to (
                    timeInMsDiffPair?.let { localMarkers.getEventsBetween(it.first, it.second) } ?: localMarkers.toList()
                ),
                InteractionConstant.APDEX_SCORE to upTimeIndex,
                InteractionConstant.USER_CATEGORY to timeCategory?.categoryName,
                InteractionConstant.TIME_TO_COMPLETE_IN_NANO to timeDifferenceInNano,
                InteractionConstant.IS_ERROR to !isSuccessInteraction,
            )
        val maps =
            if (!isSuccessInteraction && errorType != null && errorMessage != null) {
                baseMaps +
                    mapOf(
                        InteractionConstant.ERROR_TYPE to errorType.code,
                        InteractionConstant.ERROR_MESSAGE to errorMessage,
                    )
            } else {
                baseMaps
            }

        return Interaction(
            id = interactionId,
            name = interactionName,
            props = maps,
        )
    }

    private fun getUpTimeIndex(
        timeDifferenceInNano: Long,
        lowerLimit: Long,
        upperLimit: Long,
    ): Double = 1.0 - (1.0 * (timeDifferenceInNano - lowerLimit) / (upperLimit - lowerLimit))

    internal data class MatchResult(
        val shouldTakeFirstEvent: Boolean,
        val shouldResetList: Boolean,
        val interactionStatus: InteractionRunningStatus,
        val sequenceViolationExpectedEventName: String?,
        val sequenceViolationReceivedEventName: String?,
    )
}

internal inline fun logDebug(body: () -> String) {
    PulseOtelUtils.logDebug(InteractionConstant.LOG_TAG, body)
}

/**
 * Contains the info about generated interaction
 */
public class Interaction internal constructor(
    public val id: String,
    public val name: String,
    public val props: Map<String, Any?> = emptyMap(),
)

@Suppress("UNCHECKED_CAST")
internal fun Interaction.getTimeSpanInNanos(timeOutInMs: Long): Pair<Long, Long>? = events.getTimeSpanInNanos(timeOutInMs)

internal fun List<InteractionLocalEvent>.getTimeSpanInNanos(timeOutInMs: Long): Pair<Long, Long>? {
    val steps = this
    if (steps.isEmpty()) {
        PulseOtelUtils.logError(
            tag = InteractionConstant.LOG_TAG,
            throwable = IllegalStateException("getTimeSpanInNanos: Events size is 0)"),
        ) {
            "getTimeSpanInNanos: Events size is 0."
        }
        return null
    }
    if (isErrored) {
        val errorTypeParsed = InteractionErrorType.fromCode(props[InteractionConstant.ERROR_TYPE] as? String)
        if (errorTypeParsed == InteractionErrorType.TIMEOUT) {
            val firstNs = steps.first().timeInNano
            val lastNs = steps.last().timeInNano
            val thresholdNs = timeOutInMs * 1_000_000L
            return firstNs to (firstNs + thresholdNs + (lastNs - firstNs))
        }
        return steps.first().timeInNano to steps.last().timeInNano
    }
    if (steps.size == 1) {
        return steps[0].timeInNano to steps[0].timeInNano + timeOutInMs * 1000000
    }
    return steps.first().timeInNano to steps.last().timeInNano
}

internal fun List<InteractionLocalEvent>.getEventsBetween(
    startInNanoInclusive: Long,
    endInNanoInclusive: Long,
): List<InteractionLocalEvent> =
    this.filter {
        it.timeInNano in startInNanoInclusive..endInNanoInclusive
    }

public fun Interaction.getTimeSpanInNanos(interactionStatus: InteractionRunningStatus.OngoingMatch): Pair<Long, Long>? =
    this.getTimeSpanInNanos(timeOutInMs = interactionStatus.interactionConfig.thresholdInMs)

@Suppress("UNCHECKED_CAST")
public val Interaction.events: List<InteractionLocalEvent>
    get() {
        return props[InteractionConstant.LOCAL_EVENTS] as? List<InteractionLocalEvent>
            ?: error("InteractionConstant.LOCAL_EVENTS is missing or not of correct type")
    }

@Suppress("UNCHECKED_CAST")
public val Interaction.markerEvents: List<InteractionLocalEvent>
    get() {
        return props[InteractionConstant.MARKER_EVENTS] as? List<InteractionLocalEvent>
            ?: error("InteractionConstant.MARKER_EVENTS is missing or not of correct type")
    }

public val Interaction.isErrored: Boolean
    get() {
        val isError =
            props[InteractionConstant.IS_ERROR] as? Boolean
                ?: error("InteractionConstant.IS_ERROR is missing or not of correct type")
        return isError
    }

/** [InteractionErrorType.code] under [InteractionConstant.ERROR_TYPE] when [isErrored]. */
public val Interaction.errorTypeCode: String?
    get() = props[InteractionConstant.ERROR_TYPE] as? String

public val Interaction.errorMessage: String?
    get() = props[InteractionConstant.ERROR_MESSAGE] as? String

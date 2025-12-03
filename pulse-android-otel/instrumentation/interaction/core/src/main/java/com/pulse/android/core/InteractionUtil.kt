package com.pulse.android.core

import android.util.Log
import com.pulse.android.remote.models.InteractionAttrsEntry
import com.pulse.android.remote.models.InteractionConfig
import com.pulse.android.remote.models.InteractionEvent
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
                                                // making a copy so that any changes to stepWiseTimeInNano doesn't effect the stored value
                                                events = stepWiseTimeInNano.toList(),
                                                localMarkers = localMarkers.toList(),
                                                isSuccessInteraction = true,
                                            ),
                                    ),
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
                                        // making a copy so that any changes to stepWiseTimeInNano doesn't effect the stored value
                                        events = stepWiseTimeInNano.toList(),
                                        localMarkers = localMarkers.toList(),
                                        isSuccessInteraction = false,
                                    ),
                            ),
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
    ): Interaction {
        val interactionName = interactionConfig.name
        val interactionConfigId = interactionConfig.id
        val lastEventTimeInNano = events.last().timeInNano

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
        val maps =
            mapOf(
                InteractionConstant.NAME to interactionName,
                InteractionConstant.CONFIG_ID to interactionConfigId,
                InteractionConstant.LAST_EVENT_TIME_IN_NANO to lastEventTimeInNano,
                InteractionConstant.LOCAL_EVENTS to events,
                InteractionConstant.MARKER_EVENTS to localMarkers,
                InteractionConstant.APDEX_SCORE to upTimeIndex,
                InteractionConstant.USER_CATEGORY to timeCategory?.categoryName,
                InteractionConstant.TIME_TO_COMPLETE_IN_NANO to timeDifferenceInNano,
                InteractionConstant.IS_ERROR to !isSuccessInteraction,
            )

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
    )
}

internal inline fun logDebug(body: () -> String) {
    Log.d(InteractionConstant.LOG_TAG, body())
}

/**
 * Contains the info about generated interaction
 */
public class Interaction internal constructor(
    public val id: String,
    public val name: String,
    public val props: Map<String, Any?> = mapOf(),
)

@Suppress("UNCHECKED_CAST")
// TODO: Investigate why events list can be empty or have only 1 item, causing IndexOutOfBoundsException
// Crash logs show: "Index 0 out of bounds for length 0" and "Index 1 out of bounds for length 1"
// This safety check prevents crash but we need to understand root cause
public val Interaction.timeSpanInNanos: Pair<Long, Long>?
    get() {
        val steps = events
        if (steps.size < 2) {
            return null
        }
        return steps[0].timeInNano to steps[1].timeInNano
    }

@Suppress("UNCHECKED_CAST")
public val Interaction.events: List<InteractionLocalEvent>
    get() {
        return props[InteractionConstant.LOCAL_EVENTS] as List<InteractionLocalEvent>
    }

@Suppress("UNCHECKED_CAST")
public val Interaction.markerEvents: List<InteractionLocalEvent>
    get() {
        return props[InteractionConstant.MARKER_EVENTS] as List<InteractionLocalEvent>
    }

public val Interaction.isErrored: Boolean
    get() {
        val isError = props[InteractionConstant.IS_ERROR] as Boolean
        return isError
    }

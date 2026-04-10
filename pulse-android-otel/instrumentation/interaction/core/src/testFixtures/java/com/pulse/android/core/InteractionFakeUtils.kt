@file:Suppress("RedundantVisibilityModifier", "unused") // explicit api requires public modifier mentioned

package com.pulse.android.core

import com.pulse.android.remote.models.InteractionConfig

/**
 * Test fakes for [Interaction] and [InteractionRunningStatus.OngoingMatch].
 * Uses the same `createFake*` object + method naming as `InteractionRemoteFakeUtils` in interaction-remote.
 */
public object InteractionFakeUtils {
    public fun createFakeInteraction(
        interactionId: String,
        config: InteractionConfig,
        events: List<InteractionLocalEvent>,
        markers: List<InteractionLocalEvent> = emptyList(),
        isSuccess: Boolean = true,
        errorType: InteractionErrorType? = null,
        sequenceViolationExpectedEventName: String? = null,
        sequenceViolationReceivedEventName: String? = null,
        timeoutExpectedEventName: String? = null,
    ): Interaction {
        require(isSuccess == (errorType == null)) {
            "createFakeInteraction: use isSuccess true with errorType null, or isSuccess false with a non-null errorType"
        }
        return InteractionUtil.buildPulseInteraction(
            interactionId,
            config,
            events,
            markers,
            if (isSuccess) {
                null
            } else {
                InteractionBuildError(
                    type = errorType!!,
                    timeoutExpectedEventName = timeoutExpectedEventName,
                    sequenceViolationExpectedEventName = sequenceViolationExpectedEventName,
                    sequenceViolationReceivedEventName = sequenceViolationReceivedEventName,
                )
            },
        )
    }

    public fun createFakeOngoingMatch(
        interaction: Interaction,
        config: InteractionConfig,
        interactionId: String = interaction.id,
        index: Int = 1,
    ): InteractionRunningStatus.OngoingMatch =
        InteractionRunningStatus.OngoingMatch(
            index = index,
            interactionId = interactionId,
            interactionConfig = config,
            interaction = interaction,
        )
}

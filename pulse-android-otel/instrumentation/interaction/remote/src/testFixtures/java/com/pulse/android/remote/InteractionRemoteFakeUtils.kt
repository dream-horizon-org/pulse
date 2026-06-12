@file:Suppress("RedundantVisibilityModifier", "unused") // explicit api requires public modifier mentioned

package com.pulse.android.remote

import com.pulse.android.remote.models.InteractionAttrsEntry
import com.pulse.android.remote.models.InteractionConfig
import com.pulse.android.remote.models.InteractionEvent
import java.util.UUID
import java.util.concurrent.TimeUnit

public object InteractionRemoteFakeUtils {
    public fun createFakeInteractionConfig(
        id: Int = 1,
        name: String = "fake-interaction_" + UUID.randomUUID().toString(),
        eventSequence: List<InteractionEvent> = listOf(createFakeInteractionEvent()),
        globalBlacklistedEvents: List<InteractionEvent> = emptyList(),
        uptimeLowerLimitInNano: Long = 100L,
        uptimeMidLimitInNano: Long = 500L,
        uptimeUpperLimitInNano: Long = 1000L,
        thresholdInNanos: Long = TimeUnit.SECONDS.toNanos(20),
        shouldThrowOnWrongConfig: Boolean = true,
    ): InteractionConfig =
        InteractionConfig(
            id = id,
            name = name,
            events = eventSequence,
            globalBlacklistedEvents = globalBlacklistedEvents,
            uptimeLowerLimitInMs = uptimeLowerLimitInNano / 1000_000,
            uptimeMidLimitInMs = uptimeMidLimitInNano / 1000_000,
            uptimeUpperLimitInMs = uptimeUpperLimitInNano / 1000_000,
            thresholdInMs = thresholdInNanos / 1000_000,
            shouldThrowOnWrongConfig = shouldThrowOnWrongConfig,
            description = "fake-description",
        )

    public fun createFakeInteractionAttrsEntry(
        name: String = "fake-attr-name",
        value: String = "fake-attr-value",
        operator: String = "EQUALS",
    ): InteractionAttrsEntry =
        InteractionAttrsEntry(
            name = name,
            value = value,
            operator = operator,
        )

    public fun createFakeInteractionEvent(
        name: String = "fake-event",
        props: List<InteractionAttrsEntry>? = null,
        isBlacklisted: Boolean = false,
    ): InteractionEvent =
        InteractionEvent(
            name = name,
            props = props,
            isBlacklisted = isBlacklisted,
        )
}

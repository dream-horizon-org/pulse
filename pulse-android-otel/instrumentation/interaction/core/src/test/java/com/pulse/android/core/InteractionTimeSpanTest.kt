package com.pulse.android.core

import com.pulse.android.remote.InteractionRemoteFakeUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class InteractionTimeSpanTest {
    @Test
    fun `getTimeSpanInNanos uses last event when three events`() {
        val config =
            InteractionRemoteFakeUtils.createFakeInteractionConfig(
                eventSequence =
                    listOf(
                        InteractionRemoteFakeUtils.createFakeInteractionEvent("a"),
                        InteractionRemoteFakeUtils.createFakeInteractionEvent("b"),
                        InteractionRemoteFakeUtils.createFakeInteractionEvent("c"),
                    ),
            )
        val t0 = 1_000L * 1_000_000_000
        val t1 = t0 + 2_000_000_000L
        val t2 = t0 + 9_000_000_000L
        val events =
            listOf(
                InteractionLocalEventFakeUtils.createFakeInteractionLocalEvent("a", t0),
                InteractionLocalEventFakeUtils.createFakeInteractionLocalEvent("b", t1),
                InteractionLocalEventFakeUtils.createFakeInteractionLocalEvent("c", t2),
            )
        val interaction =
            InteractionFakeUtils.createFakeInteraction(
                interactionId = "id-1",
                config = config,
                events = events,
            )
        assertThat(interaction.getTimeSpanInNanos(config.thresholdInMs)).isEqualTo(t0 to t2)
    }

    @Test
    fun `getTimeSpanInNanos matches first and second when only two events`() {
        val config =
            InteractionRemoteFakeUtils.createFakeInteractionConfig(
                eventSequence =
                    listOf(
                        InteractionRemoteFakeUtils.createFakeInteractionEvent("a"),
                        InteractionRemoteFakeUtils.createFakeInteractionEvent("b"),
                    ),
            )
        val t0 = 100L
        val t1 = 500L
        val events =
            listOf(
                InteractionLocalEventFakeUtils.createFakeInteractionLocalEvent("a", t0),
                InteractionLocalEventFakeUtils.createFakeInteractionLocalEvent("b", t1),
            )
        val interaction =
            InteractionFakeUtils.createFakeInteraction(
                interactionId = "id-2",
                config = config,
                events = events,
            )
        assertThat(interaction.getTimeSpanInNanos(config.thresholdInMs)).isEqualTo(t0 to t1)
    }

    @Test
    fun `getTimeSpanInNanos single event extends end by threshold`() {
        val config = InteractionRemoteFakeUtils.createFakeInteractionConfig()
        val t0 = 5_000L * 1_000_000_000
        val thresholdMs = config.thresholdInMs
        val events = listOf(InteractionLocalEventFakeUtils.createFakeInteractionLocalEvent("solo", t0))
        val interaction =
            InteractionFakeUtils.createFakeInteraction(
                interactionId = "id-3",
                config = config,
                events = events,
            )
        assertThat(interaction.getTimeSpanInNanos(thresholdMs))
            .isEqualTo(t0 to t0 + thresholdMs * 1_000_000L)
    }
}

@file:OptIn(ExperimentalCoroutinesApi::class)
@file:Suppress("ClassName")

package com.pulse.android.core

import com.pulse.android.core.utils.InteractionFakeUtils
import com.pulse.android.remote.InteractionApiService
import com.pulse.android.remote.models.InteractionAttrsEntry
import com.pulse.android.remote.models.InteractionConfig
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions
import org.assertj.core.api.ThrowingConsumer
import org.assertj.core.api.iterable.ThrowingExtractor
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

@ExtendWith(MockKExtension::class)
class InteractionManagerTest {

    private lateinit var mockInteractionManager: InteractionManager

    @MockK
    lateinit var mockApiService: InteractionApiService

    private val standardTestDispatcher =
        StandardTestDispatcher(name = "InteractionManagerTest\$standardTestDispatcher")


    @BeforeEach
    fun init() {
        mockInteractionManager = InteractionManager(
            mockApiService,
            standardTestDispatcher,
            standardTestDispatcher
        )
    }

    @Test
    fun `Calling instance gives non null instance`() = runTest {
        val interactionManager = InteractionManager.instance
        Assertions.assertThat(interactionManager).isNotNull
    }

    @Test
    fun `When interaction init is not done interactionTrackers should be null`() =
        runTest(standardTestDispatcher) {
            coEvery { mockApiService.getInteractions() } returns emptyList()
            advanceUntilIdle()
            Assertions.assertThat(mockInteractionManager.interactionTrackers).isNull()
        }

    @Test
    fun `When interactions are empty interaction trackers should be empty`() =
        runTest(standardTestDispatcher) {
            coEvery { mockApiService.getInteractions() } returns emptyList()
            mockInteractionManager.init()
            advanceUntilIdle()
            Assertions.assertThat(mockInteractionManager.interactionTrackers).isEmpty()
        }

    @Test
    fun `When interaction is one interaction trackers should be one`() =
        runTest(standardTestDispatcher) {
            coEvery { mockApiService.getInteractions() } returns listOf(InteractionFakeUtils.createFakeInteractionConfig())
            mockInteractionManager.init()
            advanceUntilIdle()
            Assertions.assertThat(mockInteractionManager.interactionTrackers).hasSize(1)
        }

    @Test
    fun `When interaction is two interaction trackers should be two`() =
        runTest(standardTestDispatcher) {
            coEvery { mockApiService.getInteractions() } returns listOf(
                InteractionFakeUtils.createFakeInteractionConfig(),
                InteractionFakeUtils.createFakeInteractionConfig(),
            )
            mockInteractionManager.init()
            advanceUntilIdle()
            Assertions.assertThat(mockInteractionManager.interactionTrackers).hasSize(2)
        }

    @Test
    fun `When interaction config has no event config throw with assertion`() =
        runTest(standardTestDispatcher) {
            org.junit.jupiter.api.Assertions.assertThrows(NoSuchElementException::class.java) {
                InteractionFakeUtils.createFakeInteractionConfig(
                    eventSequence = emptyList()
                )
            }
        }

    @Test
    fun `When interaction config has all blacklisted config throws with exception`() =
        runTest(standardTestDispatcher) {
            org.junit.jupiter.api.Assertions.assertThrows(AssertionError::class.java) {
                InteractionFakeUtils.createFakeInteractionConfig(
                    eventSequence = listOf(
                        InteractionFakeUtils.createFakeInteractionEvent(
                            "blacklisted",
                            isBlacklisted = true
                        )
                    )
                )
            }
        }

    @Nested
    inner class `With two length interaction config` {
        private val interactionConfigWithTwoEvents =
            InteractionFakeUtils.createFakeInteractionConfig(
                eventSequence = listOf(
                    InteractionFakeUtils.createFakeInteractionEvent(
                        name = "event1",
                    ),
                    InteractionFakeUtils.createFakeInteractionEvent(
                        name = "event2",
                    ),
                ),
                globalBlacklistedEvents = listOf(
                    InteractionFakeUtils.createFakeInteractionEvent(
                        name = "blacklist1",
                    ),
                ),
            )

        @Test
        fun `With events in same order and correct time`() =
            runTest(standardTestDispatcher) {
                initMockInteractionManager(interactionConfigWithTwoEvents)
                addEventWithNanoTimeFromBoot(
                    "event1",
                    emptyMap()
                )
                val ongoingId = assertSingleOngoingInteraction()

                addEventWithNanoTimeFromBoot(
                    "event2",
                    emptyMap()
                )

                assertSingleFinalInteraction(ongoingId)
            }

        @Test
        fun `With events in same order with reverse time`() =
            runTest(standardTestDispatcher) {
                initMockInteractionManager(interactionConfigWithTwoEvents)
                val timeInNano = System.nanoTime()
                addEventWithNanoTimeFromBoot(
                    "event1",
                    emptyMap(),
                    timeInNano,
                )
                assertSingleOngoingInteraction()

                addEventWithNanoTimeFromBoot(
                    "event2",
                    emptyMap(),
                    timeInNano - 1,
                )

                assertSingleOngoingInteraction()
            }

        @Test
        fun `With events in same order with props`() =
            runTest(standardTestDispatcher) {
                val config = InteractionFakeUtils.createFakeInteractionConfig(
                    eventSequence = listOf(
                        InteractionFakeUtils.createFakeInteractionEvent(
                            name = "event1",
                        ),
                        InteractionFakeUtils.createFakeInteractionEvent(
                            name = "event2",
                            props = listOf(
                                InteractionAttrsEntry(
                                    "key1",
                                    "value1",
                                    operator = InteractionConstant.Operators.EQUALS.name
                                )
                            )
                        ),
                    ),
                )
                initMockInteractionManager(config)
                addEventWithNanoTimeFromBoot(
                    "event1",
                    emptyMap(),
                )
                assertSingleOngoingInteraction()

                addEventWithNanoTimeFromBoot(
                    "event2",
                    emptyMap(),
                )

                assertSingleOngoingInteraction()

                addEventWithNanoTimeFromBoot(
                    "event2",
                    mapOf("key1" to "value1"),
                )

                assertSingleFinalInteraction()
            }

        @Test
        fun `With events in same order with reverse time 1`() =
            runTest(standardTestDispatcher) {
                initMockInteractionManager(interactionConfigWithTwoEvents)
                val timeInNano = System.nanoTime()
                addEventWithNanoTimeFromBoot(
                    "event1",
                    emptyMap(),
                    timeInNano,
                )
                assertSingleOngoingInteraction()

                addEventWithNanoTimeFromBoot(
                    "event2",
                    emptyMap(),
                    timeInNano - 1,
                )

                assertSingleOngoingInteraction()

                addEventWithNanoTimeFromBoot(
                    "event2",
                    emptyMap(),
                    timeInNano + 1,
                )

                assertSingleFinalInteraction()
            }

        @Test
        fun `With events in reverse order with correct time`() =
            runTest(standardTestDispatcher) {
                initMockInteractionManager(interactionConfigWithTwoEvents)
                val timeStampInNano = System.nanoTime()
                addEventWithNanoTimeFromBoot(
                    "event2",
                    emptyMap(),
                    timeStampInNano,
                )
                assertSingleNoOngoingInteraction()

                addEventWithNanoTimeFromBoot(
                    "event1",
                    emptyMap(),
                    timeStampInNano - 1,
                )

                assertSingleFinalInteraction()
            }

        @Test
        fun `When events happen with same timestamp`() =
            runTest(standardTestDispatcher) {
                val sameEventTime = System.nanoTime()
                initMockInteractionManager(interactionConfigWithTwoEvents)
                addEventWithNanoTimeFromBoot(
                    "event2",
                    emptyMap(),
                    eventTimeInNano = sameEventTime,
                )
                assertSingleNoOngoingInteraction()

                addEventWithNanoTimeFromBoot(
                    "event1",
                    emptyMap(),
                    eventTimeInNano = sameEventTime,
                )

                assertSingleFinalInteraction()
            }

        @Test
        fun `event1, event2, blacklist1 before event2 gives ongoing then no interaction`() =
            runTest(standardTestDispatcher) {
                val sameEventTime = System.nanoTime()
                initMockInteractionManager(interactionConfigWithTwoEvents)
                addEventWithNanoTimeFromBoot(
                    "event1",
                    emptyMap(),
                    eventTimeInNano = sameEventTime,
                )
                val interactionId = assertSingleOngoingInteraction()

                addEventWithNanoTimeFromBoot(
                    "event2",
                    emptyMap(),
                    eventTimeInNano = sameEventTime + 2,
                )

                addEventWithNanoTimeFromBoot(
                    "blacklist1",
                    emptyMap(),
                    eventTimeInNano = sameEventTime + 1,
                )
                assertSingleFinalInteraction(interactionId)
            }

        @Test
        fun `event1, event2, blacklist1 before event1 gives ongoing then final interaction`() =
            runTest(standardTestDispatcher) {
                val sameEventTime = System.nanoTime()
                initMockInteractionManager(interactionConfigWithTwoEvents)
                addEventWithNanoTimeFromBoot(
                    "event1",
                    emptyMap(),
                    eventTimeInNano = sameEventTime,
                )
                val interactionId = assertSingleOngoingInteraction()

                addEventWithNanoTimeFromBoot(
                    "event2",
                    emptyMap(),
                    eventTimeInNano = sameEventTime + 2,
                )

                addEventWithNanoTimeFromBoot(
                    "blacklist1",
                    emptyMap(),
                    eventTimeInNano = sameEventTime - 1,
                )

                assertSingleFinalInteraction(interactionId)
            }

        @Test
        fun `event1, event2, blacklist1 after event2 gives ongoing then final interaction`() =
            runTest(standardTestDispatcher) {
                val sameEventTime = System.nanoTime()
                initMockInteractionManager(interactionConfigWithTwoEvents)
                addEventWithNanoTimeFromBoot(
                    "event1",
                    emptyMap(),
                    eventTimeInNano = sameEventTime,
                )
                val interactionId = assertSingleOngoingInteraction()

                addEventWithNanoTimeFromBoot(
                    "event2",
                    emptyMap(),
                    eventTimeInNano = sameEventTime + 2,
                )

                addEventWithNanoTimeFromBoot(
                    "blacklist1",
                    emptyMap(),
                    eventTimeInNano = sameEventTime + 3,
                )

                assertSingleFinalInteraction(interactionId)
            }

        @Test
        fun `With config of two same event event four of that event trigger two interactions`() =
            runTest(standardTestDispatcher) {
                initMockInteractionManager(interactionConfigWithTwoEvents)
                addEventWithNanoTimeFromBoot(
                    "event1",
                    emptyMap()
                )
                val ongoingId = assertSingleOngoingInteraction()

                addEventWithNanoTimeFromBoot(
                    "event2",
                    emptyMap()
                )

                assertSingleFinalInteraction(ongoingId)

                addEventWithNanoTimeFromBoot(
                    "event1",
                    emptyMap()
                )

                val newOngoingId1 = assertSingleOngoingInteraction()

                Assertions.assertThat(ongoingId).isNotEqualTo(newOngoingId1)

                addEventWithNanoTimeFromBoot(
                    "event2",
                    emptyMap()
                )

                val (final2ndInteractionId, _) = assertSingleFinalInteraction()

                Assertions.assertThat(newOngoingId1).isEqualTo(final2ndInteractionId)
            }

        @Test
        fun `With one correct event and second different event keeps the ongoing interaction`() =
            runTest(standardTestDispatcher) {
                initMockInteractionManager(interactionConfigWithTwoEvents)
                addEventWithNanoTimeFromBoot(
                    "event1",
                    emptyMap()
                )
                val id = assertSingleOngoingInteraction()

                addEventWithNanoTimeFromBoot(
                    "eventUnknown",
                    emptyMap()
                )

                assertSingleOngoingInteraction(id)
            }

        @Test
        fun `event1, unknown event, event2 keeps gives the final interaction`() =
            runTest(standardTestDispatcher) {
                initMockInteractionManager(interactionConfigWithTwoEvents)
                addEventWithNanoTimeFromBoot(
                    "event1",
                    emptyMap()
                )
                val id = assertSingleOngoingInteraction()

                addEventWithNanoTimeFromBoot(
                    "eventUnknown",
                    emptyMap()
                )
                assertSingleOngoingInteraction(id)

                addEventWithNanoTimeFromBoot(
                    "event2",
                    emptyMap()
                )
                assertSingleFinalInteraction(id)
            }
    }

    @Nested
    inner class `With global black listed event` {
        private val doubleEventConfig = InteractionFakeUtils.createFakeInteractionConfig(
            eventSequence = listOf(
                InteractionFakeUtils.createFakeInteractionEvent("event1"),
                InteractionFakeUtils.createFakeInteractionEvent("event2"),
            ),
            globalBlacklistedEvents = listOf(
                InteractionFakeUtils.createFakeInteractionEvent("blacklist1"),
                InteractionFakeUtils.createFakeInteractionEvent("blacklist2"),
            ),
        )

        @Test
        fun `with double event config, when event start with correct 1st then blacklisted event then correct 1st event`() =
            runTest(standardTestDispatcher) {
                initMockInteractionManager(doubleEventConfig)
                addEventWithNanoTimeFromBoot("event1")
                val interactionId1st = assertSingleOngoingInteraction()
                advanceTimeBy(1.seconds)
                addEventWithNanoTimeFromBoot("blacklist1")
                assertSingleNoOngoingInteraction()
                addEventWithNanoTimeFromBoot("event1")
                val interactionId2nd = assertSingleOngoingInteraction()

                Assertions.assertThat(interactionId2nd).isNotEqualTo(interactionId1st)
            }

        @Test
        fun `with double event config, when event start with correct 1st then blacklisted event then correct 2nd event`() =
            runTest(standardTestDispatcher) {
                initMockInteractionManager(doubleEventConfig)
                addEventWithNanoTimeFromBoot("event1")
                assertSingleOngoingInteraction()
                addEventWithNanoTimeFromBoot("blacklist1")
                assertSingleNoOngoingInteraction()
                addEventWithNanoTimeFromBoot("event2")
                assertSingleNoOngoingInteraction()
            }

        @Test
        fun `with double event config, when event start with correct 1st then blacklisted event then 1st then 2nd`() =
            runTest(standardTestDispatcher) {
                initMockInteractionManager(doubleEventConfig)
                addEventWithNanoTimeFromBoot("event1")
                assertSingleOngoingInteraction()
                addEventWithNanoTimeFromBoot("blacklist1")
                assertSingleNoOngoingInteraction()
                addEventWithNanoTimeFromBoot("event1")
                val interactionId = assertSingleOngoingInteraction()
                addEventWithNanoTimeFromBoot("event2")
                assertSingleFinalInteraction(interactionId)
            }

        @Test
        fun `with double event config and global blacklisted with props, when event start with correct 1st then blacklisted event without props then 1st then 2nd`() =
            runTest(standardTestDispatcher) {
                initMockInteractionManager(
                    InteractionFakeUtils.createFakeInteractionConfig(
                        eventSequence = listOf(
                            InteractionFakeUtils.createFakeInteractionEvent("event1"),
                            InteractionFakeUtils.createFakeInteractionEvent("event2"),
                        ),
                        globalBlacklistedEvents = listOf(
                            InteractionFakeUtils.createFakeInteractionEvent(
                                "blacklist1", props = listOf(
                                    InteractionAttrsEntry(
                                        "key1",
                                        "value1",
                                        InteractionConstant.Operators.EQUALS.name
                                    )
                                )
                            ),
                        ),
                    )
                )
                addEventWithNanoTimeFromBoot("event1")
                val interactionId = assertSingleOngoingInteraction()
                addEventWithNanoTimeFromBoot("blacklist1")
                assertSingleOngoingInteraction()
                addEventWithNanoTimeFromBoot("event2")
                assertSingleFinalInteraction(interactionId)
            }

        @Test
        fun `with double event config and global blacklisted with props, when event start with correct 1st then blacklisted event with same props then 1st then 2nd`() =
            runTest(standardTestDispatcher) {
                initMockInteractionManager(
                    InteractionFakeUtils.createFakeInteractionConfig(
                        eventSequence = listOf(
                            InteractionFakeUtils.createFakeInteractionEvent("event1"),
                            InteractionFakeUtils.createFakeInteractionEvent("event2"),
                        ),
                        globalBlacklistedEvents = listOf(
                            InteractionFakeUtils.createFakeInteractionEvent(
                                "blacklist1", props = listOf(
                                    InteractionAttrsEntry(
                                        "key1",
                                        "value1",
                                        InteractionConstant.Operators.EQUALS.name
                                    )
                                )
                            ),
                        ),
                    )
                )
                addEventWithNanoTimeFromBoot("event1")
                assertSingleOngoingInteraction()
                addEventWithNanoTimeFromBoot("blacklist1", mapOf("key1" to "value1"))
                assertSingleNoOngoingInteraction()
                addEventWithNanoTimeFromBoot("event2")
                assertSingleNoOngoingInteraction()
            }
    }

    @Nested
    inner class `With local black listed event` {
        @Test
        fun `when correct event happen without blacklisted event`() =
            runTest(standardTestDispatcher) {
                val interactionConfig = InteractionFakeUtils.createFakeInteractionConfig(
                    eventSequence = listOf(
                        InteractionFakeUtils.createFakeInteractionEvent("event1"),
                        InteractionFakeUtils.createFakeInteractionEvent(
                            "blacklist",
                            isBlacklisted = true
                        ),
                        InteractionFakeUtils.createFakeInteractionEvent("event2"),
                    ),
                )
                initMockInteractionManager(interactionConfig)
                addEventWithNanoTimeFromBoot("event1")
                val interactionId = assertSingleOngoingInteraction()
                addEventWithNanoTimeFromBoot("event2")
                assertSingleFinalInteraction(interactionId)
            }

        @Disabled("Single event not supported")
        @Test
        fun `config with one event and trailing blacklisted events when first event is correct`() =
            runTest(standardTestDispatcher) {
                val interactionConfig = InteractionFakeUtils.createFakeInteractionConfig(
                    eventSequence = listOf(
                        InteractionFakeUtils.createFakeInteractionEvent("event1"),
                        InteractionFakeUtils.createFakeInteractionEvent(
                            "blacklist1",
                            isBlacklisted = true
                        ),
                        InteractionFakeUtils.createFakeInteractionEvent(
                            "blacklist2",
                            isBlacklisted = true
                        ),
                    ),
                )
                initMockInteractionManager(interactionConfig)
                addEventWithNanoTimeFromBoot("event1")
                assertSingleFinalInteraction()
            }

        @Disabled("Trailing black list ")
        @Test
        fun `config with two event and trailing blacklisted events when first event is correct`() =
            runTest(standardTestDispatcher) {
                val interactionConfig = InteractionFakeUtils.createFakeInteractionConfig(
                    eventSequence = listOf(
                        InteractionFakeUtils.createFakeInteractionEvent("event1"),
                        InteractionFakeUtils.createFakeInteractionEvent(
                            "blacklist1",
                            isBlacklisted = true
                        ),
                        InteractionFakeUtils.createFakeInteractionEvent("event2"),
                        InteractionFakeUtils.createFakeInteractionEvent(
                            "blacklist2",
                            isBlacklisted = true
                        ),
                    ),
                )
                initMockInteractionManager(interactionConfig)
                addEventWithNanoTimeFromBoot("event1")
                assertSingleOngoingInteraction()
            }

        @Disabled("Trailing black list ")
        @Test
        fun `config with two event and trailing blacklisted events when first event is correct and second is incorrect`() =
            runTest(standardTestDispatcher) {
                val interactionConfig = InteractionFakeUtils.createFakeInteractionConfig(
                    eventSequence = listOf(
                        InteractionFakeUtils.createFakeInteractionEvent("event1"),
                        InteractionFakeUtils.createFakeInteractionEvent("event2"),
                        InteractionFakeUtils.createFakeInteractionEvent(
                            "blacklist1",
                            isBlacklisted = true
                        ),
                        InteractionFakeUtils.createFakeInteractionEvent(
                            "blacklist2",
                            isBlacklisted = true
                        ),
                    ),
                )
                initMockInteractionManager(interactionConfig)
                addEventWithNanoTimeFromBoot("event1")
                assertSingleOngoingInteraction()

                addEventWithNanoTimeFromBoot("eventUnknown")
                assertSingleOngoingInteraction()
            }

        @Test
        fun `config with two event and trailing blacklisted events when first and second event is config first event`() =
            runTest(standardTestDispatcher) {
                val interactionConfig = InteractionFakeUtils.createFakeInteractionConfig(
                    eventSequence = listOf(
                        InteractionFakeUtils.createFakeInteractionEvent("event1"),
                        InteractionFakeUtils.createFakeInteractionEvent("event2"),
                        InteractionFakeUtils.createFakeInteractionEvent(
                            "blacklist1",
                            isBlacklisted = true
                        ),
                        InteractionFakeUtils.createFakeInteractionEvent(
                            "blacklist2",
                            isBlacklisted = true
                        ),
                        InteractionFakeUtils.createFakeInteractionEvent("event3"),
                    ),
                )
                initMockInteractionManager(interactionConfig)
                addEventWithNanoTimeFromBoot("event1")
                assertSingleOngoingInteraction()

                addEventWithNanoTimeFromBoot("event1")
                assertSingleOngoingInteraction()
            }

        @Test
        fun `config with two event and trailing blacklisted events when first and second event is config second event`() =
            runTest(standardTestDispatcher) {
                val interactionConfig = InteractionFakeUtils.createFakeInteractionConfig(
                    eventSequence = listOf(
                        InteractionFakeUtils.createFakeInteractionEvent("event1"),
                        InteractionFakeUtils.createFakeInteractionEvent("event2"),
                        InteractionFakeUtils.createFakeInteractionEvent(
                            "blacklist1",
                            isBlacklisted = true
                        ),
                        InteractionFakeUtils.createFakeInteractionEvent(
                            "blacklist2",
                            isBlacklisted = true
                        ),
                        InteractionFakeUtils.createFakeInteractionEvent("event3"),
                    ),
                )
                initMockInteractionManager(interactionConfig)
                addEventWithNanoTimeFromBoot("event2")
                assertSingleNoOngoingInteraction()

                addEventWithNanoTimeFromBoot("event2")
                assertSingleNoOngoingInteraction()
            }

        @Test
        fun `config with two events and fron blacklisted event`() =
            runTest(standardTestDispatcher) {
                val interactionConfig = InteractionFakeUtils.createFakeInteractionConfig(
                    eventSequence = listOf(
                        InteractionFakeUtils.createFakeInteractionEvent("event0"),
                        InteractionFakeUtils.createFakeInteractionEvent(
                            "blacklist1",
                            isBlacklisted = true
                        ),
                        InteractionFakeUtils.createFakeInteractionEvent(
                            "blacklist2",
                            isBlacklisted = true
                        ),
                        InteractionFakeUtils.createFakeInteractionEvent("event1"),
                        InteractionFakeUtils.createFakeInteractionEvent("event2"),
                    ),
                )
                initMockInteractionManager(interactionConfig)
                addEventWithNanoTimeFromBoot("event0")
                val interactionId = assertSingleOngoingInteraction()
                addEventWithNanoTimeFromBoot("event1")
                assertSingleOngoingInteraction(interactionId)

                addEventWithNanoTimeFromBoot("event2")
                assertSingleFinalInteraction(interactionId)
            }
    }

    // todo add queue for events before config download
    //  clear that queue even config api fails or interaction is empty

    @Nested
    inner class `When event matching gets broken by config allowed events` {
        @Test
        fun `When first event comes to break the ongoing match and then completed the interaction`() =
            runTest(standardTestDispatcher) {
                val interactionConfig = InteractionFakeUtils.createFakeInteractionConfig(
                    eventSequence = listOf(
                        InteractionFakeUtils.createFakeInteractionEvent("event1"),
                        InteractionFakeUtils.createFakeInteractionEvent("event2"),
                        InteractionFakeUtils.createFakeInteractionEvent(
                            "blacklist1",
                            isBlacklisted = true
                        ),
                        InteractionFakeUtils.createFakeInteractionEvent(
                            "blacklist2",
                            isBlacklisted = true
                        ),
                        InteractionFakeUtils.createFakeInteractionEvent("event3"),
                    ),
                )
                initMockInteractionManager(interactionConfig)
                addEventWithNanoTimeFromBoot("event1")
                val interactionId = assertSingleOngoingInteraction()

                addEventWithNanoTimeFromBoot("event2")
                assertSingleOngoingInteraction(interactionId)

                addEventWithNanoTimeFromBoot("event1")
                val interactionId2 = assertSingleOngoingInteraction()
                Assertions.assertThat(interactionId2).isNotEqualTo(interactionId)

                addEventWithNanoTimeFromBoot("event2")
                assertSingleOngoingInteraction(interactionId2)

                addEventWithNanoTimeFromBoot("event3")
                assertSingleFinalInteraction(interactionId2)
            }
    }

    @Nested
    inner class `With multiple interaction running simultaneously` {
        @Test
        fun `two config with one longer then the previous one`() = runTest(standardTestDispatcher) {
            val interactionConfigLen2 = InteractionFakeUtils.createFakeInteractionConfig(
                eventSequence = listOf(
                    InteractionFakeUtils.createFakeInteractionEvent("event1"),
                    InteractionFakeUtils.createFakeInteractionEvent("event2"),
                )
            )

            val interactionConfigLen3 = InteractionFakeUtils.createFakeInteractionConfig(
                eventSequence = listOf(
                    InteractionFakeUtils.createFakeInteractionEvent("event1"),
                    InteractionFakeUtils.createFakeInteractionEvent("event2"),
                    InteractionFakeUtils.createFakeInteractionEvent("event3"),
                )
            )

            initMockInteractionManager(interactionConfigLen2, interactionConfigLen3)

            addEventWithNanoTimeFromBoot("event1")
            assertAllInteraction<InteractionRunningStatus.OngoingMatch>(2)

            addEventWithNanoTimeFromBoot("event2")

            advanceTimeBy(1.seconds)
            Assertions.assertThat(mockInteractionManager.interactionTrackerStatesState.value)
                .hasSize(2)
                .hasOnlyElementsOfType(InteractionRunningStatus.OngoingMatch::class.java)
                .satisfiesOnlyOnce(
                    ThrowingConsumer {
                        Assertions.assertThat((it as? InteractionRunningStatus.OngoingMatch)?.interaction).isNotNull
                    }
                )

            addEventWithNanoTimeFromBoot("event3")

            advanceUntilIdle()

            Assertions.assertThat(mockInteractionManager.interactionTrackerStatesState.value)
                .hasSize(2)
                .hasOnlyElementsOfType(InteractionRunningStatus.OngoingMatch::class.java)
                .allSatisfy(
                    ThrowingConsumer {
                        Assertions.assertThat((it as InteractionRunningStatus.OngoingMatch).interaction).isNotNull
                    }
                ).extracting(
                    ThrowingExtractor {
                        (it as InteractionRunningStatus.OngoingMatch).interaction!!.id
                    }
                ).doesNotHaveDuplicates()
        }
    }

    @Nested
    inner class `With delay processing` {

        @Disabled("Not supported as of now, we can support this with props acting as dimensions")
        @Nested
        inner class `With multi interactions` {
            /*
            e1     e2
                e1    e2
             */
            @Test
            fun `event1, event1, event1 after first event1, event2 gives two interaction with two event config`() =
                runTest(standardTestDispatcher) {
                    val interactionConfig = InteractionFakeUtils.createFakeInteractionConfig(
                        eventSequence = listOf(
                            InteractionFakeUtils.createFakeInteractionEvent("event1"),
                            InteractionFakeUtils.createFakeInteractionEvent("event2"),
                        )
                    )
                    initMockInteractionManager(interactionConfig)
                    addEventWithNanoTimeFromBoot("event1")
                    advanceTimeBy(1.seconds)
                    assertSingleOngoingInteraction(skipAdvancing = true)

                    addEventWithNanoTimeFromBoot("event1")
                    advanceTimeBy(1.seconds)
                    assertAllInteraction<InteractionRunningStatus.OngoingMatch>(
                        2,
                        skipAdvancing = true
                    )


                    advanceTimeBy(19.seconds)
                    assertAllInteraction<InteractionRunningStatus.OngoingMatch>(
                        2,
                        skipAdvancing = true
                    )
                    Assertions.assertThat(
                        mockInteractionManager.interactionTrackerStatesState.value
                            .filterIsInstance<InteractionRunningStatus.OngoingMatch>()
                    ).filteredOn { it.interaction != null }
                        .hasSize(1)

                    advanceTimeBy(1.seconds)
                    Assertions.assertThat(
                        mockInteractionManager.interactionTrackerStatesState.value
                            .filterIsInstance<InteractionRunningStatus.OngoingMatch>()
                    ).filteredOn { it.interaction != null }
                        .hasSize(2)
                }

            /*
            e1      e2
                  e1     e2
               gb
             */
            @Test
            fun `event1, event1, event1 after first event1, event2, gb after first event1 gives one interaction with two event config`() =
                runTest(standardTestDispatcher) {
                    val interactionConfig = InteractionFakeUtils.createFakeInteractionConfig(
                        eventSequence = listOf(
                            InteractionFakeUtils.createFakeInteractionEvent("event1"),
                            InteractionFakeUtils.createFakeInteractionEvent("event2"),
                        )
                    )
                    initMockInteractionManager(interactionConfig)
                    addEventWithNanoTimeFromBoot("event1")
                    advanceTimeBy(1.seconds)
                    assertSingleOngoingInteraction(skipAdvancing = true)

                    addEventWithNanoTimeFromBoot("event1")
                    advanceTimeBy(1.seconds)
                    assertAllInteraction<InteractionRunningStatus.OngoingMatch>(
                        2,
                        skipAdvancing = true
                    )


                    advanceTimeBy(19.seconds)
                    assertAllInteraction<InteractionRunningStatus.OngoingMatch>(
                        2,
                        skipAdvancing = true
                    )
                    Assertions.assertThat(
                        mockInteractionManager.interactionTrackerStatesState.value
                            .filterIsInstance<InteractionRunningStatus.OngoingMatch>()
                    ).filteredOn { it.interaction != null }
                        .hasSize(1)

                    advanceTimeBy(1.seconds)
                    Assertions.assertThat(
                        mockInteractionManager.interactionTrackerStatesState.value
                            .filterIsInstance<InteractionRunningStatus.OngoingMatch>()
                    ).filteredOn { it.interaction != null }
                        .hasSize(2)
                }

            /*
            e1      e2
                 e1    gb e2

             */
            @Test
            fun `event1, event1, event1 after first event1, event2, gb after first event2 gives one interaction with two event config`() =
                runTest(standardTestDispatcher) {
                    val interactionConfig = InteractionFakeUtils.createFakeInteractionConfig(
                        eventSequence = listOf(
                            InteractionFakeUtils.createFakeInteractionEvent("event1"),
                            InteractionFakeUtils.createFakeInteractionEvent("event2"),
                        )
                    )
                    initMockInteractionManager(interactionConfig)
                    addEventWithNanoTimeFromBoot("event1")
                    advanceTimeBy(1.seconds)
                    assertSingleOngoingInteraction(skipAdvancing = true)

                    addEventWithNanoTimeFromBoot("event1")
                    advanceTimeBy(1.seconds)
                    assertAllInteraction<InteractionRunningStatus.OngoingMatch>(
                        2,
                        skipAdvancing = true
                    )


                    advanceTimeBy(19.seconds)
                    assertAllInteraction<InteractionRunningStatus.OngoingMatch>(
                        2,
                        skipAdvancing = true
                    )
                    Assertions.assertThat(
                        mockInteractionManager.interactionTrackerStatesState.value
                            .filterIsInstance<InteractionRunningStatus.OngoingMatch>()
                    ).filteredOn { it.interaction != null }
                        .hasSize(1)

                    advanceTimeBy(1.seconds)
                    Assertions.assertThat(
                        mockInteractionManager.interactionTrackerStatesState.value
                            .filterIsInstance<InteractionRunningStatus.OngoingMatch>()
                    ).filteredOn { it.interaction != null }
                        .hasSize(2)
                }

            /*
            e1          e2
                 e1  gb     e2

             */
            @Test
            fun `event1, event1, event1 after first event1, event2, gb after e1 before e2 gives one interaction with two event config`() =
                runTest(standardTestDispatcher) {
                    val interactionConfig = InteractionFakeUtils.createFakeInteractionConfig(
                        eventSequence = listOf(
                            InteractionFakeUtils.createFakeInteractionEvent("event1"),
                            InteractionFakeUtils.createFakeInteractionEvent("event2"),
                        )
                    )
                    initMockInteractionManager(interactionConfig)
                    addEventWithNanoTimeFromBoot("event1")
                    advanceTimeBy(1.seconds)
                    assertSingleOngoingInteraction(skipAdvancing = true)

                    addEventWithNanoTimeFromBoot("event1")
                    advanceTimeBy(1.seconds)
                    assertAllInteraction<InteractionRunningStatus.OngoingMatch>(
                        2,
                        skipAdvancing = true
                    )


                    advanceTimeBy(19.seconds)
                    assertAllInteraction<InteractionRunningStatus.OngoingMatch>(
                        2,
                        skipAdvancing = true
                    )
                    Assertions.assertThat(
                        mockInteractionManager.interactionTrackerStatesState.value
                            .filterIsInstance<InteractionRunningStatus.OngoingMatch>()
                    ).filteredOn { it.interaction != null }
                        .hasSize(1)

                    advanceTimeBy(1.seconds)
                    Assertions.assertThat(
                        mockInteractionManager.interactionTrackerStatesState.value
                            .filterIsInstance<InteractionRunningStatus.OngoingMatch>()
                    ).filteredOn { it.interaction != null }
                        .hasSize(2)
                }
        }

        // update to drop delay processing
        @Test
        fun `event1, event2 gives ongoing interaction then goes to final interaction`() =
            runTest(standardTestDispatcher) {
                val interactionConfig = InteractionFakeUtils.createFakeInteractionConfig(
                    eventSequence = listOf(
                        InteractionFakeUtils.createFakeInteractionEvent("event1"),
                        InteractionFakeUtils.createFakeInteractionEvent("event2"),
                    )
                )
                initMockInteractionManager(interactionConfig)
                addEventWithNanoTimeFromBoot("event1")
                advanceTimeBy(1.seconds)
                val interactionId1 = assertSingleOngoingInteraction(skipAdvancing = true)
                addEventWithNanoTimeFromBoot("event2")
                advanceTimeBy(1.seconds)
                assertSingleFinalInteraction(interactionId1, skipAdvancing = true)

                // terminal state
                advanceTimeBy(30.seconds)
                assertSingleFinalInteraction(interactionId1, skipAdvancing = true)
            }

        @Test
        fun `event1, event2, unknownEvent gives ongoing interaction then final interaction with two event config`() =
            runTest(standardTestDispatcher) {
                val interactionConfig = InteractionFakeUtils.createFakeInteractionConfig(
                    eventSequence = listOf(
                        InteractionFakeUtils.createFakeInteractionEvent("event1"),
                        InteractionFakeUtils.createFakeInteractionEvent("event2"),
                    )
                )
                initMockInteractionManager(interactionConfig)
                addEventWithNanoTimeFromBoot("event1")
                advanceTimeBy(1.seconds)
                val interactionId1 = assertSingleOngoingInteraction(skipAdvancing = true)

                addEventWithNanoTimeFromBoot("event2")
                advanceTimeBy(1.seconds)
                assertSingleFinalInteraction(interactionId1, skipAdvancing = true)

                addEventWithNanoTimeFromBoot("unknownEvent")
                advanceTimeBy(1.seconds)
                assertSingleFinalInteraction(interactionId1, skipAdvancing = true)

                // terminal state
                advanceTimeBy(30.seconds)
                assertSingleFinalInteraction(interactionId1)
            }

        @Test
        fun `event1, event2, globalBlacklist1 with older than event2 timestamp gives ongoing then no interaction`() =
            runTest(standardTestDispatcher) {
                val interactionConfig = InteractionFakeUtils.createFakeInteractionConfig(
                    eventSequence = listOf(
                        InteractionFakeUtils.createFakeInteractionEvent("event1"),
                        InteractionFakeUtils.createFakeInteractionEvent("event2"),
                    ),
                    globalBlacklistedEvents = listOf(
                        InteractionFakeUtils.createFakeInteractionEvent("blacklist1"),
                    ),
                )
                initMockInteractionManager(interactionConfig)
                val timeInNano = System.nanoTime()
                addEventWithNanoTimeFromBoot("event1", eventTimeInNano = timeInNano)
                advanceTimeBy(1.seconds)
                val interactionId1 = assertSingleOngoingInteraction(skipAdvancing = true)
                addEventWithNanoTimeFromBoot("event2", eventTimeInNano = timeInNano + 10)
                advanceTimeBy(10.seconds)
                // no delay processing, can be changed to ongoing interaction after persistence
                assertSingleFinalInteraction(interactionId1, skipAdvancing = true)
                addEventWithNanoTimeFromBoot("blacklist1", eventTimeInNano = timeInNano + 5)
                advanceTimeBy(5.seconds)
                assertSingleFinalInteraction(interactionId1, skipAdvancing = true)

                // terminal state
                advanceTimeBy(30.seconds)
                assertSingleFinalInteraction(interactionId1, skipAdvancing = true)
            }

        @Test
        fun `event1, event2, globalBlacklist1 gives ongoing then final interaction`() =
            runTest(standardTestDispatcher) {
                val interactionConfig = InteractionFakeUtils.createFakeInteractionConfig(
                    eventSequence = listOf(
                        InteractionFakeUtils.createFakeInteractionEvent("event1"),
                        InteractionFakeUtils.createFakeInteractionEvent("event2"),
                    ),
                    globalBlacklistedEvents = listOf(
                        InteractionFakeUtils.createFakeInteractionEvent("blacklist1"),
                    ),
                )
                initMockInteractionManager(interactionConfig)
                val timeInNano = System.nanoTime()
                addEventWithNanoTimeFromBoot("event1", eventTimeInNano = timeInNano)
                advanceTimeBy(1.seconds)
                val interactionId1 = assertSingleOngoingInteraction(skipAdvancing = true)
                addEventWithNanoTimeFromBoot("event2", eventTimeInNano = timeInNano + 1)
                advanceTimeBy(10.seconds)
                // no delay processing, can be changed to ongoing interaction after persistence
                assertSingleFinalInteraction(interactionId1, skipAdvancing = true)
                addEventWithNanoTimeFromBoot("blacklist1", eventTimeInNano = timeInNano + 2)
                advanceTimeBy(11.seconds)
                assertSingleFinalInteraction(skipAdvancing = true)

                // terminal state
                advanceTimeBy(30.seconds)
                assertSingleFinalInteraction(skipAdvancing = true)
            }

        @Test
        fun `event1, event2, globalBlacklist1 before event1 gives ongoing then final interaction`() =
            runTest(standardTestDispatcher) {
                val interactionConfig = InteractionFakeUtils.createFakeInteractionConfig(
                    eventSequence = listOf(
                        InteractionFakeUtils.createFakeInteractionEvent("event1"),
                        InteractionFakeUtils.createFakeInteractionEvent("event2"),
                    ),
                    globalBlacklistedEvents = listOf(
                        InteractionFakeUtils.createFakeInteractionEvent("blacklist1"),
                    ),
                )
                initMockInteractionManager(interactionConfig)
                val timeInNano = System.nanoTime()
                addEventWithNanoTimeFromBoot("event1", eventTimeInNano = timeInNano)
                advanceTimeBy(1.seconds)
                val interactionId1 = assertSingleOngoingInteraction(skipAdvancing = true)
                addEventWithNanoTimeFromBoot("event2", eventTimeInNano = timeInNano + 1)
                advanceTimeBy(10.seconds)
                // no delay processing, can be changed to ongoing interaction after persistence
                assertSingleFinalInteraction(interactionId1, skipAdvancing = true)
                addEventWithNanoTimeFromBoot("blacklist1", eventTimeInNano = timeInNano - 2)
                advanceTimeBy(11.seconds)
                assertSingleFinalInteraction(skipAdvancing = true)

                // terminal state
                advanceTimeBy(30.seconds)
                assertSingleFinalInteraction(skipAdvancing = true)
            }

        @Disabled("Out of order processing is not supported")
        @Test
        fun `event1, event2, localBlacklist1, event3 with localBlacklist1 older than event2 timestamp gives ongoing then no interaction`() =
            runTest(standardTestDispatcher) {
                val interactionConfig = InteractionFakeUtils.createFakeInteractionConfig(
                    eventSequence = listOf(
                        InteractionFakeUtils.createFakeInteractionEvent("event1"),
                        InteractionFakeUtils.createFakeInteractionEvent("localBlacklist1"),
                        InteractionFakeUtils.createFakeInteractionEvent("event2"),
                        InteractionFakeUtils.createFakeInteractionEvent("event3"),
                    ),
                )
                initMockInteractionManager(interactionConfig)
                val timeInNano = System.nanoTime()
                addEventWithNanoTimeFromBoot("event1", eventTimeInNano = timeInNano)
                advanceTimeBy(1.seconds)
                val interactionId1 = assertSingleOngoingInteraction(skipAdvancing = true)
                advanceTimeBy(10.seconds)
                assertSingleOngoingInteraction(interactionId1, skipAdvancing = true)
                addEventWithNanoTimeFromBoot("event2", eventTimeInNano = timeInNano + 10)
                advanceTimeBy(10.seconds)
                assertSingleOngoingInteraction(interactionId1, skipAdvancing = true)
                addEventWithNanoTimeFromBoot("localBlacklist1", eventTimeInNano = timeInNano + 5)
                advanceTimeBy(10.seconds)
                assertSingleNoOngoingInteraction(skipAdvancing = true)

                addEventWithNanoTimeFromBoot("event3", eventTimeInNano = timeInNano + 15)
                advanceTimeBy(10.seconds)
                assertSingleNoOngoingInteraction(skipAdvancing = true)

                // terminal state
                advanceTimeBy(30.seconds)
                assertSingleNoOngoingInteraction(skipAdvancing = true)
            }

        @Disabled("Out of order processing is not supported")
        @Test
        fun `event1, event2, globalBlacklist1 with older than event1 timestamp gives ongoing then final interaction`() =
            runTest(standardTestDispatcher) {
                val interactionConfig = InteractionFakeUtils.createFakeInteractionConfig(
                    eventSequence = listOf(
                        InteractionFakeUtils.createFakeInteractionEvent("event1"),
                        InteractionFakeUtils.createFakeInteractionEvent("localBlacklist1"),
                        InteractionFakeUtils.createFakeInteractionEvent("event2"),
                        InteractionFakeUtils.createFakeInteractionEvent("event3"),
                    ),
                )
                initMockInteractionManager(interactionConfig)
                val timeInNano = System.nanoTime()
                addEventWithNanoTimeFromBoot("event1", eventTimeInNano = timeInNano)
                advanceTimeBy(1.seconds)
                val interactionId1 = assertSingleOngoingInteraction(skipAdvancing = true)
                advanceTimeBy(10.seconds)
                assertSingleOngoingInteraction(interactionId1, skipAdvancing = true)
                addEventWithNanoTimeFromBoot("localBlacklist1", eventTimeInNano = timeInNano + 10)
                advanceTimeBy(10.seconds)
                assertSingleOngoingInteraction(interactionId1, skipAdvancing = true)
                // older than event1
                addEventWithNanoTimeFromBoot("event2", eventTimeInNano = timeInNano + 5)
                advanceTimeBy(5.seconds)
                assertSingleOngoingInteraction(interactionId1, skipAdvancing = true)

                addEventWithNanoTimeFromBoot("event3", eventTimeInNano = timeInNano + 5)
                advanceTimeBy(5.seconds)
                assertSingleFinalInteraction(interactionId1, skipAdvancing = true)

                // terminal state
                advanceTimeBy(30.seconds)
                assertSingleFinalInteraction(interactionId1, skipAdvancing = true)
            }
    }

    @Nested
    inner class `With timeout events` {
        @Test
        fun `event1, and then 20s delay gives error interaction`() =
            runTest(standardTestDispatcher) {
                val interactionConfig = InteractionFakeUtils.createFakeInteractionConfig(
                    eventSequence = listOf(
                        InteractionFakeUtils.createFakeInteractionEvent("event1"),
                        InteractionFakeUtils.createFakeInteractionEvent("event2"),
                    ),
                    thresholdInNanos = TimeUnit.SECONDS.toNanos(20)
                )

                initMockInteractionManager(interactionConfig)

                addEventWithNanoTimeFromBoot("event1")
                advanceTimeBy(20.seconds)
                assertSingleOngoingInteraction(skipAdvancing = true)
                advanceTimeBy(1.seconds)
                assertSingleFinalInteraction(skipAdvancing = true, isSuccess = false)
            }

        @Test
        fun `event1, and then 20s delay gives error interaction with event1 and event 2 gives final interaction`() =
            runTest(standardTestDispatcher) {
                val interactionConfig = InteractionFakeUtils.createFakeInteractionConfig(
                    eventSequence = listOf(
                        InteractionFakeUtils.createFakeInteractionEvent("event1"),
                        InteractionFakeUtils.createFakeInteractionEvent("event2"),
                    ),
                    thresholdInNanos = TimeUnit.SECONDS.toNanos(20)
                )

                initMockInteractionManager(interactionConfig)

                addEventWithNanoTimeFromBoot("event1")
                advanceTimeBy(20.seconds)
                assertSingleOngoingInteraction(skipAdvancing = true)
                advanceTimeBy(1.seconds)
                assertSingleFinalInteraction(skipAdvancing = true, isSuccess = false)

                addEventWithNanoTimeFromBoot("event1")
                advanceTimeBy(1.seconds)
                assertSingleOngoingInteraction(skipAdvancing = true)

                addEventWithNanoTimeFromBoot("event2")
                advanceTimeBy(1.seconds)
                assertSingleFinalInteraction(skipAdvancing = true)

                // terminal state
                advanceTimeBy(40.seconds)
                assertSingleFinalInteraction(skipAdvancing = true)
            }

        @Test
        fun `event1, event2 with after 20s delay doesn't give final interaction`() =
            runTest(standardTestDispatcher) {
                val interactionConfig = InteractionFakeUtils.createFakeInteractionConfig(
                    eventSequence = listOf(
                        InteractionFakeUtils.createFakeInteractionEvent("event1"),
                        InteractionFakeUtils.createFakeInteractionEvent("event2"),
                    ),
                    thresholdInNanos = TimeUnit.SECONDS.toNanos(20)
                )

                initMockInteractionManager(interactionConfig)

                addEventWithNanoTimeFromBoot("event1")
                advanceTimeBy(1.seconds)
                assertSingleOngoingInteraction(skipAdvancing = true)
                advanceTimeBy(20.seconds)
                assertSingleFinalInteraction(skipAdvancing = true, isSuccess = false)
                addEventWithNanoTimeFromBoot("event2")
                advanceTimeBy(1.seconds)
                assertSingleFinalInteraction(skipAdvancing = true, isSuccess = false)
            }

        @Test
        fun `event1, event2 with after 19s delay does give final interaction`() =
            runTest(standardTestDispatcher) {
                val interactionConfig = InteractionFakeUtils.createFakeInteractionConfig(
                    eventSequence = listOf(
                        InteractionFakeUtils.createFakeInteractionEvent("event1"),
                        InteractionFakeUtils.createFakeInteractionEvent("event2"),
                    ),
                    thresholdInNanos = TimeUnit.SECONDS.toNanos(20)
                )

                initMockInteractionManager(interactionConfig)

                addEventWithNanoTimeFromBoot("event1")
                advanceTimeBy(19.seconds)
                assertSingleOngoingInteraction(skipAdvancing = true)
                addEventWithNanoTimeFromBoot("event2")
                advanceTimeBy(1.seconds)
                assertSingleFinalInteraction(skipAdvancing = true)
            }

        @Test
        fun `event1, event2 with after 19s delay, event3 after 19s delay does give final interaction`() =
            runTest(standardTestDispatcher) {
                val interactionConfig = InteractionFakeUtils.createFakeInteractionConfig(
                    eventSequence = listOf(
                        InteractionFakeUtils.createFakeInteractionEvent("event1"),
                        InteractionFakeUtils.createFakeInteractionEvent("event2"),
                        InteractionFakeUtils.createFakeInteractionEvent("event3"),
                    ),
                    thresholdInNanos = TimeUnit.SECONDS.toNanos(20)
                )

                initMockInteractionManager(interactionConfig)

                addEventWithNanoTimeFromBoot("event1")
                advanceTimeBy(19.seconds)
                assertSingleOngoingInteraction(skipAdvancing = true)
                addEventWithNanoTimeFromBoot("event2")
                advanceTimeBy(19.seconds)
                assertSingleOngoingInteraction(skipAdvancing = true)
                addEventWithNanoTimeFromBoot("event3")
                advanceTimeBy(19.seconds)
                assertSingleFinalInteraction(skipAdvancing = true)

                // terminal state
                advanceTimeBy(40.seconds)
                assertSingleFinalInteraction(skipAdvancing = true)
            }

        @Test
        fun `event1, eventUnknown doesn't reset the timer`() =
            runTest(standardTestDispatcher) {
                val interactionConfig = InteractionFakeUtils.createFakeInteractionConfig(
                    eventSequence = listOf(
                        InteractionFakeUtils.createFakeInteractionEvent("event1"),
                        InteractionFakeUtils.createFakeInteractionEvent("event2"),
                    ),
                    thresholdInNanos = TimeUnit.SECONDS.toNanos(20)
                )

                initMockInteractionManager(interactionConfig)

                addEventWithNanoTimeFromBoot("event1")
                advanceTimeBy(18.seconds)
                assertSingleOngoingInteraction(skipAdvancing = true)
                addEventWithNanoTimeFromBoot("eventUnknown")
                advanceTimeBy(1.seconds)
                assertSingleOngoingInteraction(skipAdvancing = true)
                advanceTimeBy(5.seconds)
                assertSingleFinalInteraction(skipAdvancing = true, isSuccess = false)
            }
    }

    @Nested
    inner class `With wrong events` {
        @Test
        fun `with no ongoing match, events contained event doesn't give error interaction`() =
            runTest(standardTestDispatcher) {
                val interactionConfig = InteractionFakeUtils.createFakeInteractionConfig(
                    eventSequence = listOf(
                        InteractionFakeUtils.createFakeInteractionEvent("event1"),
                        InteractionFakeUtils.createFakeInteractionEvent("event2"),
                    ),
                )
                initMockInteractionManager(interactionConfig)

                addEventWithNanoTimeFromBoot("event2")
                assertSingleNoOngoingInteraction()
            }

        @Test
        fun `with ongoing match, event1 event3 gives error interaction`() =
            runTest(standardTestDispatcher) {
                val interactionConfig = InteractionFakeUtils.createFakeInteractionConfig(
                    eventSequence = listOf(
                        InteractionFakeUtils.createFakeInteractionEvent("event1"),
                        InteractionFakeUtils.createFakeInteractionEvent("event2"),
                        InteractionFakeUtils.createFakeInteractionEvent("event3"),
                    ),
                )
                initMockInteractionManager(interactionConfig)

                addEventWithNanoTimeFromBoot("event1")
                advanceTimeBy(1.seconds)
                val interactionId = assertSingleOngoingInteraction(skipAdvancing = true)

                addEventWithNanoTimeFromBoot("event3")
                advanceTimeBy(1.seconds)
                val (interactionId2, _) = assertSingleFinalInteraction(
                    skipAdvancing = true,
                    isSuccess = false
                )
                Assertions.assertThat(interactionId2).isEqualTo(interactionId)

                // terminal state
                advanceTimeBy(40.seconds)
                val (interactionId3, _) = assertSingleFinalInteraction(
                    skipAdvancing = true,
                    isSuccess = false
                )
                Assertions.assertThat(interactionId3).isEqualTo(interactionId)
            }

        @Test
        fun `after error interaction success interaction is made`() =
            runTest(standardTestDispatcher) {
                val interactionConfig = InteractionFakeUtils.createFakeInteractionConfig(
                    eventSequence = listOf(
                        InteractionFakeUtils.createFakeInteractionEvent("event1"),
                        InteractionFakeUtils.createFakeInteractionEvent("event2"),
                        InteractionFakeUtils.createFakeInteractionEvent("event3"),
                    ),
                )
                initMockInteractionManager(interactionConfig)

                addEventWithNanoTimeFromBoot("event1")
                advanceTimeBy(1.seconds)

                addEventWithNanoTimeFromBoot("event3")
                advanceTimeBy(1.seconds)

                val (failedInteractionId, _) = assertSingleFinalInteraction(isSuccess = false)

                addEventWithNanoTimeFromBoot("event1")
                advanceTimeBy(1.seconds)
                addEventWithNanoTimeFromBoot("event2")
                advanceTimeBy(1.seconds)
                addEventWithNanoTimeFromBoot("event3")
                advanceTimeBy(1.seconds)
                val (successInteractionId, _) = assertSingleFinalInteraction()
                Assertions.assertThat(successInteractionId).isNotEqualTo(failedInteractionId)
            }
    }

    private fun TestScope.initMockInteractionManager(vararg interactionConfigs: InteractionConfig) {
        coEvery { mockApiService.getInteractions() } returns interactionConfigs.toList()
        mockInteractionManager.init()
        advanceUntilIdle()
    }

    private fun TestScope.assertSingleNoOngoingInteraction(
        skipAdvancing: Boolean = false,
    ): InteractionRunningStatus? {
        if (!skipAdvancing) advanceTimeBy(1.seconds)
        Assertions
            .assertThat(mockInteractionManager.interactionTrackerStatesState.value)
            .hasSize(1)
            .first()
            .isInstanceOf(InteractionRunningStatus.NoOngoingMatch::class.java)
            .isNotNull

        return (mockInteractionManager.interactionTrackerStatesState.value.first() as InteractionRunningStatus.NoOngoingMatch).oldOngoingInteractionRunningStatus
    }

    private inline fun <reified M : InteractionRunningStatus> TestScope.assertAllInteraction(
        size: Int? = null,
        skipAdvancing: Boolean = false,
    ): Int {
        if (!skipAdvancing) advanceTimeBy(1.seconds)
        val listAssertions =
            Assertions.assertThat(mockInteractionManager.interactionTrackerStatesState.value)
                .hasOnlyElementsOfType(M::class.java)

        if (M::class.java == InteractionRunningStatus.OngoingMatch::class.java) {
            listAssertions.extracting<String> { (it as InteractionRunningStatus.OngoingMatch).interactionId }
                .doesNotHaveDuplicates()
        }

        size?.let {
            listAssertions.hasSize(size)
        }

        return mockInteractionManager.interactionTrackerStatesState.value.size
    }

    private fun TestScope.assertSingleOngoingInteraction(
        previousIdToMatch: String? = null,
        skipAdvancing: Boolean = false,
    ): String {
        if (!skipAdvancing) advanceTimeBy(1.seconds)
        Assertions
            .assertThat(mockInteractionManager.interactionTrackerStatesState.value)
            .hasSize(1)
            .first()
            .isInstanceOf(InteractionRunningStatus.OngoingMatch::class.java)
            .extracting { (it as InteractionRunningStatus.OngoingMatch).interaction }
            .isNull()

        previousIdToMatch?.let {
            Assertions.assertThat(
                (mockInteractionManager.interactionTrackerStatesState.value[0] as InteractionRunningStatus.OngoingMatch).interactionId
            ).isEqualTo(it)
        }

        return (mockInteractionManager.interactionTrackerStatesState.value.first() as InteractionRunningStatus.OngoingMatch).interactionId
    }

    private fun TestScope.assertSingleFinalInteraction(
        previousIdToMatch: String? = null,
        skipAdvancing: Boolean = false,
        isSuccess: Boolean = true,
    ): Pair<String, Interaction> {
        if (!skipAdvancing) advanceTimeBy(1.seconds)
        Assertions
            .assertThat(mockInteractionManager.interactionTrackerStatesState.value)
            .hasSize(1)
            .first()
            .isInstanceOf(InteractionRunningStatus.OngoingMatch::class.java)
            .extracting { (it as InteractionRunningStatus.OngoingMatch).interaction }
            .isNotNull

        val finalInteractionOngoingStatus =
            mockInteractionManager.interactionTrackerStatesState.value.first() as InteractionRunningStatus.OngoingMatch
        val interaction =
            finalInteractionOngoingStatus.interaction ?: error("Interaction should not be null")
        Assertions.assertThat(finalInteractionOngoingStatus.interactionId)
            .isEqualTo(interaction.id)
        Assertions.assertThat(interaction.isErrored)
            .isEqualTo(!isSuccess)
        previousIdToMatch?.let {
            Assertions.assertThat(finalInteractionOngoingStatus.interactionId).isEqualTo(it)
        }
        @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
        return finalInteractionOngoingStatus.interactionId to interaction
    }

    /**
     * This method ensures that nano time is used
     */
    private fun addEventWithNanoTimeFromBoot(
        eventName: String,
        params: Map<String, Any?> = emptyMap(),
        eventTimeInNano: Long? = null,
    ) {
        mockInteractionManager.addEvent(
            eventName = eventName,
            eventTimeInNano = eventTimeInNano ?: System.nanoTime(),
            params = params,
        )
    }
}
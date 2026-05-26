package io.opentelemetry.android.instrumentation.slowrendering

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

internal class FrameDataHelperTest {
    @Test
    fun `returns zero when no events exist`() {
        val events = emptyList<FrameDataHelper.CumulativeFrameData>()

        val result =
            FrameDataHelper.createCumulativeFrameMetric(
                events = events,
                startTimeInMs = 0,
                endTimeInMs = 1000,
            )

        assertThat(result).isNull()
    }

    @Test
    fun `exact timestamp match returns correct delta`() {
        val events =
            listOf(
                FrameDataHelper.CumulativeFrameData(1000, 10, 5, 1, 0),
                FrameDataHelper.CumulativeFrameData(2000, 30, 15, 2, 1),
            )

        val result =
            FrameDataHelper.createCumulativeFrameMetric(
                events = events,
                startTimeInMs = 1000,
                endTimeInMs = 2000,
            )

        assertThat(result).isNotNull()
        assertThat(result!!)
            .extracting(
                FrameDataHelper.CumulativeFrameData::analysedFrameCount,
                FrameDataHelper.CumulativeFrameData::unanalysedFrameCount,
                FrameDataHelper.CumulativeFrameData::slowFrameCount,
                FrameDataHelper.CumulativeFrameData::frozenFrameCount,
            ).containsExactly(20L, 10L, 1L, 0L)
    }

    @Test
    fun `slight increased end timestamp match returns correct delta`() {
        val events =
            listOf(
                FrameDataHelper.CumulativeFrameData(1000, 10, 5, 1, 0),
                FrameDataHelper.CumulativeFrameData(2000, 30, 15, 2, 1),
            )

        val result =
            FrameDataHelper.createCumulativeFrameMetric(
                events = events,
                startTimeInMs = 1000,
                endTimeInMs = 2001,
            )

        assertThat(result).isNotNull()
        assertThat(result!!)
            .extracting(
                FrameDataHelper.CumulativeFrameData::analysedFrameCount,
                FrameDataHelper.CumulativeFrameData::unanalysedFrameCount,
                FrameDataHelper.CumulativeFrameData::slowFrameCount,
                FrameDataHelper.CumulativeFrameData::frozenFrameCount,
            ).containsExactly(20L, 10L, 2L, 1L)
    }

    @Test
    fun `linearly interpolates when end timestamp is missing`() {
        val events =
            listOf(
                FrameDataHelper.CumulativeFrameData(1000, 10, 10, 1, 0),
                FrameDataHelper.CumulativeFrameData(2000, 30, 30, 2, 1),
            )

        val result =
            FrameDataHelper.createCumulativeFrameMetric(
                events = events,
                startTimeInMs = 0,
                endTimeInMs = 1500,
            )

        assertThat(result).isNotNull()
        assertThat(result!!)
            .extracting(
                FrameDataHelper.CumulativeFrameData::analysedFrameCount,
                FrameDataHelper.CumulativeFrameData::unanalysedFrameCount,
                FrameDataHelper.CumulativeFrameData::slowFrameCount,
                FrameDataHelper.CumulativeFrameData::frozenFrameCount,
            ).containsExactly(30L, 30L, 1L, 0L)
    }

    @Test
    fun `interpolates both start and end timestamps`() {
        val events =
            listOf(
                FrameDataHelper.CumulativeFrameData(1000, 10, 10, 1, 0),
                FrameDataHelper.CumulativeFrameData(3000, 50, 50, 3, 2),
            )

        val result =
            FrameDataHelper.createCumulativeFrameMetric(
                events = events,
                startTimeInMs = 1500,
                endTimeInMs = 2500,
            )

        assertThat(result).isNotNull()
        assertThat(result!!)
            .extracting(
                FrameDataHelper.CumulativeFrameData::analysedFrameCount,
                FrameDataHelper.CumulativeFrameData::unanalysedFrameCount,
                FrameDataHelper.CumulativeFrameData::slowFrameCount,
                FrameDataHelper.CumulativeFrameData::frozenFrameCount,
            ).containsExactly(20L, 20L, 0L, 0L)
    }

    @Test
    fun `uses last known value when no future event exists when single slow event lies between the range`() {
        val events =
            listOf(
                FrameDataHelper.CumulativeFrameData(1000, 10, 5, 1, 0),
            )

        val result =
            FrameDataHelper.createCumulativeFrameMetric(
                events = events,
                startTimeInMs = 0,
                endTimeInMs = 5000,
            )

        assertThat(result)
            .usingRecursiveComparison()
            .isEqualTo(
                FrameDataHelper.CumulativeFrameData(
                    timeInMs = 5000,
                    analysedFrameCount = 1,
                    unanalysedFrameCount = 0,
                    slowFrameCount = 1,
                    frozenFrameCount = 0,
                ),
            )
    }

    @Test
    fun `uses last known value when no future event exists when single frozen event lies between the range`() {
        val events =
            listOf(
                FrameDataHelper.CumulativeFrameData(1000, 10, 5, 0, 1),
            )

        val result =
            FrameDataHelper.createCumulativeFrameMetric(
                events = events,
                startTimeInMs = 0,
                endTimeInMs = 5000,
            )

        assertThat(result)
            .usingRecursiveComparison()
            .isEqualTo(
                FrameDataHelper.CumulativeFrameData(
                    timeInMs = 5000,
                    analysedFrameCount = 1,
                    unanalysedFrameCount = 0,
                    slowFrameCount = 0,
                    frozenFrameCount = 1,
                ),
            )
    }

    @Test
    fun `uses last known value when no future event exists when single normal event lies between the range`() {
        val events =
            listOf(
                FrameDataHelper.CumulativeFrameData(1000, 10, 5, 0, 0),
            )

        val result =
            FrameDataHelper.createCumulativeFrameMetric(
                events = events,
                startTimeInMs = 0,
                endTimeInMs = 5000,
            )

        assertThat(result)
            .usingRecursiveComparison()
            .isEqualTo(
                FrameDataHelper.CumulativeFrameData(
                    timeInMs = 5000,
                    analysedFrameCount = 1,
                    unanalysedFrameCount = 0,
                    slowFrameCount = 0,
                    frozenFrameCount = 0,
                ),
            )
    }

    @Test
    fun `returns null when single event lies after the range`() {
        val events =
            listOf(
                FrameDataHelper.CumulativeFrameData(6000, 10, 5, 1, 0),
            )

        val result =
            FrameDataHelper.createCumulativeFrameMetric(
                events = events,
                startTimeInMs = 0,
                endTimeInMs = 5000,
            )

        assertThat(result).isNull()
    }

    @Test
    fun `returns null when single event lies before the range`() {
        val events =
            listOf(
                FrameDataHelper.CumulativeFrameData(1000, 10, 5, 1, 0),
            )

        val result =
            FrameDataHelper.createCumulativeFrameMetric(
                events = events,
                startTimeInMs = 2000,
                endTimeInMs = 5000,
            )

        assertThat(result).isNull()
    }

    @Test
    fun `when startTime is before all events with endtime matches the last event, interpolates from inner two values`() {
        val events =
            listOf(
                FrameDataHelper.CumulativeFrameData(1000, 10, 5, 1, 0),
                FrameDataHelper.CumulativeFrameData(2000, 30, 15, 2, 1),
            )

        val result =
            FrameDataHelper.createCumulativeFrameMetric(
                events = events,
                startTimeInMs = 0,
                endTimeInMs = 2000,
            )

        assertThat(result)
            .usingRecursiveComparison()
            .isEqualTo(
                FrameDataHelper.CumulativeFrameData(
                    timeInMs = 2000,
                    analysedFrameCount = 40,
                    unanalysedFrameCount = 20,
                    slowFrameCount = 1,
                    frozenFrameCount = 0,
                ),
            )
    }

    @Test
    fun `when startTime is before all events with endtime is slightly more than the last event time, interpolates from inner two values`() {
        val events =
            listOf(
                FrameDataHelper.CumulativeFrameData(1000, 10, 5, 1, 0),
                FrameDataHelper.CumulativeFrameData(2000, 30, 15, 2, 1),
            )

        val result =
            FrameDataHelper.createCumulativeFrameMetric(
                events = events,
                startTimeInMs = 0,
                endTimeInMs = 2010,
            )

        assertThat(result)
            .usingRecursiveComparison()
            .isEqualTo(
                FrameDataHelper.CumulativeFrameData(
                    timeInMs = 2010,
                    analysedFrameCount = 40,
                    unanalysedFrameCount = 20,
                    slowFrameCount = 2,
                    frozenFrameCount = 1,
                ),
            )
    }

    @Test
    fun `interpolates from previous two values when target start matches the second event timestamp`() {
        val events =
            listOf(
                FrameDataHelper.CumulativeFrameData(1000, 10, 5, 3, 1),
                FrameDataHelper.CumulativeFrameData(2000, 20, 10, 6, 2),
            )

        val result =
            FrameDataHelper.createCumulativeFrameMetric(
                events = events,
                startTimeInMs = 2000,
                endTimeInMs = 3000,
            )

        assertThat(result)
            .usingRecursiveComparison()
            .isEqualTo(
                FrameDataHelper.CumulativeFrameData(
                    timeInMs = 3000,
                    analysedFrameCount = 10,
                    unanalysedFrameCount = 5,
                    slowFrameCount = 3,
                    frozenFrameCount = 1,
                ),
            )
    }

    @Test
    fun `interpolates from previous two values when target start does not match the second event timestamp`() {
        val events =
            listOf(
                FrameDataHelper.CumulativeFrameData(1000, 10, 5, 3, 1),
                FrameDataHelper.CumulativeFrameData(2000, 20, 10, 6, 2),
            )

        val result =
            FrameDataHelper.createCumulativeFrameMetric(
                events = events,
                startTimeInMs = 3000,
                endTimeInMs = 4000,
            )

        assertThat(result)
            .usingRecursiveComparison()
            .isEqualTo(
                FrameDataHelper.CumulativeFrameData(
                    timeInMs = 4000,
                    analysedFrameCount = 10,
                    unanalysedFrameCount = 5,
                    slowFrameCount = 0,
                    frozenFrameCount = 0,
                ),
            )
    }

    @Test
    fun `interpolates from previous two values when three values are there`() {
        val events =
            listOf(
                FrameDataHelper.CumulativeFrameData(1000, 10, 5, 3, 1),
                FrameDataHelper.CumulativeFrameData(2000, 20, 10, 6, 2),
                FrameDataHelper.CumulativeFrameData(3000, 80, 10, 6, 2),
            )

        val result =
            FrameDataHelper.createCumulativeFrameMetric(
                events = events,
                startTimeInMs = 3000,
                endTimeInMs = 4000,
            )

        assertThat(result)
            .usingRecursiveComparison()
            .isEqualTo(
                FrameDataHelper.CumulativeFrameData(
                    timeInMs = 4000,
                    analysedFrameCount = 60,
                    unanalysedFrameCount = 0,
                    slowFrameCount = 0,
                    frozenFrameCount = 0,
                ),
            )
    }

    @Test
    fun `interpolates from previous two values when three values are there and slow and frozen frames are increasing`() {
        val events =
            listOf(
                FrameDataHelper.CumulativeFrameData(1000, 10, 5, 3, 1),
                FrameDataHelper.CumulativeFrameData(2000, 20, 10, 6, 2),
                FrameDataHelper.CumulativeFrameData(3000, 80, 10, 9, 3),
            )

        val result =
            FrameDataHelper.createCumulativeFrameMetric(
                events = events,
                startTimeInMs = 4000,
                endTimeInMs = 5000,
            )

        assertThat(result)
            .usingRecursiveComparison()
            .isEqualTo(
                FrameDataHelper.CumulativeFrameData(
                    timeInMs = 5000,
                    analysedFrameCount = 60,
                    unanalysedFrameCount = 0,
                    slowFrameCount = 0,
                    frozenFrameCount = 0,
                ),
            )
    }

    @Test
    fun `interpolates from next two values`() {
        val events =
            listOf(
                FrameDataHelper.CumulativeFrameData(2000, 10, 5, 3, 1),
                FrameDataHelper.CumulativeFrameData(3000, 20, 10, 6, 2),
            )

        val result =
            FrameDataHelper.createCumulativeFrameMetric(
                events = events,
                startTimeInMs = 0,
                endTimeInMs = 1000,
            )

        assertThat(result)
            .usingRecursiveComparison()
            .isEqualTo(
                FrameDataHelper.CumulativeFrameData(
                    timeInMs = 1000,
                    analysedFrameCount = 10,
                    unanalysedFrameCount = 5,
                    slowFrameCount = 0,
                    frozenFrameCount = 0,
                ),
            )
    }

    @Test
    fun `interpolates from next two values when three values are present #1`() {
        val events =
            listOf(
                FrameDataHelper.CumulativeFrameData(2000, 10, 5, 3, 1),
                FrameDataHelper.CumulativeFrameData(3000, 20, 20, 6, 2),
                FrameDataHelper.CumulativeFrameData(4000, 80, 40, 6, 2),
            )

        val result =
            FrameDataHelper.createCumulativeFrameMetric(
                events = events,
                startTimeInMs = 0,
                endTimeInMs = 1000,
            )

        assertThat(result)
            .usingRecursiveComparison()
            .isEqualTo(
                FrameDataHelper.CumulativeFrameData(
                    timeInMs = 1000,
                    analysedFrameCount = 10,
                    unanalysedFrameCount = 15,
                    slowFrameCount = 0,
                    frozenFrameCount = 0,
                ),
            )
    }

    @Test
    fun `interpolates from next two values when three values are present #2`() {
        val events =
            listOf(
                FrameDataHelper.CumulativeFrameData(4500, 10, 5, 3, 1),
                FrameDataHelper.CumulativeFrameData(5500, 20, 20, 6, 2),
                FrameDataHelper.CumulativeFrameData(6500, 80, 40, 6, 2),
            )

        val result =
            FrameDataHelper.createCumulativeFrameMetric(
                events = events,
                startTimeInMs = 0,
                endTimeInMs = 1000,
            )

        assertThat(result)
            .usingRecursiveComparison()
            .isEqualTo(
                FrameDataHelper.CumulativeFrameData(
                    timeInMs = 1000,
                    analysedFrameCount = 10,
                    unanalysedFrameCount = 15,
                    slowFrameCount = 0,
                    frozenFrameCount = 0,
                ),
            )
    }

    @Test
    fun `interpolates from intersecting two values`() {
        val events =
            listOf(
                FrameDataHelper.CumulativeFrameData(2000, 10, 5, 3, 1),
                FrameDataHelper.CumulativeFrameData(3000, 20, 10, 6, 2),
            )

        val result =
            FrameDataHelper.createCumulativeFrameMetric(
                events = events,
                startTimeInMs = 2500,
                endTimeInMs = 3500,
            )

        assertThat(result)
            .usingRecursiveComparison()
            .isEqualTo(
                FrameDataHelper.CumulativeFrameData(
                    timeInMs = 3500,
                    analysedFrameCount = 10,
                    unanalysedFrameCount = 5,
                    slowFrameCount = 3,
                    frozenFrameCount = 1,
                ),
            )
    }

    @Test
    fun `when intersecting and inner is present choose inner`() {
        val events =
            listOf(
                FrameDataHelper.CumulativeFrameData(2000, 10, 5, 3, 1),
                FrameDataHelper.CumulativeFrameData(3000, 20, 10, 6, 2),
                FrameDataHelper.CumulativeFrameData(4000, 40, 20, 6, 2),
            )

        val result =
            FrameDataHelper.createCumulativeFrameMetric(
                events = events,
                startTimeInMs = 2500,
                endTimeInMs = 5500,
            )

        assertThat(result)
            .usingRecursiveComparison()
            .isEqualTo(
                FrameDataHelper.CumulativeFrameData(
                    timeInMs = 5500,
                    analysedFrameCount = 60,
                    unanalysedFrameCount = 30,
                    slowFrameCount = 3,
                    frozenFrameCount = 1,
                ),
            )
    }

    @Test
    fun `interpolates from outside two values`() {
        val events =
            listOf(
                FrameDataHelper.CumulativeFrameData(1000, 10, 5, 3, 1),
                FrameDataHelper.CumulativeFrameData(5000, 30, 15, 6, 2),
            )

        val result =
            FrameDataHelper.createCumulativeFrameMetric(
                events = events,
                startTimeInMs = 2000,
                endTimeInMs = 3000,
            )

        assertThat(result)
            .usingRecursiveComparison()
            .isEqualTo(
                FrameDataHelper.CumulativeFrameData(
                    timeInMs = 3000,
                    analysedFrameCount = 5,
                    unanalysedFrameCount = 2,
                    slowFrameCount = 0,
                    frozenFrameCount = 0,
                ),
            )
    }

    @Test
    fun `interpolates from outside two values when inner values are present`() {
        val events =
            listOf(
                FrameDataHelper.CumulativeFrameData(1000, 10, 5, 3, 1),
                FrameDataHelper.CumulativeFrameData(2100, 11, 6, 4, 1),
                FrameDataHelper.CumulativeFrameData(2600, 12, 6, 5, 1),
                FrameDataHelper.CumulativeFrameData(5000, 30, 15, 6, 2),
            )

        val result =
            FrameDataHelper.createCumulativeFrameMetric(
                events = events,
                startTimeInMs = 2000,
                endTimeInMs = 3000,
            )

        assertThat(result)
            .usingRecursiveComparison()
            .isEqualTo(
                FrameDataHelper.CumulativeFrameData(
                    timeInMs = 3000,
                    analysedFrameCount = 5,
                    unanalysedFrameCount = 2,
                    slowFrameCount = 2,
                    frozenFrameCount = 0,
                ),
            )
    }

    @Test
    fun `interpolates from immediate outside two values when inner values are present and two outer values are present`() {
        val events =
            listOf(
                FrameDataHelper.CumulativeFrameData(500, 8, 4, 3, 1),
                FrameDataHelper.CumulativeFrameData(1000, 10, 5, 3, 1),
                FrameDataHelper.CumulativeFrameData(2100, 11, 6, 4, 1),
                FrameDataHelper.CumulativeFrameData(2600, 12, 6, 5, 1),
                FrameDataHelper.CumulativeFrameData(5000, 30, 15, 6, 2),
                FrameDataHelper.CumulativeFrameData(5500, 32, 16, 6, 2),
            )

        val result =
            FrameDataHelper.createCumulativeFrameMetric(
                events = events,
                startTimeInMs = 2000,
                endTimeInMs = 3000,
            )

        assertThat(result)
            .usingRecursiveComparison()
            .isEqualTo(
                FrameDataHelper.CumulativeFrameData(
                    timeInMs = 3000,
                    analysedFrameCount = 5,
                    unanalysedFrameCount = 2,
                    slowFrameCount = 2,
                    frozenFrameCount = 0,
                ),
            )
    }

    @Test
    fun `interpolates from largest range when multiple inner values are present`() {
        val events =
            listOf(
                FrameDataHelper.CumulativeFrameData(1000, 10, 5, 3, 1),
                FrameDataHelper.CumulativeFrameData(2100, 11, 6, 4, 1),
                FrameDataHelper.CumulativeFrameData(2600, 12, 6, 5, 1),
                FrameDataHelper.CumulativeFrameData(5000, 30, 15, 6, 2),
            )

        val result =
            FrameDataHelper.createCumulativeFrameMetric(
                events = events,
                startTimeInMs = 0,
                endTimeInMs = 6000,
            )

        assertThat(result)
            .usingRecursiveComparison()
            .isEqualTo(
                FrameDataHelper.CumulativeFrameData(
                    timeInMs = 6000,
                    analysedFrameCount = 30,
                    unanalysedFrameCount = 15,
                    slowFrameCount = 6,
                    frozenFrameCount = 2,
                ),
            )
    }

    @Test
    fun `interpolates from outside two values even if previous and next two values are present`() {
        val events =
            listOf(
                FrameDataHelper.CumulativeFrameData(1000, 10, 5, 3, 1),
                FrameDataHelper.CumulativeFrameData(2000, 20, 6, 3, 1),
                FrameDataHelper.CumulativeFrameData(6000, 100, 6, 3, 1),
                FrameDataHelper.CumulativeFrameData(7000, 110, 7, 3, 1),
            )

        val result =
            FrameDataHelper.createCumulativeFrameMetric(
                events = events,
                startTimeInMs = 3000,
                endTimeInMs = 4000,
            )

        assertThat(result)
            .usingRecursiveComparison()
            .isEqualTo(
                FrameDataHelper.CumulativeFrameData(
                    timeInMs = 4000,
                    analysedFrameCount = 20,
                    unanalysedFrameCount = 0,
                    slowFrameCount = 0,
                    frozenFrameCount = 0,
                ),
            )
    }

    @Test
    fun `when start time and end time in ms are same then returns null`() {
        val events =
            listOf(
                FrameDataHelper.CumulativeFrameData(1000, 10, 5, 3, 1),
                FrameDataHelper.CumulativeFrameData(2000, 20, 6, 3, 1),
                FrameDataHelper.CumulativeFrameData(6000, 100, 6, 3, 1),
                FrameDataHelper.CumulativeFrameData(7000, 110, 7, 3, 1),
            )

        val result =
            FrameDataHelper.createCumulativeFrameMetric(
                events = events,
                startTimeInMs = 3000,
                endTimeInMs = 3000,
            )

        assertThat(result).isNull()
    }

    @Test
    fun `does not throw when span lies in gap between two frame samples`() {
        // Regression: span entirely between e1 and e2 hit startBefore branch with
        // events.last { < startBefore } when startBefore was the first sample → NSEE.
        val events =
            listOf(
                FrameDataHelper.CumulativeFrameData(1000, 10, 5, 1, 0),
                FrameDataHelper.CumulativeFrameData(2000, 30, 15, 2, 1),
            )

        val result =
            FrameDataHelper.createCumulativeFrameMetric(
                events = events,
                startTimeInMs = 1500,
                endTimeInMs = 1800,
            )

        assertThat(result).isNotNull()
        assertThat(result!!)
            .extracting(
                FrameDataHelper.CumulativeFrameData::analysedFrameCount,
                FrameDataHelper.CumulativeFrameData::unanalysedFrameCount,
            ).containsExactly(6L, 3L)
    }

    @Test
    fun `does not throw when end anchor is the last frame sample`() {
        val events =
            listOf(
                FrameDataHelper.CumulativeFrameData(1000, 10, 5, 1, 0),
                FrameDataHelper.CumulativeFrameData(2000, 30, 15, 2, 1),
            )

        val result =
            FrameDataHelper.createCumulativeFrameMetric(
                events = events,
                startTimeInMs = 2100,
                endTimeInMs = 2200,
            )

        assertThat(result).isNotNull()
        assertThat(result!!.analysedFrameCount).isEqualTo(2L)
    }

    @Test
    fun `concurrent writes and reads do not throw ConcurrentModificationException`() {
        // Regression: FrameDataHelper.frameDataEvents was iterated by readers on
        // arbitrary threads (span-end processors) while the FrameMetrics thread
        // mutated it, throwing CME. Snapshotting under FrameDataHelper.lock fixes it.
        synchronized(FrameDataHelper.lock) {
            FrameDataHelper.frameDataEvents.clear()
            FrameDataHelper.totalAnalysedFrames = 0
            FrameDataHelper.totalUnanalysedDroppedFrames = 0
        }

        val durationMs = 750L
        val deadline = System.currentTimeMillis() + durationMs
        val failure = AtomicReference<Throwable?>(null)
        val started = CountDownLatch(2)

        val writer =
            Thread {
                started.countDown()
                var t = 0L
                while (System.currentTimeMillis() < deadline && failure.get() == null) {
                    try {
                        synchronized(FrameDataHelper.lock) {
                            if (FrameDataHelper.frameDataEvents.size > FrameDataHelper.FRAME_EVENTS_MAX_COUNT) {
                                FrameDataHelper.frameDataEvents.removeFirst()
                            }
                            val last = FrameDataHelper.frameDataEvents.lastOrNull()
                            FrameDataHelper.frameDataEvents.add(
                                FrameDataHelper.CumulativeFrameData(
                                    timeInMs = t++,
                                    analysedFrameCount = (last?.analysedFrameCount ?: 0) + 1,
                                    unanalysedFrameCount = last?.unanalysedFrameCount ?: 0,
                                    slowFrameCount = last?.slowFrameCount ?: 0,
                                    frozenFrameCount = last?.frozenFrameCount ?: 0,
                                ),
                            )
                        }
                    } catch (e: Throwable) {
                        failure.compareAndSet(null, e)
                    }
                }
            }

        val reader =
            Thread {
                started.countDown()
                while (System.currentTimeMillis() < deadline && failure.get() == null) {
                    try {
                        FrameDataHelper.createCumulativeFrameMetric(
                            startTimeInMs = 0,
                            endTimeInMs = Long.MAX_VALUE,
                        )
                    } catch (e: Throwable) {
                        failure.compareAndSet(null, e)
                    }
                }
            }

        writer.start()
        reader.start()
        started.await(2, TimeUnit.SECONDS)
        writer.join(durationMs + 2000)
        reader.join(durationMs + 2000)

        synchronized(FrameDataHelper.lock) {
            FrameDataHelper.frameDataEvents.clear()
            FrameDataHelper.totalAnalysedFrames = 0
            FrameDataHelper.totalUnanalysedDroppedFrames = 0
        }

        assertThat(failure.get()).isNull()
    }
}

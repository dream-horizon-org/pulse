package io.opentelemetry.android.instrumentation.slowrendering

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

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

        assertThat(result).isEqualTo(
            FrameDataHelper.CumulativeFrameData(
                timeInMs = 1000,
                analysedFrameCount = 0,
                unanalysedFrameCount = 0,
                slowFrameCount = 0,
                frozenFrameCount = 0,
            ),
        )
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

        assertThat(result)
            .extracting(
                FrameDataHelper.CumulativeFrameData::analysedFrameCount,
                FrameDataHelper.CumulativeFrameData::unanalysedFrameCount,
                FrameDataHelper.CumulativeFrameData::slowFrameCount,
                FrameDataHelper.CumulativeFrameData::frozenFrameCount,
            ).containsExactly(20L, 10L, 1L, 1L)
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

        assertThat(result.analysedFrameCount).isEqualTo(20)
        assertThat(result.unanalysedFrameCount).isEqualTo(20)
        assertThat(result.slowFrameCount).isEqualTo(1)
        assertThat(result.frozenFrameCount).isEqualTo(0)
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

        assertThat(result)
            .extracting(
                FrameDataHelper.CumulativeFrameData::analysedFrameCount,
                FrameDataHelper.CumulativeFrameData::unanalysedFrameCount,
                FrameDataHelper.CumulativeFrameData::slowFrameCount,
                FrameDataHelper.CumulativeFrameData::frozenFrameCount,
            ).containsExactly(20L, 20L, 0L, 0L)
    }

    @Test
    fun `uses last known value when no future event exists`() {
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
                    analysedFrameCount = 10,
                    unanalysedFrameCount = 5,
                    slowFrameCount = 1,
                    frozenFrameCount = 0,
                ),
            )
    }
}

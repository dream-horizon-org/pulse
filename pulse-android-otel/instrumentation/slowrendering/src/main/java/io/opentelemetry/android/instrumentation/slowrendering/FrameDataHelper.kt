package io.opentelemetry.android.instrumentation.slowrendering

internal object FrameDataHelper {
    internal val frameDataEvents = ArrayDeque<CumulativeFrameData>()
    internal var totalAnalysedFrames: Long = 0
    internal var totalUnanalysedDroppedFrames: Long = 0
    internal const val FRAME_EVENTS_MAX_COUNT = 8000

    internal data class CumulativeFrameData(
        val timeInMs: Long,
        val analysedFrameCount: Long,
        val unanalysedFrameCount: Long,
        val slowFrameCount: Long,
        val frozenFrameCount: Long,
    )

    private fun linearInterpolate(
        startValue: Long,
        endValue: Long,
        startTime: Long,
        endTime: Long,
        targetTime: Long,
    ): Long {
        if (endTime == startTime) return startValue

        val ratio =
            (targetTime - startTime).toDouble() /
                (endTime - startTime).toDouble()

        return (startValue + (endValue - startValue) * ratio).toLong()
    }

    private fun interpolateLinear(
        before: CumulativeFrameData,
        after: CumulativeFrameData,
        targetTime: Long,
    ): CumulativeFrameData =
        CumulativeFrameData(
            timeInMs = targetTime,
            analysedFrameCount =
                linearInterpolate(
                    before.analysedFrameCount,
                    after.analysedFrameCount,
                    before.timeInMs,
                    after.timeInMs,
                    targetTime,
                ),
            unanalysedFrameCount =
                linearInterpolate(
                    before.unanalysedFrameCount,
                    after.unanalysedFrameCount,
                    before.timeInMs,
                    after.timeInMs,
                    targetTime,
                ),
            slowFrameCount = before.slowFrameCount,
            frozenFrameCount = before.frozenFrameCount,
        )

    private fun cumulativeAt(
        events: List<CumulativeFrameData>,
        timeInMs: Long,
    ): CumulativeFrameData? {
        val before = events.lastOrNull { it.timeInMs <= timeInMs }
        val after = events.firstOrNull { it.timeInMs > timeInMs }

        return when {
            before != null && before.timeInMs == timeInMs -> before

            before != null && after != null -> interpolateLinear(before, after, timeInMs)

            before != null -> before

            else -> null
        }
    }

    internal fun createCumulativeFrameMetric(
        startTimeInMs: Long,
        endTimeInMs: Long,
        events: List<CumulativeFrameData> = frameDataEvents,
    ): CumulativeFrameData {
        val start = cumulativeAt(events, startTimeInMs)
        val end =
            cumulativeAt(events, endTimeInMs)
                ?: return CumulativeFrameData(
                    timeInMs = endTimeInMs,
                    analysedFrameCount = 0,
                    unanalysedFrameCount = 0,
                    slowFrameCount = 0,
                    frozenFrameCount = 0,
                )

        return CumulativeFrameData(
            timeInMs = endTimeInMs,
            analysedFrameCount =
                end.analysedFrameCount - (start?.analysedFrameCount ?: 0),
            unanalysedFrameCount =
                end.unanalysedFrameCount - (start?.unanalysedFrameCount ?: 0),
            slowFrameCount =
                end.slowFrameCount - (start?.slowFrameCount ?: 0),
            frozenFrameCount =
                end.frozenFrameCount - (start?.frozenFrameCount ?: 0),
        )
    }
}

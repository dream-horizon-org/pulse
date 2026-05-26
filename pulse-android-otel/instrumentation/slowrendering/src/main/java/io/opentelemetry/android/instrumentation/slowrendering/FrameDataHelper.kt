package io.opentelemetry.android.instrumentation.slowrendering

internal object FrameDataHelper {
    // Guards all access to frameDataEvents and the two running totals below.
    // Writers run on the FrameMetricsCollector HandlerThread; readers run on
    // whatever thread ends a span (e.g. OkHttp dispatcher), so iteration must
    // happen on a snapshot taken under this lock to avoid ConcurrentModificationException.
    internal val lock = Any()
    internal val frameDataEvents = ArrayDeque<CumulativeFrameData>()
    internal var totalAnalysedFrames: Long = 0
    internal var totalUnanalysedDroppedFrames: Long = 0
    internal const val FRAME_EVENTS_MAX_COUNT = 8000

    internal fun snapshotEvents(): List<CumulativeFrameData> = synchronized(lock) { frameDataEvents.toList() }

    internal data class CumulativeFrameData(
        val timeInMs: Long,
        val analysedFrameCount: Long,
        val unanalysedFrameCount: Long,
        val slowFrameCount: Long,
        val frozenFrameCount: Long,
    )

    private fun interpolateLinear(
        before: CumulativeFrameData,
        after: CumulativeFrameData,
        targetStartTimeInMs: Long,
        targetEndTimeInMs: Long,
    ): CumulativeFrameData {
        val analysedDelta = after.analysedFrameCount - before.analysedFrameCount
        val unanalysedDelta = after.unanalysedFrameCount - before.unanalysedFrameCount
        val sourceTimeDelta = after.timeInMs - before.timeInMs
        val targetTimeDelta = targetEndTimeInMs - targetStartTimeInMs

        return CumulativeFrameData(
            timeInMs = targetEndTimeInMs,
            analysedFrameCount =
                if (sourceTimeDelta == 0L) {
                    analysedDelta
                } else {
                    (analysedDelta.toDouble() / sourceTimeDelta * targetTimeDelta).toLong()
                },
            unanalysedFrameCount =
                if (sourceTimeDelta == 0L) {
                    unanalysedDelta
                } else {
                    (unanalysedDelta.toDouble() / sourceTimeDelta * targetTimeDelta).toLong()
                },
            slowFrameCount = before.slowFrameCount,
            frozenFrameCount = before.frozenFrameCount,
        )
    }

    private fun findBestPairForInterpolation(
        events: List<CumulativeFrameData>,
        startTimeInMs: Long,
        endTimeInMs: Long,
    ): Pair<CumulativeFrameData, CumulativeFrameData> {
        if (events.size < 2) error("findBestRangePair: events should have at least 2 elements")

        val startBefore = events.lastOrNull { it.timeInMs <= startTimeInMs }
        val startAfter = events.firstOrNull { it.timeInMs in startTimeInMs until endTimeInMs }
        val endBefore = events.lastOrNull { it.timeInMs in (startTimeInMs + 1)..endTimeInMs }
        val endAfter = events.firstOrNull { it.timeInMs >= endTimeInMs }

        return when {
            startBefore != null && endAfter != null -> {
                // when outside range is available
                startBefore to endAfter
            }

            startAfter != null && endBefore != null && startAfter != endBefore -> {
                // when inner max length range is available
                startAfter to endBefore
            }

            startBefore != null && endBefore != null -> {
                startBefore to endBefore
            }

            startAfter != null && endAfter != null -> {
                startAfter to endAfter
            }

            startBefore != null -> {
                val before = events.lastOrNull { it.timeInMs < startBefore.timeInMs } ?: events.first()
                before to startBefore
            }

            endAfter != null -> {
                val after = events.firstOrNull { it.timeInMs > endAfter.timeInMs } ?: events.last()
                endAfter to after
            }

            else -> {
                error("This case should not come")
            }
        }
    }

    private fun findCriticalFrameCounts(
        events: List<CumulativeFrameData>,
        startTimeInMs: Long,
        endTimeInMs: Long,
    ): Pair<Long, Long> {
        val eventsInRange = events.filter { it.timeInMs in startTimeInMs until endTimeInMs }

        return if (eventsInRange.isEmpty()) {
            0L to 0L
        } else {
            // Multiple events: last event - (event before first event, or 0 if no previous)
            val firstEventInRange = eventsInRange.first()
            val lastEventInRange = eventsInRange.last()
            val eventBeforeFirst = events.lastOrNull { it.timeInMs < firstEventInRange.timeInMs }

            val startSlow = eventBeforeFirst?.slowFrameCount ?: 0L
            val startFrozen = eventBeforeFirst?.frozenFrameCount ?: 0L

            val slowDelta = lastEventInRange.slowFrameCount - startSlow
            val frozenDelta = lastEventInRange.frozenFrameCount - startFrozen
            slowDelta to frozenDelta
        }
    }

    internal fun createCumulativeFrameMetric(
        startTimeInMs: Long,
        endTimeInMs: Long,
        events: List<CumulativeFrameData> = snapshotEvents(),
    ): CumulativeFrameData? {
        if (startTimeInMs == endTimeInMs) return null
        if (events.isEmpty()) return null

        if (events.size == 1) {
            val singleEvent = events.first()
            val eventTime = singleEvent.timeInMs
            // If event is inside the range (startTime <= eventTime <= endTime)
            return if (eventTime in startTimeInMs..endTimeInMs) {
                CumulativeFrameData(
                    timeInMs = endTimeInMs,
                    analysedFrameCount = 1,
                    unanalysedFrameCount = 0,
                    slowFrameCount = if (singleEvent.slowFrameCount > 0) 1 else 0,
                    frozenFrameCount = if (singleEvent.frozenFrameCount > 0) 1 else 0,
                )
            } else {
                // Event is outside the range, return null
                null
            }
        }

        val bestRangePair = findBestPairForInterpolation(events, startTimeInMs, endTimeInMs)

        // Calculate start value
        val interpolatedValue: CumulativeFrameData =
            interpolateLinear(
                before = bestRangePair.first,
                after = bestRangePair.second,
                targetStartTimeInMs = startTimeInMs,
                targetEndTimeInMs = endTimeInMs,
            )

        val (slow, frozen) = findCriticalFrameCounts(events, startTimeInMs, endTimeInMs)

        return CumulativeFrameData(
            timeInMs = endTimeInMs,
            analysedFrameCount = interpolatedValue.analysedFrameCount,
            unanalysedFrameCount = interpolatedValue.unanalysedFrameCount,
            slowFrameCount = slow,
            frozenFrameCount = frozen,
        )
    }
}

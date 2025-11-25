package com.pulse.android.remote.models

import androidx.annotation.Keep
import com.pulse.android.remote.BuildConfig

@Keep
public data class InteractionConfig(
    val id: Int,
    val name: String,
    val eventSequence: List<InteractionEvent>,
    val globalBlacklistedEvents: List<InteractionEvent> = emptyList(),
    val uptimeLowerLimitInMs: Long,
    val uptimeMidLimitInMs: Long,
    val uptimeUpperLimitInMs: Long,
    val thresholdInMs: Long,
) {
    val eventSequenceSize: Int = eventSequence.size

    val firstEvent: InteractionEvent = eventSequence.first()

    init {
        if (BuildConfig.DEBUG) {
            assert(eventSequence.count { !it.isBlacklisted } > 0) { "event sequence doesn't have any non blacklisted event" }
            assert(!eventSequence.first().isBlacklisted) { "event first event is blacklisted" }
            assert(!eventSequence.last().isBlacklisted) { "event last event is blacklisted" }
        }
    }
}

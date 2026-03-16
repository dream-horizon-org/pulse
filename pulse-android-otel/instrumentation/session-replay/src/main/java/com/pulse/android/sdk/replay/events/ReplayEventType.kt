package com.pulse.android.sdk.replay.events

/**
 * Type of replay event. Mirrors common session-replay event kinds (e.g. rrweb-style).
 * Used for serialization and downstream consumers.
 */
public enum class ReplayEventType(public val value: Int) {
    DomContentLoaded(0),
    Load(1),
    FullSnapshot(2),
    IncrementalSnapshot(3),
    Meta(4),
    Custom(5),
    Plugin(6),
    ;

    public companion object {
        private val valueMap = values().associateBy { it.value }

        public fun fromValue(value: Int): ReplayEventType? = valueMap[value]
    }
}

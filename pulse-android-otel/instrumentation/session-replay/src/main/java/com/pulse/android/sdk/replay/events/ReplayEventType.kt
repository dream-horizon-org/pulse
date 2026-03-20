package com.pulse.android.sdk.replay.events

public enum class ReplayEventType(
    public val value: Int,
) {
    DOM_CONTENT_LOADED(0),
    LOAD(1),
    FULL_SNAPSHOT(2),
    INCREMENTAL_SNAPSHOT(3),
    META(4),
    CUSTOM(5),
    PLUGIN(6),
    ;

    public companion object {
        private val valueMap = values().associateBy { it.value }

        public fun fromValue(value: Int): ReplayEventType? = valueMap[value]
    }
}

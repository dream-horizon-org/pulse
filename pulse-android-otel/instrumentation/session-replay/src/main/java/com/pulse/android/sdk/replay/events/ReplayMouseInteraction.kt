package com.pulse.android.sdk.replay.events

/**
 * Mouse/touch interaction type. Matches rrweb/PostHog values.
 */
public enum class ReplayMouseInteraction(public val value: Int) {
    MouseUp(0),
    MouseDown(1),
    Click(2),
    ContextMenu(3),
    DblClick(4),
    Focus(5),
    Blur(6),
    TouchStart(7),
    TouchMoveDeparted(8),
    TouchEnd(9),
    TouchCancel(10),
    ;

    public companion object {
        private val valueMap = values().associateBy { it.value }

        public fun fromValue(value: Int): ReplayMouseInteraction? = valueMap[value]
    }
}

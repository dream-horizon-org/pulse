package com.pulse.android.sdk.replay.events

public enum class ReplayMouseInteraction(
    public val value: Int,
) {
    MOUSE_UP(0),
    MOUSE_DOWN(1),
    CLICK(2),
    CONTEXT_MENU(3),
    DBL_CLICK(4),
    FOCUS(5),
    BLUR(6),
    TOUCH_START(7),
    TOUCH_MOVE_DEPARTED(8),
    TOUCH_END(9),
    TOUCH_CANCEL(10),
    ;

    public companion object {
        private val valueMap = values().associateBy { it.value }

        public fun fromValue(value: Int): ReplayMouseInteraction? = valueMap[value]
    }
}

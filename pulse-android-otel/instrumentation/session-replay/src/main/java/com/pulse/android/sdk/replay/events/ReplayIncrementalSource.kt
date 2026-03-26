package com.pulse.android.sdk.replay.events

public enum class ReplayIncrementalSource(
    public val value: Int,
) {
    MUTATION(0),
    MOUSE_MOVE(1),
    MOUSE_INTERACTION(2),
    SCROLL(3),
    VIEWPORT_RESIZE(4),
    INPUT(5),
    TOUCH_MOVE(6),
    MEDIA_INTERACTION(7),
    STYLE_SHEET_RULE(8),
    CANVAS_MUTATION(9),
    FONT(10),
    LOG(11),
    DRAG(12),
    STYLE_DECLARATION(13),
    SELECTION(14),
    ADOPTED_STYLE_SHEET(15),
    CUSTOM_ELEMENT(16),
    ;

    public companion object {
        private val valueMap = values().associateBy { it.value }

        public fun fromValue(value: Int): ReplayIncrementalSource? = valueMap[value]
    }
}

package com.pulse.android.sdk.replay.events

public enum class ReplayIncrementalSource(public val value: Int) {
    Mutation(0),
    MouseMove(1),
    MouseInteraction(2),
    Scroll(3),
    ViewportResize(4),
    Input(5),
    TouchMove(6),
    MediaInteraction(7),
    StyleSheetRule(8),
    CanvasMutation(9),
    Font(10),
    Log(11),
    Drag(12),
    StyleDeclaration(13),
    Selection(14),
    AdoptedStyleSheet(15),
    CustomElement(16),
    ;

    public companion object {
        public fun fromValue(value: Int): ReplayIncrementalSource? =
            values().firstOrNull { it.value == value }
    }
}

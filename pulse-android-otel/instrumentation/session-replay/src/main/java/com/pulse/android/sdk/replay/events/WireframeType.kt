package com.pulse.android.sdk.replay.events

/** Constants for [ReplayWireframe.type]. */
internal object WireframeType {
    const val SCREENSHOT = "screenshot"
    const val TEXT = "text"
    const val IMAGE = "image"
    const val INPUT = "input"
    const val STATUS_BAR = "status_bar"
    const val NAVIGATION_BAR = "navigation_bar"
    const val WEB_VIEW = "web_view"
    const val RADIO_GROUP = "radio_group"
}

/** Constants for [ReplayWireframe.inputType]. */
internal object InputWireframeType {
    const val BUTTON = "button"
    const val CHECKBOX = "checkbox"
    const val RADIO = "radio"
    const val TEXT_AREA = "text_area"
    const val SELECT = "select"
    const val TOGGLE = "toggle"
    const val PROGRESS = "progress"
}

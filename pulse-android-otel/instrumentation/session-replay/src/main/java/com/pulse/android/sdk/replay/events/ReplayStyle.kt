package com.pulse.android.sdk.replay.events

/**
 * Styling for a wireframe node (used in view-based wireframe mode).
 * Screenshot mode only uses bounds; style is optional for consistency with event shape.
 * All fields are immutable — use [copy] to create modified instances.
 */
public data class ReplayStyle(
    public val color: String? = null,
    public val backgroundColor: String? = null,
    public val backgroundImage: String? = null,
    public val borderWidth: Int? = null,
    public val borderRadius: Int? = null,
    public val borderColor: String? = null,
    public val fontSize: Int? = null,
    public val fontFamily: String? = null,
    public val horizontalAlign: String? = null,
    public val verticalAlign: String? = null,
    public val paddingTop: Int? = null,
    public val paddingBottom: Int? = null,
    public val paddingLeft: Int? = null,
    public val paddingRight: Int? = null,
    public val bar: String? = null,
    public val iconLeft: String? = null,
    public val iconRight: String? = null,
)

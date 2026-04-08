package com.pulse.android.sdk.replay.events

import java.util.Objects

/**
 * Styling for a wireframe node (used in view-based wireframe mode).
 * Screenshot mode only uses bounds; style is optional for consistency with event shape.
 * All fields are immutable — use [copy] to create modified instances.
 */
public class ReplayStyle internal constructor(
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
) {
    internal fun copy(
        color: String? = this.color,
        backgroundColor: String? = this.backgroundColor,
        backgroundImage: String? = this.backgroundImage,
        borderWidth: Int? = this.borderWidth,
        borderRadius: Int? = this.borderRadius,
        borderColor: String? = this.borderColor,
        fontSize: Int? = this.fontSize,
        fontFamily: String? = this.fontFamily,
        horizontalAlign: String? = this.horizontalAlign,
        verticalAlign: String? = this.verticalAlign,
        paddingTop: Int? = this.paddingTop,
        paddingBottom: Int? = this.paddingBottom,
        paddingLeft: Int? = this.paddingLeft,
        paddingRight: Int? = this.paddingRight,
        bar: String? = this.bar,
        iconLeft: String? = this.iconLeft,
        iconRight: String? = this.iconRight,
    ): ReplayStyle =
        ReplayStyle(
            color,
            backgroundColor,
            backgroundImage,
            borderWidth,
            borderRadius,
            borderColor,
            fontSize,
            fontFamily,
            horizontalAlign,
            verticalAlign,
            paddingTop,
            paddingBottom,
            paddingLeft,
            paddingRight,
            bar,
            iconLeft,
            iconRight,
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ReplayStyle) return false
        return color == other.color &&
            backgroundColor == other.backgroundColor &&
            backgroundImage == other.backgroundImage &&
            borderWidth == other.borderWidth &&
            borderRadius == other.borderRadius &&
            borderColor == other.borderColor &&
            fontSize == other.fontSize &&
            fontFamily == other.fontFamily &&
            horizontalAlign == other.horizontalAlign &&
            verticalAlign == other.verticalAlign &&
            paddingTop == other.paddingTop &&
            paddingBottom == other.paddingBottom &&
            paddingLeft == other.paddingLeft &&
            paddingRight == other.paddingRight &&
            bar == other.bar &&
            iconLeft == other.iconLeft &&
            iconRight == other.iconRight
    }

    override fun hashCode(): Int =
        Objects.hash(
            color,
            backgroundColor,
            backgroundImage,
            borderWidth,
            borderRadius,
            borderColor,
            fontSize,
            fontFamily,
            horizontalAlign,
            verticalAlign,
            paddingTop,
            paddingBottom,
            paddingLeft,
            paddingRight,
            bar,
            iconLeft,
            iconRight,
        )
}

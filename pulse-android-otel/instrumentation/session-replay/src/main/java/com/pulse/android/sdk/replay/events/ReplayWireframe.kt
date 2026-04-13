package com.pulse.android.sdk.replay.events

import java.util.Objects

/**
 * A single node in the replay snapshot tree.
 * For screenshot mode: one root node with type "screenshot" and base64 image.
 * For wireframe mode: tree of nodes (text, image, input, div, etc.).
 *
 * @property id Stable id (e.g. System.identityHashCode(view))
 * @property x x position in density-independent units
 * @property y y position in density-independent units
 * @property width Width in density-independent units
 * @property height Height in density-independent units
 * @property childWireframes Children for wireframe mode; null for screenshot or leaf nodes
 * @property type Wireframe type — see [WireframeType] constants
 * @property inputType TBA<anirudh.bharti> for session replay
 * @property text TBA<anirudh.bharti> for session replay
 * @property label TBA by <anirudh.bharti> for session replay
 * @property value JSON-compatible primitive only: [String], [Int], or [Float]. No other types.
 * @property base64 Optional image data (screenshot or drawable)
 * @property style TBA by <anirudh.bharti> for session replay
 * @property isDisabled TBA by <anirudh.bharti> for session replay
 * @property isChecked TBA by <anirudh.bharti> for session replay
 * @property options TBA by <anirudh.bharti> for session replay
 * @property parentId Parent wireframe id for incremental updates (transient, not serialized)
 * @property max TBA by <anirudh.bharti> for session replay
 */
public class ReplayWireframe(
    public val id: Int,
    public val x: Int,
    public val y: Int,
    public val width: Int,
    public val height: Int,
    public val childWireframes: List<ReplayWireframe>? = null,
    public val type: String? = null,
    public val inputType: String? = null,
    public val text: String? = null,
    public val label: String? = null,
    public val value: Any? = null,
    public val base64: String? = null,
    public val style: ReplayStyle? = null,
    public val isDisabled: Boolean? = null,
    public val isChecked: Boolean? = null,
    public val options: List<String>? = null,
    @Transient public val parentId: Int? = null,
    public val max: Int? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ReplayWireframe) return false
        return id == other.id &&
            x == other.x &&
            y == other.y &&
            width == other.width &&
            height == other.height &&
            childWireframes == other.childWireframes &&
            type == other.type &&
            inputType == other.inputType &&
            text == other.text &&
            label == other.label &&
            value == other.value &&
            base64 == other.base64 &&
            style == other.style &&
            isDisabled == other.isDisabled &&
            isChecked == other.isChecked &&
            options == other.options &&
            parentId == other.parentId &&
            max == other.max
    }

    override fun hashCode(): Int =
        Objects.hash(
            id,
            x,
            y,
            width,
            height,
            childWireframes,
            type,
            inputType,
            text,
            label,
            value,
            base64,
            style,
            isDisabled,
            isChecked,
            options,
            parentId,
            max,
        )
}

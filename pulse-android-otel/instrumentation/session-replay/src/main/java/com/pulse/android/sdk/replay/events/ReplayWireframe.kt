package com.pulse.android.sdk.replay.events

/**
 * A single node in the replay snapshot tree.
 * For screenshot mode: one root node with type "screenshot" and base64 image.
 * For wireframe mode: tree of nodes (text, image, input, div, etc.).
 *
 * @param id Stable id (e.g. System.identityHashCode(view))
 * @param x x position in density-independent units
 * @param y y position in density-independent units
 * @param width Width in density-independent units
 * @param height Height in density-independent units
 * @param type Wireframe type — see [WireframeType] constants
 * @param base64 Optional image data (screenshot or drawable)
 * @param childWireframes Children for wireframe mode; null for screenshot or leaf nodes
 * @param parentId Parent wireframe id for incremental updates (transient, not serialized)
 * @param value JSON-compatible primitive only: [String], [Int], or [Float]. No other types.
 */
public data class ReplayWireframe(
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
    public val disabled: Boolean? = null,
    public val checked: Boolean? = null,
    public val options: List<String>? = null,
    @Transient public val parentId: Int? = null,
    public val max: Int? = null,
)

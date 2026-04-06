package com.pulse.android.sdk.replay.events

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
    public val isDisabled: Boolean? = null,
    public val isChecked: Boolean? = null,
    public val options: List<String>? = null,
    @Transient public val parentId: Int? = null,
    public val max: Int? = null,
)

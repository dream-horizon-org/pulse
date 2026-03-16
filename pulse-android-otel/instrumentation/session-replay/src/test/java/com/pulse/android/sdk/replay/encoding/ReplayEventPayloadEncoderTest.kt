package com.pulse.android.sdk.replay.encoding

import com.pulse.android.sdk.replay.events.ReplayCustomEvent
import com.pulse.android.sdk.replay.events.ReplayEvent
import com.pulse.android.sdk.replay.events.ReplayEventType
import com.pulse.android.sdk.replay.events.ReplayFullSnapshotEvent
import com.pulse.android.sdk.replay.events.ReplayIncrementalMouseInteractionData
import com.pulse.android.sdk.replay.events.ReplayIncrementalMouseInteractionEvent
import com.pulse.android.sdk.replay.events.ReplayIncrementalMutationData
import com.pulse.android.sdk.replay.events.ReplayIncrementalSnapshotEvent
import com.pulse.android.sdk.replay.events.ReplayMetaEvent
import com.pulse.android.sdk.replay.events.ReplayMouseInteraction
import com.pulse.android.sdk.replay.events.ReplayMutatedNode
import com.pulse.android.sdk.replay.events.ReplayRemovedNode
import com.pulse.android.sdk.replay.events.ReplayStyle
import com.pulse.android.sdk.replay.events.ReplayWireframe
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONArray
import org.junit.jupiter.api.Test

class ReplayEventPayloadEncoderTest {
    @Test
    fun `encodeToJson with empty list returns empty array`() {
        val result = ReplayEventPayloadEncoder.encodeToJson(emptyList())
        assertThat(result).isEqualTo("[]")
    }

    @Test
    fun `encodeToJson with MetaEvent has correct type and data fields`() {
        val event = ReplayMetaEvent(width = 1080, height = 1920, timestamp = 1000L, href = "https://example.com")
        val result = ReplayEventPayloadEncoder.encodeToJson(listOf(event))
        val arr = JSONArray(result)
        assertThat(arr.length()).isEqualTo(1)
        val obj = arr.getJSONObject(0)
        assertThat(obj.getInt("type")).isEqualTo(4)
        assertThat(obj.getLong("timestamp")).isEqualTo(1000L)
        val data = obj.getJSONObject("data")
        assertThat(data.getString("href")).isEqualTo("https://example.com")
        assertThat(data.getInt("width")).isEqualTo(1080)
        assertThat(data.getInt("height")).isEqualTo(1920)
    }

    @Test
    fun `encodeToJson with FullSnapshotEvent has correct type and wireframes array`() {
        val wireframe = ReplayWireframe(id = 1, x = 0, y = 0, width = 100, height = 50)
        val event = ReplayFullSnapshotEvent(wireframes = listOf(wireframe), initialOffsetTop = 0, initialOffsetLeft = 0, timestamp = 2000L)
        val result = ReplayEventPayloadEncoder.encodeToJson(listOf(event))
        val arr = JSONArray(result)
        val obj = arr.getJSONObject(0)
        assertThat(obj.getInt("type")).isEqualTo(2)
        val data = obj.getJSONObject("data")
        val wireframes = data.getJSONArray("wireframes")
        assertThat(wireframes.length()).isEqualTo(1)
        val wf = wireframes.getJSONObject(0)
        assertThat(wf.getInt("id")).isEqualTo(1)
        assertThat(wf.getInt("x")).isEqualTo(0)
        assertThat(wf.getInt("y")).isEqualTo(0)
        assertThat(wf.getInt("width")).isEqualTo(100)
        assertThat(wf.getInt("height")).isEqualTo(50)
    }

    @Test
    fun `encodeToJson with IncrementalSnapshotEvent has adds removes updates`() {
        val wireframe = ReplayWireframe(id = 10, x = 5, y = 5, width = 50, height = 25)
        val add = ReplayMutatedNode(wireframe = wireframe, parentId = 0)
        val remove = ReplayRemovedNode(id = 99, parentId = 1)
        val updateWireframe = ReplayWireframe(id = 20, x = 10, y = 10, width = 60, height = 30)
        val update = ReplayMutatedNode(wireframe = updateWireframe, parentId = 0)
        val mutationData =
            ReplayIncrementalMutationData(
                adds = listOf(add),
                removes = listOf(remove),
                updates = listOf(update),
            )
        val event = ReplayIncrementalSnapshotEvent(mutationData = mutationData, timestamp = 3000L)
        val result = ReplayEventPayloadEncoder.encodeToJson(listOf(event))
        val arr = JSONArray(result)
        val obj = arr.getJSONObject(0)
        assertThat(obj.getInt("type")).isEqualTo(3)
        val data = obj.getJSONObject("data")
        val adds = data.getJSONArray("adds")
        assertThat(adds.length()).isEqualTo(1)
        assertThat(adds.getJSONObject(0).getJSONObject("wireframe").getInt("id")).isEqualTo(10)
        val removes = data.getJSONArray("removes")
        assertThat(removes.length()).isEqualTo(1)
        assertThat(removes.getJSONObject(0).getInt("id")).isEqualTo(99)
        val updates = data.getJSONArray("updates")
        assertThat(updates.length()).isEqualTo(1)
        assertThat(updates.getJSONObject(0).getJSONObject("wireframe").getInt("id")).isEqualTo(20)
    }

    @Test
    fun `encodeToJson with MouseInteractionEvent has correct source type x y`() {
        val mouseData =
            ReplayIncrementalMouseInteractionData(
                id = 42,
                type = ReplayMouseInteraction.TouchStart,
                x = 150,
                y = 200,
            )
        val event = ReplayIncrementalMouseInteractionEvent(mouseInteractionData = mouseData, timestamp = 4000L)
        val result = ReplayEventPayloadEncoder.encodeToJson(listOf(event))
        val arr = JSONArray(result)
        val obj = arr.getJSONObject(0)
        assertThat(obj.getInt("type")).isEqualTo(3)
        val data = obj.getJSONObject("data")
        assertThat(data.getInt("id")).isEqualTo(42)
        assertThat(data.getInt("type")).isEqualTo(7)
        assertThat(data.getInt("x")).isEqualTo(150)
        assertThat(data.getInt("y")).isEqualTo(200)
        assertThat(data.getInt("source")).isEqualTo(2)
    }

    @Test
    fun `encodeToJson with CustomEvent has tag and payload`() {
        val event = ReplayCustomEvent(tag = "keyboard_open", payload = mapOf("visible" to true, "count" to 3), timestamp = 5000L)
        val result = ReplayEventPayloadEncoder.encodeToJson(listOf(event))
        val arr = JSONArray(result)
        val obj = arr.getJSONObject(0)
        assertThat(obj.getInt("type")).isEqualTo(5)
        val data = obj.getJSONObject("data")
        assertThat(data.getString("tag")).isEqualTo("keyboard_open")
        val payload = data.getJSONObject("payload")
        assertThat(payload.getBoolean("visible")).isTrue()
        assertThat(payload.getInt("count")).isEqualTo(3)
    }

    @Test
    fun `wireframe JSON includes style fields correctly`() {
        val style = ReplayStyle(color = "#333333", backgroundColor = "#ffffff", fontSize = 14)
        val wireframe = ReplayWireframe(id = 1, x = 0, y = 0, width = 100, height = 50, style = style)
        val event = ReplayFullSnapshotEvent(wireframes = listOf(wireframe), initialOffsetTop = 0, initialOffsetLeft = 0, timestamp = 0L)
        val result = ReplayEventPayloadEncoder.encodeToJson(listOf(event))
        val data = JSONArray(result).getJSONObject(0).getJSONObject("data")
        val wf = data.getJSONArray("wireframes").getJSONObject(0)
        val styleObj = wf.getJSONObject("style")
        assertThat(styleObj.getString("color")).isEqualTo("#333333")
        assertThat(styleObj.getString("backgroundColor")).isEqualTo("#ffffff")
        assertThat(styleObj.getInt("fontSize")).isEqualTo(14)
    }

    @Test
    fun `wireframe JSON includes childWireframes recursively`() {
        val child = ReplayWireframe(id = 2, x = 10, y = 10, width = 30, height = 20)
        val parent = ReplayWireframe(id = 1, x = 0, y = 0, width = 100, height = 50, childWireframes = listOf(child))
        val event = ReplayFullSnapshotEvent(wireframes = listOf(parent), initialOffsetTop = 0, initialOffsetLeft = 0, timestamp = 0L)
        val result = ReplayEventPayloadEncoder.encodeToJson(listOf(event))
        val data = JSONArray(result).getJSONObject(0).getJSONObject("data")
        val parentWf = data.getJSONArray("wireframes").getJSONObject(0)
        assertThat(parentWf.getInt("id")).isEqualTo(1)
        val children = parentWf.getJSONArray("childWireframes")
        assertThat(children.length()).isEqualTo(1)
        val childWf = children.getJSONObject(0)
        assertThat(childWf.getInt("id")).isEqualTo(2)
        assertThat(childWf.getInt("x")).isEqualTo(10)
    }

    @Test
    fun `null event data encodes as null`() {
        val event =
            object : ReplayEvent(type = ReplayEventType.Meta, timestamp = 0L, data = null) {}
        val result = ReplayEventPayloadEncoder.encodeToJson(listOf(event))
        val arr = JSONArray(result)
        val obj = arr.getJSONObject(0)
        assertThat(obj.getInt("type")).isEqualTo(4)
        assertThat(obj.isNull("data")).isTrue()
    }
}

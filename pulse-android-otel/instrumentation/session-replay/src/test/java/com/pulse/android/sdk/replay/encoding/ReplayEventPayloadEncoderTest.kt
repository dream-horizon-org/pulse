package com.pulse.android.sdk.replay.encoding

import com.google.gson.JsonParser
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
        val arr = JsonParser.parseString(result).asJsonArray
        assertThat(arr.size()).isEqualTo(1)
        val obj = arr.get(0).asJsonObject
        assertThat(obj.get("type").asJsonPrimitive.asInt).isEqualTo(4)
        assertThat(obj.get("timestamp").asJsonPrimitive.asLong).isEqualTo(1000L)
        val data = obj.getAsJsonObject("data")
        assertThat(data.get("href").asJsonPrimitive.asString).isEqualTo("https://example.com")
        assertThat(data.get("width").asJsonPrimitive.asInt).isEqualTo(1080)
        assertThat(data.get("height").asJsonPrimitive.asInt).isEqualTo(1920)
    }

    @Test
    fun `encodeToJson with FullSnapshotEvent has correct type and wireframes array`() {
        val wireframe = ReplayWireframe(id = 1, x = 0, y = 0, width = 100, height = 50)
        val event = ReplayFullSnapshotEvent(wireframes = listOf(wireframe), initialOffsetTop = 0, initialOffsetLeft = 0, timestamp = 2000L)
        val result = ReplayEventPayloadEncoder.encodeToJson(listOf(event))
        val arr = JsonParser.parseString(result).asJsonArray
        val obj = arr.get(0).asJsonObject
        assertThat(obj.get("type").asJsonPrimitive.asInt).isEqualTo(2)
        val data = obj.getAsJsonObject("data")
        val wireframes = data.getAsJsonArray("wireframes")
        assertThat(wireframes.size()).isEqualTo(1)
        val wf = wireframes.get(0).asJsonObject
        assertThat(wf.get("id").asJsonPrimitive.asInt).isEqualTo(1)
        assertThat(wf.get("x").asJsonPrimitive.asInt).isEqualTo(0)
        assertThat(wf.get("y").asJsonPrimitive.asInt).isEqualTo(0)
        assertThat(wf.get("width").asJsonPrimitive.asInt).isEqualTo(100)
        assertThat(wf.get("height").asJsonPrimitive.asInt).isEqualTo(50)
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
        val arr = JsonParser.parseString(result).asJsonArray
        val obj = arr.get(0).asJsonObject
        assertThat(obj.get("type").asJsonPrimitive.asInt).isEqualTo(3)
        val data = obj.getAsJsonObject("data")
        val adds = data.getAsJsonArray("adds")
        assertThat(adds.size()).isEqualTo(1)
        val addWireframe = adds.get(0).asJsonObject.getAsJsonObject("wireframe")
        assertThat(addWireframe.get("id").asJsonPrimitive.asInt).isEqualTo(10)
        val removes = data.getAsJsonArray("removes")
        assertThat(removes.size()).isEqualTo(1)
        assertThat(
            removes
                .get(0)
                .asJsonObject
                .get("id")
                .asJsonPrimitive.asInt,
        ).isEqualTo(99)
        val updates = data.getAsJsonArray("updates")
        assertThat(updates.size()).isEqualTo(1)
        val updateWireframeJson = updates.get(0).asJsonObject.getAsJsonObject("wireframe")
        assertThat(updateWireframeJson.get("id").asJsonPrimitive.asInt).isEqualTo(20)
    }

    @Test
    fun `encodeToJson with MouseInteractionEvent has correct source type x y`() {
        val mouseData =
            ReplayIncrementalMouseInteractionData(
                id = 42,
                type = ReplayMouseInteraction.TOUCH_START,
                x = 150,
                y = 200,
            )
        val event = ReplayIncrementalMouseInteractionEvent(mouseInteractionData = mouseData, timestamp = 4000L)
        val result = ReplayEventPayloadEncoder.encodeToJson(listOf(event))
        val arr = JsonParser.parseString(result).asJsonArray
        val obj = arr.get(0).asJsonObject
        assertThat(obj.get("type").asJsonPrimitive.asInt).isEqualTo(3)
        val data = obj.getAsJsonObject("data")
        assertThat(data.get("id").asJsonPrimitive.asInt).isEqualTo(42)
        assertThat(data.get("type").asJsonPrimitive.asInt).isEqualTo(7)
        assertThat(data.get("x").asJsonPrimitive.asInt).isEqualTo(150)
        assertThat(data.get("y").asJsonPrimitive.asInt).isEqualTo(200)
        assertThat(data.get("source").asJsonPrimitive.asInt).isEqualTo(2)
    }

    @Test
    fun `encodeToJson with CustomEvent has tag and payload`() {
        val event = ReplayCustomEvent(tag = "keyboard_open", payload = mapOf("visible" to true, "count" to 3), timestamp = 5000L)
        val result = ReplayEventPayloadEncoder.encodeToJson(listOf(event))
        val arr = JsonParser.parseString(result).asJsonArray
        val obj = arr.get(0).asJsonObject
        assertThat(obj.get("type").asJsonPrimitive.asInt).isEqualTo(5)
        val data = obj.getAsJsonObject("data")
        assertThat(data.get("tag").asJsonPrimitive.asString).isEqualTo("keyboard_open")
        val payload = data.getAsJsonObject("payload")
        assertThat(payload.get("visible").asJsonPrimitive.asBoolean).isTrue()
        assertThat(payload.get("count").asJsonPrimitive.asInt).isEqualTo(3)
    }

    @Test
    fun `wireframe JSON includes style fields correctly`() {
        val style = ReplayStyle(color = "#333333", backgroundColor = "#ffffff", fontSize = 14)
        val wireframe = ReplayWireframe(id = 1, x = 0, y = 0, width = 100, height = 50, style = style)
        val event = ReplayFullSnapshotEvent(wireframes = listOf(wireframe), initialOffsetTop = 0, initialOffsetLeft = 0, timestamp = 0L)
        val result = ReplayEventPayloadEncoder.encodeToJson(listOf(event))
        val data =
            JsonParser
                .parseString(result)
                .asJsonArray
                .get(0)
                .asJsonObject
                .getAsJsonObject("data")
        val wf = data.getAsJsonArray("wireframes").get(0).asJsonObject
        val styleObj = wf.getAsJsonObject("style")
        assertThat(styleObj.get("color").asJsonPrimitive.asString).isEqualTo("#333333")
        assertThat(styleObj.get("backgroundColor").asJsonPrimitive.asString).isEqualTo("#ffffff")
        assertThat(styleObj.get("fontSize").asJsonPrimitive.asInt).isEqualTo(14)
    }

    @Test
    fun `wireframe JSON includes childWireframes recursively`() {
        val child = ReplayWireframe(id = 2, x = 10, y = 10, width = 30, height = 20)
        val parent = ReplayWireframe(id = 1, x = 0, y = 0, width = 100, height = 50, childWireframes = listOf(child))
        val event = ReplayFullSnapshotEvent(wireframes = listOf(parent), initialOffsetTop = 0, initialOffsetLeft = 0, timestamp = 0L)
        val result = ReplayEventPayloadEncoder.encodeToJson(listOf(event))
        val data =
            JsonParser
                .parseString(result)
                .asJsonArray
                .get(0)
                .asJsonObject
                .getAsJsonObject("data")
        val parentWf = data.getAsJsonArray("wireframes").get(0).asJsonObject
        assertThat(parentWf.get("id").asJsonPrimitive.asInt).isEqualTo(1)
        val children = parentWf.getAsJsonArray("childWireframes")
        assertThat(children.size()).isEqualTo(1)
        val childWf = children.get(0).asJsonObject
        assertThat(childWf.get("id").asJsonPrimitive.asInt).isEqualTo(2)
        assertThat(childWf.get("x").asJsonPrimitive.asInt).isEqualTo(10)
    }

    @Test
    fun `null event data is absent from JSON`() {
        val event =
            object : ReplayEvent(type = ReplayEventType.META, timestamp = 0L, data = null) {}
        val result = ReplayEventPayloadEncoder.encodeToJson(listOf(event))
        val arr = JsonParser.parseString(result).asJsonArray
        val obj = arr.get(0).asJsonObject
        assertThat(obj.get("type").asJsonPrimitive.asInt).isEqualTo(4)
        assertThat(!obj.has("data") || obj.get("data").isJsonNull).isTrue()
    }
}

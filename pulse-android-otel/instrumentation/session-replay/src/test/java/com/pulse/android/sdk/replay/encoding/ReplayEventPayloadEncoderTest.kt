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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
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
        val arr = Json.parseToJsonElement(result).jsonArray
        assertThat(arr.size).isEqualTo(1)
        val obj = arr[0].jsonObject
        assertThat(obj["type"]!!.jsonPrimitive.int).isEqualTo(4)
        assertThat(obj["timestamp"]!!.jsonPrimitive.long).isEqualTo(1000L)
        val data = obj["data"]!!.jsonObject
        assertThat(data["href"]!!.jsonPrimitive.content).isEqualTo("https://example.com")
        assertThat(data["width"]!!.jsonPrimitive.int).isEqualTo(1080)
        assertThat(data["height"]!!.jsonPrimitive.int).isEqualTo(1920)
    }

    @Test
    fun `encodeToJson with FullSnapshotEvent has correct type and wireframes array`() {
        val wireframe = ReplayWireframe(id = 1, x = 0, y = 0, width = 100, height = 50)
        val event = ReplayFullSnapshotEvent(wireframes = listOf(wireframe), initialOffsetTop = 0, initialOffsetLeft = 0, timestamp = 2000L)
        val result = ReplayEventPayloadEncoder.encodeToJson(listOf(event))
        val arr = Json.parseToJsonElement(result).jsonArray
        val obj = arr[0].jsonObject
        assertThat(obj["type"]!!.jsonPrimitive.int).isEqualTo(2)
        val data = obj["data"]!!.jsonObject
        val wireframes = data["wireframes"]!!.jsonArray
        assertThat(wireframes.size).isEqualTo(1)
        val wf = wireframes[0].jsonObject
        assertThat(wf["id"]!!.jsonPrimitive.int).isEqualTo(1)
        assertThat(wf["x"]!!.jsonPrimitive.int).isEqualTo(0)
        assertThat(wf["y"]!!.jsonPrimitive.int).isEqualTo(0)
        assertThat(wf["width"]!!.jsonPrimitive.int).isEqualTo(100)
        assertThat(wf["height"]!!.jsonPrimitive.int).isEqualTo(50)
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
        val arr = Json.parseToJsonElement(result).jsonArray
        val obj = arr[0].jsonObject
        assertThat(obj["type"]!!.jsonPrimitive.int).isEqualTo(3)
        val data = obj["data"]!!.jsonObject
        val adds = data["adds"]!!.jsonArray
        assertThat(adds.size).isEqualTo(1)
        val addWireframe = adds[0].jsonObject["wireframe"]!!.jsonObject
        assertThat(addWireframe["id"]!!.jsonPrimitive.int).isEqualTo(10)
        val removes = data["removes"]!!.jsonArray
        assertThat(removes.size).isEqualTo(1)
        assertThat(removes[0].jsonObject["id"]!!.jsonPrimitive.int).isEqualTo(99)
        val updates = data["updates"]!!.jsonArray
        assertThat(updates.size).isEqualTo(1)
        val updateWireframeJson = updates[0].jsonObject["wireframe"]!!.jsonObject
        assertThat(updateWireframeJson["id"]!!.jsonPrimitive.int).isEqualTo(20)
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
        val arr = Json.parseToJsonElement(result).jsonArray
        val obj = arr[0].jsonObject
        assertThat(obj["type"]!!.jsonPrimitive.int).isEqualTo(3)
        val data = obj["data"]!!.jsonObject
        assertThat(data["id"]!!.jsonPrimitive.int).isEqualTo(42)
        assertThat(data["type"]!!.jsonPrimitive.int).isEqualTo(7)
        assertThat(data["x"]!!.jsonPrimitive.int).isEqualTo(150)
        assertThat(data["y"]!!.jsonPrimitive.int).isEqualTo(200)
        assertThat(data["source"]!!.jsonPrimitive.int).isEqualTo(2)
    }

    @Test
    fun `encodeToJson with CustomEvent has tag and payload`() {
        val event = ReplayCustomEvent(tag = "keyboard_open", payload = mapOf("visible" to true, "count" to 3), timestamp = 5000L)
        val result = ReplayEventPayloadEncoder.encodeToJson(listOf(event))
        val arr = Json.parseToJsonElement(result).jsonArray
        val obj = arr[0].jsonObject
        assertThat(obj["type"]!!.jsonPrimitive.int).isEqualTo(5)
        val data = obj["data"]!!.jsonObject
        assertThat(data["tag"]!!.jsonPrimitive.content).isEqualTo("keyboard_open")
        val payload = data["payload"]!!.jsonObject
        assertThat(payload["visible"]!!.jsonPrimitive.boolean).isTrue()
        assertThat(payload["count"]!!.jsonPrimitive.int).isEqualTo(3)
    }

    @Test
    fun `wireframe JSON includes style fields correctly`() {
        val style = ReplayStyle(color = "#333333", backgroundColor = "#ffffff", fontSize = 14)
        val wireframe = ReplayWireframe(id = 1, x = 0, y = 0, width = 100, height = 50, style = style)
        val event = ReplayFullSnapshotEvent(wireframes = listOf(wireframe), initialOffsetTop = 0, initialOffsetLeft = 0, timestamp = 0L)
        val result = ReplayEventPayloadEncoder.encodeToJson(listOf(event))
        val data =
            Json
                .parseToJsonElement(result)
                .jsonArray[0]
                .jsonObject["data"]!!
                .jsonObject
        val wf = data["wireframes"]!!.jsonArray[0].jsonObject
        val styleObj = wf["style"]!!.jsonObject
        assertThat(styleObj["color"]!!.jsonPrimitive.content).isEqualTo("#333333")
        assertThat(styleObj["backgroundColor"]!!.jsonPrimitive.content).isEqualTo("#ffffff")
        assertThat(styleObj["fontSize"]!!.jsonPrimitive.int).isEqualTo(14)
    }

    @Test
    fun `wireframe JSON includes childWireframes recursively`() {
        val child = ReplayWireframe(id = 2, x = 10, y = 10, width = 30, height = 20)
        val parent = ReplayWireframe(id = 1, x = 0, y = 0, width = 100, height = 50, childWireframes = listOf(child))
        val event = ReplayFullSnapshotEvent(wireframes = listOf(parent), initialOffsetTop = 0, initialOffsetLeft = 0, timestamp = 0L)
        val result = ReplayEventPayloadEncoder.encodeToJson(listOf(event))
        val data =
            Json
                .parseToJsonElement(result)
                .jsonArray[0]
                .jsonObject["data"]!!
                .jsonObject
        val parentWf = data["wireframes"]!!.jsonArray[0].jsonObject
        assertThat(parentWf["id"]!!.jsonPrimitive.int).isEqualTo(1)
        val children = parentWf["childWireframes"]!!.jsonArray
        assertThat(children.size).isEqualTo(1)
        val childWf = children[0].jsonObject
        assertThat(childWf["id"]!!.jsonPrimitive.int).isEqualTo(2)
        assertThat(childWf["x"]!!.jsonPrimitive.int).isEqualTo(10)
    }

    @Test
    fun `null event data is absent from JSON`() {
        val event =
            object : ReplayEvent(type = ReplayEventType.META, timestamp = 0L, data = null) {}
        val result = ReplayEventPayloadEncoder.encodeToJson(listOf(event))
        val arr = Json.parseToJsonElement(result).jsonArray
        val obj = arr[0].jsonObject
        assertThat(obj["type"]!!.jsonPrimitive.int).isEqualTo(4)
        assertThat(!obj.containsKey("data") || obj["data"] == null).isTrue()
    }
}

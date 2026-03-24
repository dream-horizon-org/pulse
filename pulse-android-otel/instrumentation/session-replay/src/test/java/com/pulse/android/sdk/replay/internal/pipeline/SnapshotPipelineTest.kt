package com.pulse.android.sdk.replay.internal.pipeline

import android.view.View
import com.pulse.android.sdk.replay.SessionReplayConfig
import com.pulse.android.sdk.replay.events.ReplayEventType
import com.pulse.android.sdk.replay.events.ReplayFullSnapshotData
import com.pulse.android.sdk.replay.events.ReplayMetaData
import com.pulse.android.sdk.replay.events.ReplayWireframe
import com.pulse.android.sdk.replay.internal.capture.MaskRectCache
import com.pulse.android.sdk.replay.internal.scheduling.NextDrawListener
import com.pulse.android.sdk.replay.internal.scheduling.ViewTreeSnapshotStatus
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class SnapshotPipelineTest {
    private lateinit var view: View
    private lateinit var status: ViewTreeSnapshotStatus
    private lateinit var config: SessionReplayConfig

    @BeforeEach
    fun setUp() {
        view = mockk(relaxed = true)
        every { view.context } returns mockk<android.content.Context>(relaxed = true)
        val listener = mockk<NextDrawListener>(relaxed = true)
        val maskRectCache = mockk<MaskRectCache>(relaxed = true)
        status = ViewTreeSnapshotStatus(listener, maskRectCache)
        config = SessionReplayConfig()
    }

    @Nested
    inner class FirstCall {
        @Test
        fun `generates Meta and FullSnapshot when hasSentFullSnapshot and hasSentMetaEvent are false`() {
            val wireframe = ReplayWireframe(id = 1, x = 0, y = 0, width = 100, height = 100)
            val events =
                SnapshotPipeline.generateEvents(
                    wireframe = wireframe,
                    status = status,
                    timestamp = 1000L,
                    view = view,
                    screenWidth = 1080,
                    screenHeight = 1920,
                )
            assertThat(events).hasSize(2)
            assertThat(events[0].type).isEqualTo(ReplayEventType.META)
            assertThat(events[1].type).isEqualTo(ReplayEventType.FULL_SNAPSHOT)
            assertThat(status.hasSentMetaEvent).isTrue
            assertThat(status.hasSentFullSnapshot).isTrue
        }

        @Test
        fun `Meta event has correct screen dimensions`() {
            val wireframe = ReplayWireframe(id = 1, x = 0, y = 0, width = 100, height = 100)
            val events =
                SnapshotPipeline.generateEvents(
                    wireframe = wireframe,
                    status = status,
                    timestamp = 1000L,
                    view = view,
                    screenWidth = 800,
                    screenHeight = 600,
                )
            val metaEvent = events.first { it.type == ReplayEventType.META }
            val metaData = (metaEvent.data ?: error("meta event data is null")) as ReplayMetaData
            assertThat(metaData.width).isEqualTo(800)
            assertThat(metaData.height).isEqualTo(600)
        }

        @Test
        fun `FullSnapshot event has the wireframe passed in`() {
            val wireframe = ReplayWireframe(id = 42, x = 10, y = 20, width = 200, height = 150)
            val events =
                SnapshotPipeline.generateEvents(
                    wireframe = wireframe,
                    status = status,
                    timestamp = 1000L,
                    view = view,
                    screenWidth = 1080,
                    screenHeight = 1920,
                )
            val fullSnapshotEvent = events.first { it.type == ReplayEventType.FULL_SNAPSHOT }
            val fullData = (fullSnapshotEvent.data ?: error("full snapshot event data is null")) as ReplayFullSnapshotData
            assertThat(fullData.wireframes).hasSize(1)
            assertThat(fullData.wireframes[0].id).isEqualTo(42)
            assertThat(fullData.wireframes[0].width).isEqualTo(200)
            assertThat(fullData.wireframes[0].height).isEqualTo(150)
        }
    }

    @Nested
    inner class SecondCall {
        @Test
        fun `generates empty list when same wireframe and hasSentFullSnapshot is true`() {
            val wireframe = ReplayWireframe(id = 1, x = 0, y = 0, width = 100, height = 100)
            status.hasSentFullSnapshot = true
            status.hasSentMetaEvent = true
            status.lastSnapshot = wireframe
            val events =
                SnapshotPipeline.generateEvents(
                    wireframe = wireframe,
                    status = status,
                    timestamp = 2000L,
                    view = view,
                    screenWidth = 1080,
                    screenHeight = 1920,
                )
            assertThat(events).isEmpty()
        }

        @Test
        fun `generates IncrementalSnapshot with adds when new wireframe has additional node`() {
            val oldWireframe = ReplayWireframe(id = 1, x = 0, y = 0, width = 100, height = 100)
            val newChild = ReplayWireframe(id = 2, x = 0, y = 100, width = 100, height = 50)
            val newWireframe =
                ReplayWireframe(
                    id = 1,
                    x = 0,
                    y = 0,
                    width = 100,
                    height = 150,
                    childWireframes = listOf(newChild),
                )
            status.hasSentFullSnapshot = true
            status.hasSentMetaEvent = true
            status.lastSnapshot = oldWireframe
            val events =
                SnapshotPipeline.generateEvents(
                    wireframe = newWireframe,
                    status = status,
                    timestamp = 2000L,
                    view = view,
                    screenWidth = 1080,
                    screenHeight = 1920,
                )
            assertThat(events).hasSize(1)
            assertThat(events[0].type).isEqualTo(ReplayEventType.INCREMENTAL_SNAPSHOT)
        }

        @Test
        fun `generates IncrementalSnapshot with removes when wireframe has fewer nodes`() {
            val oldChild = ReplayWireframe(id = 2, x = 0, y = 100, width = 100, height = 50)
            val oldWireframe =
                ReplayWireframe(
                    id = 1,
                    x = 0,
                    y = 0,
                    width = 100,
                    height = 150,
                    childWireframes = listOf(oldChild),
                )
            val newWireframe = ReplayWireframe(id = 1, x = 0, y = 0, width = 100, height = 100)
            status.hasSentFullSnapshot = true
            status.hasSentMetaEvent = true
            status.lastSnapshot = oldWireframe
            val events =
                SnapshotPipeline.generateEvents(
                    wireframe = newWireframe,
                    status = status,
                    timestamp = 2000L,
                    view = view,
                    screenWidth = 1080,
                    screenHeight = 1920,
                )
            assertThat(events).hasSize(1)
            assertThat(events[0].type).isEqualTo(ReplayEventType.INCREMENTAL_SNAPSHOT)
        }

        @Test
        fun `generates IncrementalSnapshot with updates when wireframe content changed`() {
            val oldWireframe =
                ReplayWireframe(
                    id = 1,
                    x = 0,
                    y = 0,
                    width = 100,
                    height = 100,
                    text = "old",
                )
            val newWireframe =
                ReplayWireframe(
                    id = 1,
                    x = 0,
                    y = 0,
                    width = 100,
                    height = 100,
                    text = "new",
                )
            status.hasSentFullSnapshot = true
            status.hasSentMetaEvent = true
            status.lastSnapshot = oldWireframe
            val events =
                SnapshotPipeline.generateEvents(
                    wireframe = newWireframe,
                    status = status,
                    timestamp = 2000L,
                    view = view,
                    screenWidth = 1080,
                    screenHeight = 1920,
                )
            assertThat(events).hasSize(1)
            assertThat(events[0].type).isEqualTo(ReplayEventType.INCREMENTAL_SNAPSHOT)
        }

        @Test
        fun `no Meta event when hasSentMetaEvent is already true`() {
            val wireframe = ReplayWireframe(id = 1, x = 0, y = 0, width = 100, height = 100)
            status.hasSentFullSnapshot = true
            status.hasSentMetaEvent = true
            status.lastSnapshot = wireframe
            val events =
                SnapshotPipeline.generateEvents(
                    wireframe = wireframe,
                    status = status,
                    timestamp = 2000L,
                    view = view,
                    screenWidth = 1080,
                    screenHeight = 1920,
                )
            assertThat(events).noneMatch { it.type == ReplayEventType.META }
        }
    }
}

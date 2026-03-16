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
import com.pulse.android.sdk.replay.internal.util.DateProvider
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
    private lateinit var dateProvider: DateProvider

    @BeforeEach
    fun setUp() {
        view = mockk(relaxed = true)
        every { view.context } returns mockk<android.content.Context>(relaxed = true)
        val listener = mockk<NextDrawListener>(relaxed = true)
        val maskRectCache = mockk<MaskRectCache>(relaxed = true)
        status = ViewTreeSnapshotStatus(listener, maskRectCache)
        config = SessionReplayConfig()
        dateProvider = mockk(relaxed = true)
        every { dateProvider.currentTimeMillis() } returns 1000L
        every { dateProvider.nanoTime() } returns 1_000_000L
    }

    @Nested
    inner class FirstCall {
        @Test
        fun `generates Meta and FullSnapshot when sentFullSnapshot and sentMetaEvent are false`() {
            val wireframe = ReplayWireframe(id = 1, x = 0, y = 0, width = 100, height = 100)
            val events =
                SnapshotPipeline.generateEvents(
                    wireframe = wireframe,
                    status = status,
                    config = config,
                    timestamp = 1000L,
                    view = view,
                    screenWidth = 1080,
                    screenHeight = 1920,
                    dateProvider = dateProvider,
                )
            assertThat(events).hasSize(2)
            assertThat(events[0].type).isEqualTo(ReplayEventType.Meta)
            assertThat(events[1].type).isEqualTo(ReplayEventType.FullSnapshot)
            assertThat(status.sentMetaEvent).isTrue
            assertThat(status.sentFullSnapshot).isTrue
        }

        @Test
        fun `Meta event has correct screen dimensions`() {
            val wireframe = ReplayWireframe(id = 1, x = 0, y = 0, width = 100, height = 100)
            val events =
                SnapshotPipeline.generateEvents(
                    wireframe = wireframe,
                    status = status,
                    config = config,
                    timestamp = 1000L,
                    view = view,
                    screenWidth = 800,
                    screenHeight = 600,
                    dateProvider = dateProvider,
                )
            val metaEvent = events.first { it.type == ReplayEventType.Meta }
            val metaData = metaEvent.data as ReplayMetaData
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
                    config = config,
                    timestamp = 1000L,
                    view = view,
                    screenWidth = 1080,
                    screenHeight = 1920,
                    dateProvider = dateProvider,
                )
            val fullSnapshotEvent = events.first { it.type == ReplayEventType.FullSnapshot }
            val fullData = fullSnapshotEvent.data as ReplayFullSnapshotData
            assertThat(fullData.wireframes).hasSize(1)
            assertThat(fullData.wireframes[0].id).isEqualTo(42)
            assertThat(fullData.wireframes[0].width).isEqualTo(200)
            assertThat(fullData.wireframes[0].height).isEqualTo(150)
        }
    }

    @Nested
    inner class SecondCall {
        @Test
        fun `generates empty list when same wireframe and sentFullSnapshot is true`() {
            val wireframe = ReplayWireframe(id = 1, x = 0, y = 0, width = 100, height = 100)
            status.sentFullSnapshot = true
            status.sentMetaEvent = true
            status.lastSnapshot = wireframe
            val events =
                SnapshotPipeline.generateEvents(
                    wireframe = wireframe,
                    status = status,
                    config = config,
                    timestamp = 2000L,
                    view = view,
                    screenWidth = 1080,
                    screenHeight = 1920,
                    dateProvider = dateProvider,
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
            status.sentFullSnapshot = true
            status.sentMetaEvent = true
            status.lastSnapshot = oldWireframe
            val events =
                SnapshotPipeline.generateEvents(
                    wireframe = newWireframe,
                    status = status,
                    config = config,
                    timestamp = 2000L,
                    view = view,
                    screenWidth = 1080,
                    screenHeight = 1920,
                    dateProvider = dateProvider,
                )
            assertThat(events).hasSize(1)
            assertThat(events[0].type).isEqualTo(ReplayEventType.IncrementalSnapshot)
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
            status.sentFullSnapshot = true
            status.sentMetaEvent = true
            status.lastSnapshot = oldWireframe
            val events =
                SnapshotPipeline.generateEvents(
                    wireframe = newWireframe,
                    status = status,
                    config = config,
                    timestamp = 2000L,
                    view = view,
                    screenWidth = 1080,
                    screenHeight = 1920,
                    dateProvider = dateProvider,
                )
            assertThat(events).hasSize(1)
            assertThat(events[0].type).isEqualTo(ReplayEventType.IncrementalSnapshot)
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
            status.sentFullSnapshot = true
            status.sentMetaEvent = true
            status.lastSnapshot = oldWireframe
            val events =
                SnapshotPipeline.generateEvents(
                    wireframe = newWireframe,
                    status = status,
                    config = config,
                    timestamp = 2000L,
                    view = view,
                    screenWidth = 1080,
                    screenHeight = 1920,
                    dateProvider = dateProvider,
                )
            assertThat(events).hasSize(1)
            assertThat(events[0].type).isEqualTo(ReplayEventType.IncrementalSnapshot)
        }

        @Test
        fun `no Meta event when sentMetaEvent is already true`() {
            val wireframe = ReplayWireframe(id = 1, x = 0, y = 0, width = 100, height = 100)
            status.sentFullSnapshot = true
            status.sentMetaEvent = true
            status.lastSnapshot = wireframe
            val events =
                SnapshotPipeline.generateEvents(
                    wireframe = wireframe,
                    status = status,
                    config = config,
                    timestamp = 2000L,
                    view = view,
                    screenWidth = 1080,
                    screenHeight = 1920,
                    dateProvider = dateProvider,
                )
            assertThat(events).noneMatch { it.type == ReplayEventType.Meta }
        }
    }
}

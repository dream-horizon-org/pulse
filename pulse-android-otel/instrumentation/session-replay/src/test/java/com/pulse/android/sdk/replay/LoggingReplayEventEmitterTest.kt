package com.pulse.android.sdk.replay

import android.util.Log
import com.pulse.android.sdk.replay.events.ReplayMetaEvent
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test

class LoggingReplayEventEmitterTest {
    @Test
    fun `emit with empty events list does not crash`() {
        val emitter = LoggingReplayEventEmitter()
        assertThatCode { emitter.emit("session-1", emptyList()) }.doesNotThrowAnyException()
    }

    @Test
    fun `emit with non-empty events list does not crash`() {
        val emitter = LoggingReplayEventEmitter()
        val events = listOf(ReplayMetaEvent(1080, 1920, 1000L, "Test"))
        assertThatCode { emitter.emit("session-1", events) }.doesNotThrowAnyException()
    }

    @Test
    fun `constructor with default args works`() {
        assertThatCode { LoggingReplayEventEmitter() }.doesNotThrowAnyException()
    }

    @Test
    fun `constructor with custom tag and level works`() {
        val emitter = LoggingReplayEventEmitter(tag = "Replay", minLogLevel = Log.INFO)
        assertThatCode { emitter.emit("sid", listOf(ReplayMetaEvent(800, 600, 0L, ""))) }
            .doesNotThrowAnyException()
    }
}

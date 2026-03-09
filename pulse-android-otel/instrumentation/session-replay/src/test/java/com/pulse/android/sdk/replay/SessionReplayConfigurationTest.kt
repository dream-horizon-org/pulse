package com.pulse.android.sdk.replay

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SessionReplayConfigurationTest {

    @Test
    fun `getConfigIfConfigured returns null when not configured`() {
        val config = SessionReplayConfiguration()
        assertThat(config.getConfigIfConfigured()).isNull()
    }

    @Test
    fun `getConfigIfConfigured returns config with default values after markConfigured`() {
        val config = SessionReplayConfiguration()
        config.markConfigured()
        val built = config.getConfigIfConfigured()
        assertThat(built).isNotNull
        assertThat(built!!.maskAllTextInputs).isTrue()
        assertThat(built.screenshot).isTrue()
        assertThat(built.throttleDelayMs).isEqualTo(1000L)
        assertThat(built.flushIntervalSeconds).isEqualTo(60)
        assertThat(built.flushAt).isEqualTo(10)
        assertThat(built.maxBatchSize).isEqualTo(50)
        assertThat(built.replayApiBaseUrl).isNull()
    }

    @Test
    fun `getConfigIfConfigured returns config with custom values when set before markConfigured`() {
        val config = SessionReplayConfiguration().apply {
            screenshot = false
            throttleDelayMs = 2000L
            replayApiBaseUrl = "https://api.example.com"
            flushAt = 5
        }
        config.markConfigured()
        val built = config.getConfigIfConfigured()
        assertThat(built).isNotNull
        assertThat(built!!.screenshot).isFalse()
        assertThat(built.throttleDelayMs).isEqualTo(2000L)
        assertThat(built.replayApiBaseUrl).isEqualTo("https://api.example.com")
        assertThat(built.flushAt).isEqualTo(5)
    }
}

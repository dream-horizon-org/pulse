package com.pulse.android.sdk.replay

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class SessionReplayConfigValidationTest {
    @Nested
    inner class EffectiveScreenshotScale {
        @Test
        fun `clamps 0f to 0_01f`() {
            val config = SessionReplayConfig(screenshotScale = 0f)
            assertThat(config.effectiveScreenshotScale).isEqualTo(0.01f)
        }

        @Test
        fun `clamps -1f to 0_01f`() {
            val config = SessionReplayConfig(screenshotScale = -1f)
            assertThat(config.effectiveScreenshotScale).isEqualTo(0.01f)
        }

        @Test
        fun `clamps 2f to 1f`() {
            val config = SessionReplayConfig(screenshotScale = 2f)
            assertThat(config.effectiveScreenshotScale).isEqualTo(1f)
        }

        @Test
        fun `passes through 0_5f unchanged`() {
            val config = SessionReplayConfig(screenshotScale = 0.5f)
            assertThat(config.effectiveScreenshotScale).isEqualTo(0.5f)
        }
    }

    @Nested
    inner class EffectiveScreenshotQuality {
        @Test
        fun `clamps -10 to 0`() {
            val config = SessionReplayConfig(screenshotQuality = -10)
            assertThat(config.effectiveScreenshotQuality).isEqualTo(0)
        }

        @Test
        fun `clamps 200 to 100`() {
            val config = SessionReplayConfig(screenshotQuality = 200)
            assertThat(config.effectiveScreenshotQuality).isEqualTo(100)
        }

        @Test
        fun `passes through 50 unchanged`() {
            val config = SessionReplayConfig(screenshotQuality = 50)
            assertThat(config.effectiveScreenshotQuality).isEqualTo(50)
        }
    }

    @Nested
    inner class EffectiveThrottleDelayMs {
        @Test
        fun `clamps 0L to 100L`() {
            val config = SessionReplayConfig(throttleDelayMs = 0L)
            assertThat(config.effectiveThrottleDelayMs).isEqualTo(100L)
        }

        @Test
        fun `clamps -100L to 100L`() {
            val config = SessionReplayConfig(throttleDelayMs = -100L)
            assertThat(config.effectiveThrottleDelayMs).isEqualTo(100L)
        }

        @Test
        fun `passes through 500L unchanged`() {
            val config = SessionReplayConfig(throttleDelayMs = 500L)
            assertThat(config.effectiveThrottleDelayMs).isEqualTo(500L)
        }
    }

    @Nested
    inner class EffectiveFlushIntervalSeconds {
        @Test
        fun `clamps 0 to 1`() {
            val config = SessionReplayConfig(flushIntervalSeconds = 0)
            assertThat(config.effectiveFlushIntervalSeconds).isEqualTo(1)
        }
    }

    @Nested
    inner class EffectiveFlushAt {
        @Test
        fun `clamps 0 to 1`() {
            val config = SessionReplayConfig(flushAt = 0)
            assertThat(config.effectiveFlushAt).isEqualTo(1)
        }
    }

    @Nested
    inner class EffectiveMaxBatchSize {
        @Test
        fun `clamps 0 to 1`() {
            val config = SessionReplayConfig(maxBatchSize = 0)
            assertThat(config.effectiveMaxBatchSize).isEqualTo(1)
        }
    }

    @Nested
    inner class DefaultConfig {
        @Test
        fun `has sane effective values`() {
            val config = SessionReplayConfig()
            assertThat(config.effectiveScreenshotScale).isEqualTo(1f)
            assertThat(config.effectiveScreenshotQuality).isEqualTo(30)
            assertThat(config.effectiveThrottleDelayMs).isEqualTo(1000L)
            assertThat(config.effectiveFlushIntervalSeconds).isEqualTo(60)
            assertThat(config.effectiveFlushAt).isEqualTo(10)
            assertThat(config.effectiveMaxBatchSize).isEqualTo(50)
        }
    }

    @Nested
    inner class Screenshot {
        @Test
        fun `is always true`() {
            val config = SessionReplayConfig()
            assertThat(config.screenshot).isTrue()
        }
    }
}

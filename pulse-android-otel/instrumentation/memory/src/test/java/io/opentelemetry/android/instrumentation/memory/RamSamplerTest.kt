/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.memory

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import com.pulse.semconv.PulseDeviceAttributes
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.verify
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.logs.LogRecordBuilder
import io.opentelemetry.api.logs.Logger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RamSamplerTest {
    @MockK
    private lateinit var logger: Logger

    @MockK(relaxed = true)
    private lateinit var logRecordBuilder: LogRecordBuilder

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        every { logger.logRecordBuilder() } returns logRecordBuilder
        every { logRecordBuilder.setAllAttributes(any()) } returns logRecordBuilder
    }

    @Nested
    inner class FlushSamples {
        @Test
        fun `does not emit log when no samples collected`() {
            val sampler = buildSampler()

            sampler.flushSamples()

            verify(exactly = 0) { logger.logRecordBuilder() }
        }

        @Test
        fun `emits single log with utilization and timestamp arrays`() {
            val sampler = buildSampler()
            sampler.collectSample()
            sampler.collectSample()

            sampler.flushSamples()

            verify(exactly = 1) { logger.logRecordBuilder() }
            val attributesSlot = mutableListOf<Attributes>()
            verify(exactly = 1) { logRecordBuilder.setAllAttributes(capture(attributesSlot)) }
            val attrs = attributesSlot.first()
            assertThat(attrs.get(PulseDeviceAttributes.PULSE_SYSTEM_MEMORY_UTILIZATION_ARRAY))
                .hasSize(2)
            assertThat(attrs.get(PulseDeviceAttributes.PULSE_SYSTEM_MEMORY_UTILIZATION_TIMESTAMP_ARRAY))
                .hasSize(2)
            verify(exactly = 1) { logRecordBuilder.emit() }
        }

        @Test
        fun `clears buffer after flush so subsequent flush emits nothing`() {
            val sampler = buildSampler()
            sampler.collectSample()
            sampler.flushSamples()

            sampler.flushSamples()

            verify(exactly = 1) { logger.logRecordBuilder() }
        }

        @Test
        fun `utilization array contains used_ram_bytes and timestamp array contains epoch ms`() {
            val sampler = buildSampler()
            val beforeMs = System.currentTimeMillis()
            sampler.collectSample()
            val afterMs = System.currentTimeMillis()

            sampler.flushSamples()

            val attributesSlot = mutableListOf<Attributes>()
            verify { logRecordBuilder.setAllAttributes(capture(attributesSlot)) }
            val attrs = attributesSlot.first()

            val utilization = attrs.get(PulseDeviceAttributes.PULSE_SYSTEM_MEMORY_UTILIZATION_ARRAY)!!
            val timestamps = attrs.get(PulseDeviceAttributes.PULSE_SYSTEM_MEMORY_UTILIZATION_TIMESTAMP_ARRAY)!!
            assertThat(utilization).hasSize(1)
            assertThat(utilization.first()).isGreaterThanOrEqualTo(0L)
            assertThat(timestamps).hasSize(1)
            assertThat(timestamps.first()).isBetween(beforeMs, afterMs + 1)
        }
    }

    @Nested
    inner class ActiveFlag {
        @Test
        fun `collectSample skips when inactive`() {
            val sampler = buildSampler()
            sampler.setActive(false)
            sampler.collectSample()

            sampler.flushSamples()

            verify(exactly = 0) { logger.logRecordBuilder() }
        }

        @Test
        fun `collectSample resumes after reactivation`() {
            val sampler = buildSampler()
            sampler.setActive(false)
            sampler.collectSample()
            sampler.setActive(true)
            sampler.collectSample()

            sampler.flushSamples()

            val attributesSlot = mutableListOf<Attributes>()
            verify(exactly = 1) { logRecordBuilder.setAllAttributes(capture(attributesSlot)) }
            assertThat(
                attributesSlot
                    .first()
                    .get(PulseDeviceAttributes.PULSE_SYSTEM_MEMORY_UTILIZATION_ARRAY),
            ).hasSize(1)
        }
    }

    @Nested
    inner class Scheduling {
        @Test
        fun `sampling fires after DEFAULT_SAMPLE_INTERVAL_MS`() =
            runTest {
                val sampler = buildSampler(flushIntervalMs = 60_000L, dispatcher = StandardTestDispatcher(testScheduler))

                sampler.start()
                advanceTimeBy(RamSampler.DEFAULT_SAMPLE_INTERVAL_MS + 1)

                sampler.flushSamples()
                verify(atLeast = 1) { logger.logRecordBuilder() }

                sampler.shutdown()
            }

        @Test
        fun `flush fires after flushIntervalMs`() =
            runTest {
                val flushIntervalMs = 60_000L
                val sampler = buildSampler(flushIntervalMs = flushIntervalMs, dispatcher = StandardTestDispatcher(testScheduler))

                sampler.start()
                sampler.collectSample()
                advanceTimeBy(flushIntervalMs + 1)

                verify(atLeast = 1) { logger.logRecordBuilder() }

                sampler.shutdown()
            }

        @Test
        fun `shutdown cancels coroutines so no further samples or flushes occur`() =
            runTest {
                val sampler = buildSampler(flushIntervalMs = 60_000L, dispatcher = StandardTestDispatcher(testScheduler))

                sampler.start()
                sampler.shutdown()
                advanceTimeBy(RamSampler.DEFAULT_SAMPLE_INTERVAL_MS * 10)

                sampler.flushSamples()
                verify(exactly = 0) { logger.logRecordBuilder() }
            }
    }

    private fun buildSampler(
        flushIntervalMs: Long = RamUsageInstrumentation.DEFAULT_FLUSH_INTERVAL_MS,
        dispatcher: kotlinx.coroutines.CoroutineDispatcher = StandardTestDispatcher(),
    ): RamSampler {
        val activityManager = mockk<ActivityManager>(relaxed = true)
        val app =
            mockk<Application>(relaxed = true) {
                every { getSystemService(Context.ACTIVITY_SERVICE) } returns activityManager
            }
        return RamSampler(
            application = app,
            logger = logger,
            flushIntervalMs = flushIntervalMs,
            sampleIntervalMs = RamUsageInstrumentation.DEFAULT_SAMPLE_INTERVAL_MS,
            dispatcher = dispatcher,
        )
    }
}

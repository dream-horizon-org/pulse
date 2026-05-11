/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.battery

import android.app.Application
import com.pulse.semconv.PulseAttributes
import com.pulse.semconv.PulseDeviceAttributes
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
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
class BatterySamplerTest {
    @MockK
    private lateinit var logger: Logger

    @MockK(relaxed = true)
    private lateinit var logRecordBuilder: LogRecordBuilder

    @MockK
    private lateinit var application: Application

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        every { logger.logRecordBuilder() } returns logRecordBuilder
        every { logRecordBuilder.setEventName(any<String>()) } returns logRecordBuilder
        every { logRecordBuilder.setAllAttributes(any()) } returns logRecordBuilder
        every { application.applicationContext } returns application
    }

    @Nested
    inner class FlushSamples {
        @Test
        fun `does not emit log when no samples collected`() {
            val sampler = buildSampler(read = { null })

            sampler.flushSamples()

            verify(exactly = 0) { logger.logRecordBuilder() }
        }

        @Test
        fun `emits single log with level plugged and timestamp arrays`() {
            val sampler =
                buildSampler(read = { BatterySample(42.0, PlugType.USB) })
            sampler.collectSample()
            sampler.collectSample()

            sampler.flushSamples()

            verify(exactly = 1) { logger.logRecordBuilder() }
            verify(exactly = 1) { logRecordBuilder.setEventName(PulseAttributes.PulseTypeValues.BATTERY) }
            val attributesSlot = mutableListOf<Attributes>()
            verify(exactly = 1) { logRecordBuilder.setAllAttributes(capture(attributesSlot)) }
            val attrs = attributesSlot.first()
            assertThat(attrs.get(PulseDeviceAttributes.PULSE_SYSTEM_BATTERY_LEVEL_ARRAY)).containsExactly(42.0)
            assertThat(attrs.get(PulseDeviceAttributes.PULSE_SYSTEM_BATTERY_PLUGGED_ARRAY))
                .containsExactly(PlugType.USB.value)
            assertThat(attrs.get(PulseDeviceAttributes.PULSE_SYSTEM_BATTERY_TIMESTAMP_ARRAY)).hasSize(1)
            assertThat(attrs.get(PulseAttributes.PULSE_TYPE)).isEqualTo(PulseAttributes.PulseTypeValues.BATTERY)
            verify(exactly = 1) { logRecordBuilder.emit() }
        }

        @Test
        fun `records new entry only when level or plug state changes`() {
            var state = BatterySample(40.0, PlugType.BATTERY)
            val sampler =
                buildSampler(read = { state })
            sampler.collectSample()
            state = BatterySample(40.0, PlugType.BATTERY)
            sampler.collectSample()
            state = BatterySample(41.0, PlugType.BATTERY)
            sampler.collectSample()
            state = BatterySample(41.0, PlugType.USB)
            sampler.collectSample()

            sampler.flushSamples()

            val attributesSlot = mutableListOf<Attributes>()
            verify { logRecordBuilder.setAllAttributes(capture(attributesSlot)) }
            val attrs = attributesSlot.first()
            assertThat(attrs.get(PulseDeviceAttributes.PULSE_SYSTEM_BATTERY_LEVEL_ARRAY))
                .containsExactly(40.0, 41.0, 41.0)
            assertThat(attrs.get(PulseDeviceAttributes.PULSE_SYSTEM_BATTERY_PLUGGED_ARRAY))
                .containsExactly(
                    PlugType.BATTERY.value,
                    PlugType.BATTERY.value,
                    PlugType.USB.value,
                )
            assertThat(attrs.get(PulseDeviceAttributes.PULSE_SYSTEM_BATTERY_TIMESTAMP_ARRAY)).hasSize(3)
        }

        @Test
        fun `does not emit second log when state unchanged after previous flush`() {
            val sampler = buildSampler(read = { BatterySample(55.0, PlugType.AC) })
            sampler.collectSample()
            sampler.flushSamples()
            sampler.collectSample()
            sampler.flushSamples()

            verify(exactly = 1) { logger.logRecordBuilder() }
        }

        @Test
        fun `clears buffer after flush so subsequent flush emits nothing`() {
            val sampler = buildSampler(read = { BatterySample(10.0, PlugType.BATTERY) })
            sampler.collectSample()
            sampler.flushSamples()

            sampler.flushSamples()

            verify(exactly = 1) { logger.logRecordBuilder() }
        }
    }

    @Nested
    inner class Scheduling {
        @Test
        fun `sampling fires after defaultSampleIntervalMs`() =
            runTest {
                val sampler =
                    buildSampler(
                        read = { BatterySample(1.0, PlugType.BATTERY) },
                        flushIntervalMs = 60_000L,
                        dispatcher = StandardTestDispatcher(testScheduler),
                    )

                sampler.start()
                advanceTimeBy(BatterySampler.defaultSampleIntervalMs + 1)

                sampler.flushSamples()
                verify(atLeast = 1) { logger.logRecordBuilder() }

                sampler.shutdown()
            }

        @Test
        fun `shutdown cancels coroutines so no further samples or flushes occur`() =
            runTest {
                val sampler =
                    buildSampler(
                        read = { BatterySample(1.0, PlugType.BATTERY) },
                        flushIntervalMs = 60_000L,
                        dispatcher = StandardTestDispatcher(testScheduler),
                    )

                sampler.start()
                sampler.shutdown()
                advanceTimeBy(BatterySampler.defaultSampleIntervalMs * 10)

                sampler.flushSamples()
                verify(exactly = 0) { logger.logRecordBuilder() }
            }
    }

    private fun buildSampler(
        read: (Application) -> BatterySample?,
        flushIntervalMs: Long = 60_000L,
        sampleIntervalMs: Long = BatterySampler.defaultSampleIntervalMs,
        dispatcher: kotlinx.coroutines.CoroutineDispatcher = StandardTestDispatcher(),
    ): BatterySampler =
        BatterySampler(
            application,
            logger,
            flushIntervalMs = flushIntervalMs,
            sampleIntervalMs = sampleIntervalMs,
            dispatcher = dispatcher,
            readSnapshot = read,
        )
}

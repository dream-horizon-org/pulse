/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.agent.dsl.instrumentation

import io.opentelemetry.android.agent.dsl.OpenTelemetryDslMarker
import io.opentelemetry.android.config.OtelRumConfig
import io.opentelemetry.android.instrumentation.AndroidInstrumentationLoader
import io.opentelemetry.android.instrumentation.battery.BatteryUsageInstrumentation

@OpenTelemetryDslMarker
class BatteryUsageConfiguration internal constructor(
    private val config: OtelRumConfig,
) : CanBeEnabledAndDisabled {
    private val batteryUsageInstrumentation: BatteryUsageInstrumentation by lazy {
        AndroidInstrumentationLoader.getInstrumentation(
            BatteryUsageInstrumentation::class.java,
        )
    }

    /**
     * Configures how often accumulated battery samples are flushed as a log record.
     * Defaults to [BatteryUsageInstrumentation.defaultFlushIntervalMs] ms. Must be positive.
     */
    fun setFlushIntervalMs(intervalMs: Long): BatteryUsageConfiguration =
        apply {
            batteryUsageInstrumentation.setFlushIntervalMs(intervalMs)
        }

    /**
     * Configures how frequently battery state is sampled.
     * Defaults to [BatteryUsageInstrumentation.defaultSampleIntervalMs] ms. Must be positive.
     */
    fun setSampleIntervalMs(intervalMs: Long): BatteryUsageConfiguration =
        apply {
            batteryUsageInstrumentation.setSampleIntervalMs(intervalMs)
        }

    override fun enabled(enabled: Boolean) {
        if (enabled) {
            config.allowInstrumentation(batteryUsageInstrumentation.name)
        } else {
            config.suppressInstrumentation(batteryUsageInstrumentation.name)
        }
    }
}

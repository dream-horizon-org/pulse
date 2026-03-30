/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.agent.dsl.instrumentation

import io.opentelemetry.android.agent.dsl.OpenTelemetryDslMarker
import io.opentelemetry.android.config.OtelRumConfig
import io.opentelemetry.android.instrumentation.AndroidInstrumentationLoader
import io.opentelemetry.android.instrumentation.memory.RamUsageInstrumentation

@OpenTelemetryDslMarker
class RamUsageConfiguration internal constructor(
    private val config: OtelRumConfig,
) : CanBeEnabledAndDisabled {
    private val ramUsageInstrumentation: RamUsageInstrumentation by lazy {
        AndroidInstrumentationLoader.getInstrumentation(
            RamUsageInstrumentation::class.java,
        )
    }

    /**
     * Configures how often accumulated RAM samples are flushed as a log record.
     * Defaults to [RamUsageInstrumentation.DEFAULT_FLUSH_INTERVAL_MS] ms. Must be positive.
     *
     * @param intervalMs flush interval in milliseconds
     */
    fun setFlushIntervalMs(intervalMs: Long): RamUsageConfiguration =
        apply {
            ramUsageInstrumentation.setFlushIntervalMs(intervalMs)
        }

    /**
     * Configures how frequently a RAM sample is taken.
     * Defaults to [RamUsageInstrumentation.DEFAULT_SAMPLE_INTERVAL_MS] ms. Must be positive.
     *
     * @param intervalMs sample interval in milliseconds
     */
    fun setSampleIntervalMs(intervalMs: Long): RamUsageConfiguration =
        apply {
            ramUsageInstrumentation.setSampleIntervalMs(intervalMs)
        }

    override fun enabled(enabled: Boolean) {
        if (enabled) {
            config.allowInstrumentation(ramUsageInstrumentation.name)
        } else {
            config.suppressInstrumentation(ramUsageInstrumentation.name)
        }
    }
}

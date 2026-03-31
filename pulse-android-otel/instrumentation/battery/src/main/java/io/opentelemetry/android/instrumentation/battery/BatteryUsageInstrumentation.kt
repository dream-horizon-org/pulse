/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.battery

import com.google.auto.service.AutoService
import com.pulse.utils.PulseOtelUtils
import io.opentelemetry.android.instrumentation.AndroidInstrumentation
import io.opentelemetry.android.instrumentation.InstallationContext
import io.opentelemetry.android.instrumentation.battery.BatteryUsageInstrumentation.Companion.defaultFlushIntervalMs
import io.opentelemetry.android.instrumentation.battery.BatteryUsageInstrumentation.Companion.defaultSampleIntervalMs
import io.opentelemetry.android.internal.services.Services.Companion.get
import io.opentelemetry.android.internal.services.applifecycle.ApplicationStateListener
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Instrumentation that periodically samples battery level and plug state and flushes accumulated samples as a single OpenTelemetry
 * log record.
 *
 * Sampling is paused while the app is in the background.
 */
@AutoService(AndroidInstrumentation::class)
class BatteryUsageInstrumentation
    @JvmOverloads
    constructor(
        private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    ) : AndroidInstrumentation {
        private var flushIntervalMs: Long = defaultFlushIntervalMs
        private var sampleIntervalMs: Long = defaultSampleIntervalMs

        @Volatile
        private var sampler: BatterySampler? = null

        fun setFlushIntervalMs(intervalMs: Long): BatteryUsageInstrumentation {
            if (intervalMs <= 0) {
                PulseOtelUtils.logError(
                    BatteryUsageInstrumentation::class.java.name,
                ) {
                    "Invalid flushIntervalMs: $intervalMs; must be positive"
                }
                return this
            }
            flushIntervalMs = intervalMs
            return this
        }

        fun setSampleIntervalMs(intervalMs: Long): BatteryUsageInstrumentation {
            if (intervalMs <= 0) {
                PulseOtelUtils.logError(
                    BatteryUsageInstrumentation::class.java.name,
                ) {
                    "Invalid sampleIntervalMs: $intervalMs; must be positive"
                }
                return this
            }
            sampleIntervalMs = intervalMs
            return this
        }

        override fun install(ctx: InstallationContext) {
            if (sampler != null) {
                PulseOtelUtils.logDebug(
                    BatteryUsageInstrumentation::class.java.name,
                ) {
                    "BatteryUsageInstrumentation skipping installation (sampler already installed)"
                }
                return
            }
            val logger = ctx.openTelemetry.logsBridge["io.opentelemetry.battery"]
            val batterySampler =
                BatterySampler(ctx.application, logger, flushIntervalMs, sampleIntervalMs, dispatcher)
            sampler = batterySampler
            batterySampler.start()
            get(ctx.application).appLifecycle.registerListener(
                object : ApplicationStateListener {
                    override fun onApplicationForegrounded() {
                        batterySampler.setActive(true)
                    }

                    override fun onApplicationBackgrounded() {
                        batterySampler.setActive(false)
                    }
                },
            )
        }

        override fun uninstall(ctx: InstallationContext) {
            sampler?.shutdown()
            sampler = null
        }

        override val name: String = "battery"

        companion object {
            val defaultFlushIntervalMs: Long = if (PulseOtelUtils.isDebug()) 1_20_000L else 9_00_000L
            val defaultSampleIntervalMs: Long = BatterySampler.defaultSampleIntervalMs
        }
    }

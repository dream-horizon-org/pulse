/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.memory

import com.google.auto.service.AutoService
import com.pulse.utils.PulseLogger
import com.pulse.utils.PulseOtelUtils
import io.opentelemetry.android.instrumentation.AndroidInstrumentation
import io.opentelemetry.android.instrumentation.InstallationContext
import io.opentelemetry.android.instrumentation.memory.RamUsageInstrumentation.Companion.DEFAULT_FLUSH_INTERVAL_MS
import io.opentelemetry.android.instrumentation.memory.RamUsageInstrumentation.Companion.DEFAULT_SAMPLE_INTERVAL_MS
import io.opentelemetry.android.internal.services.Services.Companion.get
import io.opentelemetry.android.internal.services.applifecycle.ApplicationStateListener
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Instrumentation that periodically samples device RAM usage and emits the accumulated
 * samples as a single OpenTelemetry log record with a JSON array body.
 *
 * Samples are collected at a configurable interval (default: [DEFAULT_SAMPLE_INTERVAL_MS])
 * and flushed at a configurable interval (default: [DEFAULT_FLUSH_INTERVAL_MS]).
 * Sampling is paused while the app is in the background.
 */
@AutoService(AndroidInstrumentation::class)
class RamUsageInstrumentation
    @JvmOverloads
    constructor(
        private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    ) : AndroidInstrumentation {
        private var flushIntervalMs: Long = DEFAULT_FLUSH_INTERVAL_MS
        private var sampleIntervalMs: Long = DEFAULT_SAMPLE_INTERVAL_MS

        @Volatile
        private var sampler: RamSampler? = null

        /**
         * Configures the interval at which accumulated RAM samples are flushed as a log record.
         *
         * @param intervalMs flush interval in milliseconds; must be positive
         * @return `this`
         */
        fun setFlushIntervalMs(intervalMs: Long): RamUsageInstrumentation {
            if (intervalMs <= 0) {
                PulseLogger.logError(
                    RamUsageInstrumentation::class.java.name,
                ) {
                    "Invalid flushIntervalMs: $intervalMs; must be positive"
                }
                return this
            }
            flushIntervalMs = intervalMs
            return this
        }

        /**
         * Configures how frequently a RAM sample is taken.
         *
         * @param intervalMs sample interval in milliseconds; must be positive
         * @return `this`
         */
        fun setSampleIntervalMs(intervalMs: Long): RamUsageInstrumentation {
            if (intervalMs <= 0) {
                PulseLogger.logError(
                    RamUsageInstrumentation::class.java.name,
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
                PulseLogger.logDebug(
                    RamUsageInstrumentation::class.java.name,
                ) {
                    "RamUsageInstrumentation skipping installation (sampler already installed)"
                }
                return
            }
            val logger = ctx.openTelemetry.logsBridge["io.opentelemetry.memory"]
            val ramSampler = RamSampler(ctx.application, logger, flushIntervalMs, sampleIntervalMs, dispatcher)
            sampler = ramSampler
            ramSampler.start()
            get(ctx.application).appLifecycle.registerListener(
                object : ApplicationStateListener {
                    override fun onApplicationForegrounded() {
                        ramSampler.setActive(true)
                    }

                    override fun onApplicationBackgrounded() {
                        ramSampler.setActive(false)
                    }
                },
            )
        }

        override fun uninstall(ctx: InstallationContext) {
            sampler?.shutdown()
            sampler = null
        }

        override val name: String = "memory"

        companion object {
            const val DEFAULT_FLUSH_INTERVAL_MS = 15L * 60L * 1000L
            const val DEFAULT_SAMPLE_INTERVAL_MS = RamSampler.DEFAULT_SAMPLE_INTERVAL_MS
        }
    }

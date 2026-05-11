/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.ccrash

import com.google.auto.service.AutoService
import com.pulse.utils.PulseLogger
import io.opentelemetry.android.instrumentation.AndroidInstrumentation
import io.opentelemetry.android.instrumentation.InstallationContext
import io.opentelemetry.sdk.OpenTelemetrySdk

@AutoService(AndroidInstrumentation::class)
class CCrashInstrumentation : AndroidInstrumentation {
    override fun install(ctx: InstallationContext) {
        PulseLogger.logDebug(TAG) { "install start" }

        val openTelemetry = ctx.openTelemetry as OpenTelemetrySdk
        val reportsDir = PulseNativeCrashStorage.reportsDir(ctx.application)

        PulseLogger.logDebug(TAG) { "reportsDir=${reportsDir.absolutePath}" }

        PulseNativeCrashReportDrain.drainAndEmit(openTelemetry, reportsDir)

        val installed = PulseNativeJni.install(reportsDir.absolutePath)
        PulseLogger.logDebug(TAG) { "signal handler installed=$installed" }
    }

    override val name: String = INSTRUMENTATION_NAME

    companion object {
        internal const val INSTRUMENTATION_NAME = "c-crash"
        internal const val TAG = "CCrash"
    }
}


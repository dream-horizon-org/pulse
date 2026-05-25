/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.ccrash

import com.google.auto.service.AutoService
import com.pulse.utils.PulseLogger
import io.opentelemetry.android.instrumentation.AndroidInstrumentation
import io.opentelemetry.android.instrumentation.InstallationContext
import io.opentelemetry.android.internal.services.metadata.PulseMetadataInstaller
import io.opentelemetry.sdk.OpenTelemetrySdk

@AutoService(AndroidInstrumentation::class)
class CCrashInstrumentation : AndroidInstrumentation {
    private var metaDataInstaller: PulseMetadataInstaller? = null

    override fun install(ctx: InstallationContext) {
        PulseLogger.logDebug(TAG) { "install start" }

        val openTelemetry = ctx.openTelemetry as OpenTelemetrySdk
        val reportsDir = PulseNativeCrashStorage.reportsDir(ctx.application)

        PulseLogger.logDebug(TAG) { "reportsDir=${reportsDir.absolutePath}" }

        PulseNativeCrashReportDrain.drainAndEmit(openTelemetry, reportsDir, ctx.application)

        metaDataInstaller = PulseMetadataInstaller.get(ctx.application).also {
            it.install(ctx.sessionProvider)
        }

        val installed = PulseNativeJni.install(reportsDir.absolutePath)
        PulseLogger.logDebug(TAG) { "signal handler installed=$installed" }
    }

    override fun uninstall(ctx: InstallationContext) {
        metaDataInstaller?.close()
        metaDataInstaller = null
    }

    override val name: String = INSTRUMENTATION_NAME

    companion object {
        internal const val INSTRUMENTATION_NAME = "c-crash"
        internal const val TAG = "CCrash"
    }
}

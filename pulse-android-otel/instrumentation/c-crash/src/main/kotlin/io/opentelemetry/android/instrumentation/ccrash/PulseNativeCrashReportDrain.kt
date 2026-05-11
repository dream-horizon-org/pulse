/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.ccrash

import com.pulse.utils.PulseLogger
import com.pulse.utils.PulseSerialisationUtils
import com.pulse.utils.fromJson
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_MESSAGE
import io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_STACKTRACE
import io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_TYPE
import io.opentelemetry.semconv.incubating.ThreadIncubatingAttributes.THREAD_ID
import io.opentelemetry.semconv.incubating.ThreadIncubatingAttributes.THREAD_NAME
import java.io.File
import kotlinx.serialization.decodeFromString

internal object PulseNativeCrashReportDrain {
    fun drainAndEmit(
        openTelemetry: OpenTelemetrySdk,
        reportsDir: File,
    ) {
        val reports = reportsDir.listFiles { f -> f.isFile && f.name.endsWith(".json") }?.toList().orEmpty()
        PulseLogger.logDebug(CCrashInstrumentation.TAG) { "drain report files count=${reports.size}" }

        for (file in reports) {
            try {
                PulseLogger.logDebug(CCrashInstrumentation.TAG) { "drain reading ${file.absolutePath}" }
                file.readText().fromJson<PulseNativeCrashReportFile>("drainAndEmit")?.let {
                    val attributes = toAttributes(it)
                    openTelemetry
                        .sdkLoggerProvider
                        .loggerBuilder("io.opentelemetry.c-crash")
                        .build()
                        .logRecordBuilder()
                        .setEventName("device.crash")
                        .setAllAttributes(attributes)
                        .emit()
                    PulseLogger.logDebug(CCrashInstrumentation.TAG) { "drain emitted; deleting ${file.name}" }
                } ?: run {
                    PulseLogger.logError(CCrashInstrumentation.TAG) { "drain failed in parsing for ${file.name}" }
                }
                file.delete()
            } catch (t: Throwable) {
                PulseLogger.logError(CCrashInstrumentation.TAG, t) { "drain failed for ${file.name}" }
            }
        }
    }

    private fun toAttributes(report: PulseNativeCrashReportFile): Attributes {
        val stackStr =
            report.stack
                ?.joinToString("\n")
                ?.takeIf { it.isNotBlank() }

        val message =
            buildString {
                append("Native crash")
                when {
                    report.signalName != null && report.signal != null ->
                        append(": ").append(report.signalName).append(" (").append(report.signal).append(")")
                    report.signalName != null ->
                        append(": ").append(report.signalName)
                    report.signal != null ->
                        append(": signal ").append(report.signal)
                }
                report.faultAddr?.takeIf { it.isNotBlank() }?.let {
                    append(" addr=").append(it)
                }
            }

        return Attributes
            .builder()
            .put(EXCEPTION_TYPE, "NativeCrash")
            .put(EXCEPTION_MESSAGE, message)
            .apply {
                report.threadName?.let { put(THREAD_NAME, it) }
                report.tid?.let { put(THREAD_ID, it) }
                if (!stackStr.isNullOrBlank()) {
                    put(EXCEPTION_STACKTRACE, stackStr)
                }
            }.build()
    }
}


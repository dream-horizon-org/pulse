/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.ccrash

import android.app.Application
import com.pulse.utils.PulseLogger
import com.pulse.utils.fromJson
import io.opentelemetry.android.internal.services.metadata.PulseAppMetadata
import io.opentelemetry.android.internal.services.metadata.PulseMetadataUpdater
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.common.AttributesBuilder
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_MESSAGE
import io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_STACKTRACE
import io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_TYPE
import io.opentelemetry.semconv.incubating.ThreadIncubatingAttributes.THREAD_ID
import io.opentelemetry.semconv.incubating.ThreadIncubatingAttributes.THREAD_NAME
import java.io.File

internal object PulseNativeCrashReportDrain {
    fun drainAndEmit(
        openTelemetry: OpenTelemetrySdk,
        reportsDir: File,
        application: Application,
    ) {
        val reports = reportsDir.listFiles { f -> f.isFile && f.name.endsWith(".json") }?.toList().orEmpty()
        PulseLogger.logDebug(CCrashInstrumentation.TAG) { "drain report files count=${reports.size}" }
        val crashMetadata = PulseMetadataUpdater.readCached(application)

        for (file in reports) {
            try {
                PulseLogger.logDebug(CCrashInstrumentation.TAG) { "drain reading ${file.absolutePath}" }
                file.readText().fromJson<PulseNativeCrashReportFile>("drainAndEmit")?.let {
                    PulseLogger.logDebug(CCrashInstrumentation.TAG) {
                        "PulseNativeCrashReportFile\n$it"
                    }
                    val attributes = toAttributes(it, crashMetadata)
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
            } finally {
                file.delete()
            }
        }
    }

    /**
     * Formatter used to represents rel_pc (offset within .so) for the pc column to
     * match the tombstone-style format produced by the C++ format_stacktrace function.
     */
    private fun formatStackFrames(frames: List<PulseNativeStackFrame>): String =
        frames.mapIndexed { index, frame ->
            buildString {
                append('#').append(index)
                frame.relPc?.let { pc ->
                    append(" pc 0x").append(java.lang.Long.toHexString(pc).padStart(16, '0'))
                }
                frame.filename?.takeIf { it.isNotBlank() }?.let { append(" ").append(it) }
                if (frame.method?.isNotBlank() == true) {
                    val offset = if (frame.frameAddress != null && frame.symbolAddress != null &&
                        frame.frameAddress >= frame.symbolAddress && frame.symbolAddress != 0L
                    ) frame.frameAddress - frame.symbolAddress else 0L
                    append(" (").append(frame.method)
                    if (offset > 0) append("+0x").append(java.lang.Long.toHexString(offset))
                    append(')')
                }
            }
        }.joinToString("\n")

    private fun toAttributes(
        report: PulseNativeCrashReportFile,
        appMetadataAtCrashTime: PulseAppMetadata?,
    ): Attributes {
        val stackStr =
            report.stackFrames
                ?.takeIf { it.isNotEmpty() }
                ?.let { formatStackFrames(it).takeIf { s -> s.isNotBlank() } }

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

        return Attributes.builder()
            .apply {
                put(EXCEPTION_TYPE, "NativeCrash")
                put(EXCEPTION_MESSAGE, message)
                report.threadName?.let { put(THREAD_NAME, it) }
                report.tid?.let { put(THREAD_ID, it) }
                if (!stackStr.isNullOrBlank()) {
                    put(EXCEPTION_STACKTRACE, stackStr)
                }
                appMetadataAtCrashTime?.let { putMetadata(appMetadataAtCrashTime) }
            }.build()
    }

    private fun AttributesBuilder.putMetadata(crashMetadata: PulseAppMetadata): AttributesBuilder = apply {
        crashMetadata.stringFields.forEach { (key, value) ->
            put(AttributeKey.stringKey(key), value)
        }
        crashMetadata.longFields.forEach { (key, value) ->
            put(AttributeKey.longKey(key), value)
        }
        crashMetadata.doubleFields.forEach { (key, value) ->
            put(AttributeKey.doubleKey(key), value)
        }
        crashMetadata.booleanFields.forEach { (key, value) ->
            put(AttributeKey.booleanKey(key), value)
        }
    }
}


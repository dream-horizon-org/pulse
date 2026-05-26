/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.ccrash

import com.pulse.semconv.PulseAttributes
import com.pulse.utils.PulseLogger
import com.pulse.utils.fromJson
import io.opentelemetry.android.internal.services.metadata.PulseAppMetadata
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.common.AttributesBuilder
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_MESSAGE
import io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_STACKTRACE
import io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_TYPE
import io.opentelemetry.semconv.incubating.SessionIncubatingAttributes.SESSION_ID
import io.opentelemetry.semconv.incubating.ThreadIncubatingAttributes.THREAD_ID
import io.opentelemetry.semconv.incubating.ThreadIncubatingAttributes.THREAD_NAME
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit

internal object PulseNativeCrashReportDrain : CoroutineScope by MainScope() {
    fun drainAndEmit(
        openTelemetry: OpenTelemetrySdk,
        reportsDir: File,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): Job =
        launch(dispatcher) {
            val crashDirs = reportsDir.listFiles { f -> f.isDirectory }?.toList().orEmpty()
            PulseLogger.logDebug(CCrashInstrumentation.TAG) { "drain crash dirs count=${crashDirs.size}" }

            for (crashDir in crashDirs) {
                PulseLogger.logDebug(CCrashInstrumentation.TAG) {
                    "Found native crash with file $crashDir"
                }
                val (crashTimeMs, sessionId) = parseCrashDirName(crashDir.name)
                if (crashTimeMs == null && sessionId == null) {
                    PulseLogger.logError(CCrashInstrumentation.TAG) {
                        "invalid crash dir name ${crashDir.name}; expected {timestamp_ms}_{session_id}"
                    }
                }

                val metadataFile = File(crashDir, CCrashInstrumentation.METADATA_FILE_NAME)
                val crashMetadata =
                    metadataFile
                        .takeIf { it.isFile && it.exists() }
                        ?.run {
                            readText().fromJson<PulseAppMetadata>(CCrashInstrumentation.TAG)
                        } ?: run {
                        PulseLogger.logError(CCrashInstrumentation.TAG) {
                            "metadata file is absent at ${metadataFile.absolutePath}"
                        }
                        null
                    }

                val crashFile =
                    File(crashDir, CCrashInstrumentation.CRASH_FILE_NAME)
                        .takeIf { it.isFile && it.exists() }

                try {
                    if (crashFile == null) {
                        PulseLogger.logError(CCrashInstrumentation.TAG) {
                            "crash file is absent at ${crashDir.absolutePath}"
                        }
                    } else {
                        PulseLogger.logDebug(CCrashInstrumentation.TAG) { "drain reading ${crashFile.absolutePath}" }
                        crashFile.readText().fromJson<PulseNativeCrashReportFile>("drainAndEmit")?.let {
                            PulseLogger.logDebug(CCrashInstrumentation.TAG) {
                                "PulseNativeCrashReportFile\n$it"
                            }
                            val attributes = toAttributes(it, crashMetadata, sessionId)
                            val logRecordBuilder =
                                openTelemetry
                                    .sdkLoggerProvider
                                    .loggerBuilder("io.opentelemetry.c-crash")
                                    .build()
                                    .logRecordBuilder()
                                    .setEventName("device.crash")
                                    .setAllAttributes(attributes)

                            crashTimeMs?.let { crashTime ->
                                logRecordBuilder.setTimestamp(crashTime, TimeUnit.MILLISECONDS)
                            }
                            logRecordBuilder.setObservedTimestamp(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
                            logRecordBuilder.emit()
                            openTelemetry.sdkLoggerProvider.forceFlush()
                            PulseLogger.logDebug(CCrashInstrumentation.TAG) { "drain emitted; deleting ${crashFile.name}" }
                        } ?: run {
                            PulseLogger.logError(CCrashInstrumentation.TAG) {
                                "drain failed in parsing for ${crashFile.name}"
                            }
                        }
                    }
                } finally {
                    crashDir.deleteRecursively()
                }
            }
        }

    internal fun parseCrashDirName(dirName: String): Pair<Long?, String?> {
        val separatorIndex = dirName.indexOf('_')
        if (separatorIndex <= 0) {
            return null to null
        }
        val crashTimeMs = dirName.substring(0, separatorIndex).toLongOrNull()
        val sessionId = dirName.substring(separatorIndex + 1).takeIf { it.isNotBlank() }
        return crashTimeMs to sessionId
    }

    /**
     * Tombstone-style stack lines from hex address strings emitted by C++ `write_report`.
     */
    private fun formatStackFrames(frames: List<PulseNativeStackFrame>): String =
        frames
            .mapIndexed { index, frame ->
                buildString {
                    append('#').append(index)
                    frame.relPc?.takeIf { it.isNotBlank() }?.let { pc ->
                        append(" pc ").append(formatPcColumn(pc))
                    }
                    frame.filename?.takeIf { it.isNotBlank() }?.let { append(" ").append(it) }
                    if (frame.method?.isNotBlank() == true) {
                        append(" (").append(frame.method)
                        formatSymbolOffsetSuffix(frame.symbolOffset)?.let { append(it) }
                        append(')')
                    }
                }
            }.joinToString("\n")

    private fun formatPcColumn(hex: String): String {
        val digits = hex.trim().removePrefix("0x").removePrefix("0X")
        if (digits.isEmpty()) {
            return hex
        }
        return "0x${digits.lowercase().padStart(16, '0')}"
    }

    private fun formatSymbolOffsetSuffix(offset: String?): String? {
        val digits = offset?.trim()?.removePrefix("0x")?.removePrefix("0X")?.lowercase().orEmpty()
        if (digits.isEmpty() || digits.all { it == '0' }) {
            return null
        }
        return "+0x$digits"
    }

    private fun toAttributes(
        report: PulseNativeCrashReportFile,
        appMetadataAtCrashTime: PulseAppMetadata?,
        sessionId: String?,
    ): Attributes {
        val stackStr =
            report.stackFrames
                ?.takeIf { it.isNotEmpty() }
                ?.let { formatStackFrames(it).takeIf { s -> s.isNotBlank() } }

        val message =
            buildString {
                append("Native crash")
                when {
                    report.signalName != null && report.signal != null -> {
                        append(": ")
                            .append(report.signalName)
                            .append(" (")
                            .append(report.signal)
                            .append(")")
                    }

                    report.signalName != null -> {
                        append(": ").append(report.signalName)
                    }

                    report.signal != null -> {
                        append(": signal ").append(report.signal)
                    }
                }
                report.faultAddr?.takeIf { it.isNotBlank() }?.let {
                    append(" addr=").append(it)
                }
            }

        return Attributes
            .builder()
            .apply {
                put(EXCEPTION_TYPE, "NativeCrash")
                put(EXCEPTION_MESSAGE, message)
                if (report.threadName != null) put(THREAD_NAME, report.threadName)
                if (report.tid != null) put(THREAD_ID, report.tid)
                if (!report.binaryArch.isNullOrBlank()) {
                    put(PulseAttributes.PULSE_NATIVE_BINARY_ARCH, report.binaryArch)
                }
                if (!stackStr.isNullOrBlank()) {
                    put(EXCEPTION_STACKTRACE, stackStr)
                }
                if (appMetadataAtCrashTime != null) putMetadata(appMetadataAtCrashTime)
                sessionId?.let { put(SESSION_ID, it) }
            }.build()
    }

    private fun AttributesBuilder.putMetadata(crashMetadata: PulseAppMetadata): AttributesBuilder =
        apply {
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

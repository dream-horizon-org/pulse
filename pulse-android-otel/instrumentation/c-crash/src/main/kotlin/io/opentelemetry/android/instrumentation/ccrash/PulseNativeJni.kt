/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.ccrash

import com.pulse.jni.PulseJniCall
import com.pulse.utils.PulseLogger

@PulseJniCall
internal object PulseNativeJni {
    init {
        try {
            System.loadLibrary("pulse_c_crash")
            PulseLogger.logDebug(CCrashInstrumentation.TAG) { "native library loaded" }
        } catch (t: Throwable) {
            PulseLogger.logError(CCrashInstrumentation.TAG, t) { "native library load failed" }
        }
    }

    fun install(
        reportsDirAbsolutePath: String,
        metadataSourceAbsolutePath: String,
        crashFileName: String,
        metadataFileName: String,
        sessionId: String,
    ): Boolean =
        try {
            PulseLogger.logDebug(CCrashInstrumentation.TAG) { "nativeInstall invoking" }
            nativeInstall(
                reportsDirAbsolutePath,
                metadataSourceAbsolutePath,
                crashFileName,
                metadataFileName,
                sessionId,
            )
        } catch (t: Throwable) {
            PulseLogger.logError(CCrashInstrumentation.TAG, t) { "nativeInstall failed" }
            false
        }

    fun updateSessionId(sessionId: String) {
        try {
            nativeUpdateSessionId(sessionId)
        } catch (t: Throwable) {
            PulseLogger.logError(CCrashInstrumentation.TAG, t) { "nativeUpdateSessionId failed" }
        }
    }

    @PulseJniCall
    private external fun nativeInstall(
        reportsDirAbsolutePath: String,
        metadataSourceAbsolutePath: String,
        crashFileName: String,
        metadataFileName: String,
        sessionId: String,
    ): Boolean

    @PulseJniCall
    private external fun nativeUpdateSessionId(sessionId: String)
}

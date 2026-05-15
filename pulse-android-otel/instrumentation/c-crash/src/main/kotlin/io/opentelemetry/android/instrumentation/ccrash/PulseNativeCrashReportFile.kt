/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.ccrash

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Shape of JSON written by native [pulse_c_crash.cpp] `write_report`. Null = absent in JSON. */
@Serializable
internal data class PulseNativeCrashReportFile(
    @SerialName("ts_ms") val tsMs: Long? = null,
    val pid: Int? = null,
    val tid: Long? = null,
    @SerialName("thread_name") val threadName: String? = null,
    val signal: Int? = null,
    @SerialName("signal_name") val signalName: String? = null,
    @SerialName("fault_addr") val faultAddr: String? = null,
    @SerialName("stack_frames") val stackFrames: List<PulseNativeStackFrame>? = null,
)

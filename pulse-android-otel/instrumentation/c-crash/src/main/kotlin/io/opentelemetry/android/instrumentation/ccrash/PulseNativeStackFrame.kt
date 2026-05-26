/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.ccrash

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One native frame from libunwindstack (see `write_report` in pulse_c_crash.cpp). */
@Serializable
internal data class PulseNativeStackFrame(
    @SerialName("frame_address") val frameAddress: String? = null,
    @SerialName("rel_pc") val relPc: String? = null,
    @SerialName("load_address") val loadAddress: String? = null,
    @SerialName("symbol_address") val symbolAddress: String? = null,
    @SerialName("symbol_offset") val symbolOffset: String? = null,
    @SerialName("code_identifier") val codeIdentifier: String? = null,
    val filename: String? = null,
    val method: String? = null,
)

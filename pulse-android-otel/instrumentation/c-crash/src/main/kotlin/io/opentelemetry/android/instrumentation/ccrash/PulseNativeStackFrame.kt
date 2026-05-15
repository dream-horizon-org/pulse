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
    @SerialName("frame_address") val frameAddress: Long? = null,
    @SerialName("rel_pc") val relPc: Long? = null,
    @SerialName("load_address") val loadAddress: Long? = null,
    @SerialName("symbol_address") val symbolAddress: Long? = null,
    @SerialName("code_identifier") val codeIdentifier: String? = null,
    val filename: String? = null,
    val method: String? = null,
)

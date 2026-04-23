/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.click.common

import com.pulse.semconv.PulseAttributes
import com.pulse.utils.PulseLogger
import io.opentelemetry.api.logs.LogRecordBuilder

/**
 * Shared wiring for `app.widget.click` log records: optional [PulseAttributes.APP_CLICK_CONTEXT]
 * from [label], plus debug-only local logging. Caller must [LogRecordBuilder.emit] after this returns.
 */
object PulseWidgetClickLogHelper {
    const val DEFAULT_LOG_TAG: String = "PulseClick"

    fun logClick(
        clickType: String,
        xPx: Float,
        yPx: Float,
        widgetName: String? = null,
        widgetId: String? = null,
        clickContext: String? = null,
        rageCount: Int? = null,
        logTag: String = DEFAULT_LOG_TAG,
    ) {
        PulseLogger.logDebug(logTag) {
            buildString {
                append("click.type=$clickType")
                rageCount?.let { append(" is_rage=true count=$it") }
                append(" x=${xPx.toLong()} y=${yPx.toLong()}")
                widgetName?.let { append(" name=$it") }
                widgetId?.let { append(" id=$it") }
                clickContext?.let { append(" context=$it") }
            }
        }
    }
}

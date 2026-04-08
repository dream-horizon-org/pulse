/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.click.common

import com.pulse.semconv.PulseAttributes
import com.pulse.utils.PulseOtelUtils
import io.opentelemetry.api.logs.LogRecordBuilder

/**
 * Shared wiring for `app.widget.click` log records: optional [PulseAttributes.APP_CLICK_CONTEXT]
 * from [label], plus debug-only local logging. Caller must [LogRecordBuilder.emit] after this returns.
 */
object PulseWidgetClickLogHelper {
    const val DEFAULT_LOG_TAG: String = "PulseClick"

    fun logClick(
        clickType: String,
        xInPx: Float,
        yInPx: Float,
        widgetName: String? = null,
        widgetId: String? = null,
        clickContext: String? = null,
        isRage: Boolean = false,
        rageCount: Int = 0,
        logTag: String = DEFAULT_LOG_TAG,
    ) {
        PulseOtelUtils.logDebug(logTag) {
            buildString {
                append("click.type=$clickType")
                if (isRage) append(" is_rage=true count=$rageCount")
                append(" x=${xInPx.toLong()} y=${yInPx.toLong()}")
                widgetName?.let { append(" name=$it") }
                widgetId?.let { append(" id=$it") }
                clickContext?.let { append(" context=$it") }
            }
        }
    }
}

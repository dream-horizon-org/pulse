/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.click.common

import com.pulse.semconv.PulseAttributes
import com.pulse.utils.PulseOtelUtils
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.logs.LogRecordBuilder
import io.opentelemetry.semconv.incubating.AppIncubatingAttributes.APP_WIDGET_ID
import io.opentelemetry.semconv.incubating.AppIncubatingAttributes.APP_WIDGET_NAME

/**
 * Shared wiring for `app.widget.click` log records: optional [PulseAttributes.APP_CLICK_CONTEXT]
 * from [label], plus debug-only local logging. Caller must [LogRecordBuilder.emit] after this returns.
 */
object PulseWidgetClickLogHelper {
    const val DEFAULT_LOG_TAG: String = "PulseClick"

    fun applyContextAndLogDebug(
        record: LogRecordBuilder,
        attributes: Attributes,
        logCoordX: String,
        logCoordY: String,
        isContextEnrichmentEnabled: Boolean,
        label: String?,
        logTag: String = DEFAULT_LOG_TAG,
    ) {
        val widgetNameForLog = attributes.get(APP_WIDGET_NAME).orEmpty()
        val widgetIdForLog = attributes.get(APP_WIDGET_ID).orEmpty()
        if (isContextEnrichmentEnabled) {
            PulseAttributes.AppClickContext.buildContext(label)?.let { ctxStr ->
                record.setAttribute(PulseAttributes.APP_CLICK_CONTEXT, ctxStr)
            }
            PulseOtelUtils.logDebug(logTag) {
                "app.widget.click: x=$logCoordX y=$logCoordY name=$widgetNameForLog " +
                    "context=${label.orEmpty()} widgetId=$widgetIdForLog"
            }
        } else {
            PulseOtelUtils.logDebug(logTag) {
                "app.widget.click: x=$logCoordX y=$logCoordY name=$widgetNameForLog " +
                    "widgetId=$widgetIdForLog (no app.click.context)"
            }
        }
    }
}

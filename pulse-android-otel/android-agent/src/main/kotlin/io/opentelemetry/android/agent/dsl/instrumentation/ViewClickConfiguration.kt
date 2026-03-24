/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.agent.dsl.instrumentation

import io.opentelemetry.android.agent.dsl.OpenTelemetryDslMarker
import io.opentelemetry.android.instrumentation.click.ClickContextEnrichmentConfig

/**
 * Configuration for View click instrumentation context enrichment.
 * Install the view-click dependency to enable click events; use this to control
 * whether UI labels are extracted for app.click.context.
 */
@OpenTelemetryDslMarker
class ViewClickConfiguration internal constructor() {
    /**
     * Enable or disable capturing context (human-readable label in app.click.context).
     * Default: true. When false, events still emit with coordinates and widget attributes, but
     * **app.click.context is not set** (no enrichment payload).
     */
    fun captureContext(enabled: Boolean) {
        ClickContextEnrichmentConfig.isViewClickContextEnrichmentEnabled = enabled
    }
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.agent.dsl.instrumentation

import io.opentelemetry.android.agent.dsl.OpenTelemetryDslMarker
import io.opentelemetry.android.instrumentation.click.ClickContextEnrichmentConfig

/**
 * Configuration for View click instrumentation.
 *
 * Install the view-click dependency to enable click events. Use this block to control:
 * - Whether UI labels are extracted for `app.click.context` ([captureContext]).
 * - Rage-click detection parameters ([rage]).
 *
 * Values set here are used as local defaults. Backend feature config overrides them
 * field-by-field at SDK initialization time.
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

    /**
     * Configure rage-click detection parameters.
     * Values provided here serve as local defaults; the backend can override individual fields.
     */
    fun rage(configure: RageConfiguration.() -> Unit) {
        ClickContextEnrichmentConfig.rageConfig = RageConfiguration().apply(configure).build()
    }
}

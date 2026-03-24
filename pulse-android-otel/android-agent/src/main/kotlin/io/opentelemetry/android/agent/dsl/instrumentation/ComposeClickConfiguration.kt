/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.agent.dsl.instrumentation

import io.opentelemetry.android.agent.dsl.OpenTelemetryDslMarker
import io.opentelemetry.android.instrumentation.click.ClickContextEnrichmentConfig

/**
 * Configuration for Compose click instrumentation context enrichment.
 * Install the compose-click dependency to enable click events; use this to control
 * whether labels and element hints are extracted.
 */
@OpenTelemetryDslMarker
class ComposeClickConfiguration internal constructor() {
    /**
     * Enable or disable capturing context (label, element hint in app.click.context).
     * Default: true. When false, events still emit with coordinates and widget attributes, but
     * **app.click.context is not set** (no enrichment payload).
     */
    fun captureContext(enabled: Boolean) {
        ClickContextEnrichmentConfig.composeClickContextEnrichmentEnabled = enabled
    }
}

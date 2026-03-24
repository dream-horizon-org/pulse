/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.click

/**
 * Configuration for click context enrichment (label, element hint in app.click.context).
 * Set via SDK InstrumentationConfiguration viewClick / composeClick blocks. Default: true.
 *
 * When disabled, click events are still emitted with tap coordinates and widget identity
 * attributes, but **app.click.context is omitted** (no label/element, no type/source string),
 * avoiding the tree traversal and label extraction that can impact performance.
 */
object ClickContextEnrichmentConfig {
    @Volatile
    @JvmField
    var viewClickContextEnrichmentEnabled: Boolean = true

    @Volatile
    @JvmField
    var composeClickContextEnrichmentEnabled: Boolean = true
}

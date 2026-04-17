/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.click

/**
 * Shared configuration for click instrumentation, applied before instrumentation installs.
 *
 * Set via the SDK DSL (`viewClick { }` / `composeClick { }` blocks) and optionally overridden
 * by backend feature config resolved in `PulseSDKInternal`.
 *
 * - Context enrichment (label extraction for `app.click.context`) can be disabled per
 *   instrumentation type to avoid the extra view-tree traversal.
 * - [rageConfig] is resolved from the backend `click` feature config (field-level override on top
 *   of DSL or hard-coded defaults) and shared across View and Compose click instrumentation.
 */
object ClickContextEnrichmentConfig {
    @Volatile
    @JvmField
    var isViewClickContextEnrichmentEnabled: Boolean = true

    @Volatile
    @JvmField
    var isComposeClickContextEnrichmentEnabled: Boolean = true

    @Volatile
    @JvmField
    var rageConfig: RageConfig = RageConfig()
}

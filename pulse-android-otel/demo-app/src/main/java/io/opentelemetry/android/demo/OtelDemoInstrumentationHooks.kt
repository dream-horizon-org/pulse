/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.demo

/**
 * Endpoints to be used in [OtelDemoApplication.onCreate].
 */
internal object OtelDemoInstrumentationHooks {
    @JvmField
    var otlpEndpointBaseUrl: String = "http://10.0.2.2:4318"

    @JvmField
    var interactionConfigUrl: String = "http://10.0.2.2:8080/v1/interaction-configs/"
}

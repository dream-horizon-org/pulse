/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.agent.dsl.instrumentation

import io.opentelemetry.android.agent.dsl.OpenTelemetryDslMarker
import io.opentelemetry.android.config.OtelRumConfig
import io.opentelemetry.android.instrumentation.AndroidInstrumentationLoader
import io.opentelemetry.android.instrumentation.interaction.library.InteractionAttributesExtractor
import io.opentelemetry.android.instrumentation.interaction.library.InteractionInstrumentation

@OpenTelemetryDslMarker
class InteractionConfiguration internal constructor(
    private val config: OtelRumConfig,
) : CanBeEnabledAndDisabled {
    private val interactionInstrumentation: InteractionInstrumentation by lazy {
        AndroidInstrumentationLoader.getInstrumentation(
            InteractionInstrumentation::class.java,
        )
    }

    fun addAttributesExtractor(value: InteractionAttributesExtractor) {
        interactionInstrumentation.addAttributesExtractor(value)
    }

    override fun enabled(enabled: Boolean) {
        if (enabled) {
            config.allowInstrumentation(interactionInstrumentation.name)
        } else {
            config.suppressInstrumentation(interactionInstrumentation.name)
        }
    }
}

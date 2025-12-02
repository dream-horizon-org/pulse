/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.agent.dsl.instrumentation

import com.pulse.android.core.config.InteractionConfigFetcher
import com.pulse.android.core.config.InteractionConfigRestFetcher
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

    /**
     * Configure the URL provider for the Interaction rest API. `Get` call will performed in this
     * URL to fetch the list of interaction configs
     * If not set, defaults to "http://10.0.2.2:8080/interaction-configs"
     * Also see [setConfigFetcher]
     */
    fun setConfigUrl(urlProvider: () -> String): InteractionConfiguration =
        apply {
            interactionInstrumentation.setConfigFetcher(InteractionConfigRestFetcher(urlProvider))
        }

    /**
     * Configure the interaction config fetcher.
     * In case not set defaults to "http://10.0.2.2:8080/interaction-configs" with [InteractionConfigRestFetcher]
     * Also see [setConfigUrl]
     */
    fun setConfigFetcher(configFetcher: InteractionConfigFetcher): InteractionConfiguration =
        apply {
            interactionInstrumentation.setConfigFetcher(configFetcher)
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

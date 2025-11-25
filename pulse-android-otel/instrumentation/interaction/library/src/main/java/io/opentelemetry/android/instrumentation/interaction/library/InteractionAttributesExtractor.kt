package io.opentelemetry.android.instrumentation.interaction.library

import com.pulse.android.core.Interaction
import com.pulse.otel.utils.putAttributesFrom
import com.pulse.semconv.PulseAttributes
import com.pulse.semconv.PulseInteractionAttributes
import io.opentelemetry.api.common.AttributesBuilder

class DefaultInteractionAttributesExtractor : InteractionAttributesExtractor {
    override fun invoke(
        attributesBuilder: AttributesBuilder,
        interaction: Interaction
    ) {
        with(attributesBuilder) {
            putAttributesFrom(interaction.props)
            put(PulseInteractionAttributes.INTERACTION_NAME, interaction.name)
            put(PulseInteractionAttributes.INTERACTION_ID, interaction.id)
            put(PulseAttributes.PULSE_TYPE, PulseAttributes.PulseTypeValues.INTERACTION)
        }
    }
}
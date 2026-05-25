package io.opentelemetry.android.internal.services.metadata

import kotlinx.serialization.Serializable

@Serializable
class PulseAppMetadata(
    val longFields: Map<String, Long> = emptyMap(),
    val stringFields: Map<String, String> = emptyMap(),
    val doubleFields: Map<String, Double> = emptyMap(),
    val booleanFields: Map<String, Boolean> = emptyMap(),
) {
    internal fun copy(
        longFields: Map<String, Long> = this.longFields,
        stringFields: Map<String, String> = this.stringFields,
        doubleFields: Map<String, Double> = this.doubleFields,
        booleanFields: Map<String, Boolean> = this.booleanFields,
    ): PulseAppMetadata {
        return PulseAppMetadata(
            longFields = longFields,
            stringFields = stringFields,
            doubleFields = doubleFields,
            booleanFields = booleanFields
        )
    }
    internal companion object {
        const val ACTIVITY_NAME = "activity.name"
        const val FRAGMENT_NAME = "fragment.name"
        const val SCREEN_NAME = "screen.name"
    }
}

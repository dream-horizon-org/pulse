package com.pulse.android.core

internal enum class InteractionErrorType(
    val code: String,
) {
    TIMEOUT("timeout"),
    SEQUENCE_VIOLATION("sequence_violation"),
    ;

    companion object {
        private val byCode: Map<String, InteractionErrorType> =
            values().associateBy { it.code }

        internal fun fromCode(value: String): InteractionErrorType? = byCode[value]
    }
}

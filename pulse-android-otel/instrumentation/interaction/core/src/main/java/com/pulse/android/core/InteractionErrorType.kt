package com.pulse.android.core

public enum class InteractionErrorType(
    public val code: String,
) {
    TIMEOUT("timeout"),
    SEQUENCE_VIOLATION("sequence_violation"),
    ;

    public companion object {
        private val byCode: Map<String, InteractionErrorType> =
            values().associateBy { it.code }

        public fun fromCode(value: String?): InteractionErrorType? = value?.let { byCode[it] }
    }
}

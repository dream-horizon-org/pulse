package com.pulse.android.core

public enum class InteractionErrorType(
    public val code: String,
) {
    TIMEOUT("timeout"),
    SEQUENCE_VIOLATION("sequence_violation"),
    ;

    public companion object {
        public fun fromCode(value: String?): InteractionErrorType? =
            value?.let { v -> values().find { it.code == v } }
    }
}

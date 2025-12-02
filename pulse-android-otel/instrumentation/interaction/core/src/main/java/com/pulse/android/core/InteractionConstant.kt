package com.pulse.android.core

public object InteractionConstant {
    public const val NAME: String = "pulse.interaction.name"
    public const val CONFIG_NAME: String = "pulse.interaction.config.name"
    public const val CONFIG_ID: String = "pulse.interaction.config.id"
    public const val ID: String = "pulse.interaction.id"
    public const val LAST_EVENT_TIME_IN_NANO: String = "pulse.interaction.last_event_time"
    public const val APDEX_SCORE: String = "pulse.interaction.apdex_score"
    public const val USER_CATEGORY: String = "pulse.interaction.user_category"
    public const val TIME_TO_COMPLETE_IN_NANO: String = "pulse.interaction.complete_time"
    public const val IS_ERROR: String = "pulse.interaction.is_error"
    public const val LOCAL_EVENTS: String = "internal_events"
    public const val MARKER_EVENTS: String = "internal_marker"

    internal enum class Operators(
        internal val operatorName: String,
    ) {
        EQUALS("EQUALS"),
        NOT_EQUALS("NOTEQUALS"),
        CONTAINS("CONTAINS"),
        NOT_CONTAINS("NOTCONTAINS"),
        STARTS_WITH("STARTSWITH"),
        ENDS_WITH("ENDSWITH"),
    }

    internal enum class TimeCategory(
        internal val categoryName: String,
    ) {
        EXCELLENT("Excellent"),
        GOOD("Good"),
        AVERAGE("Average"),
        POOR("Poor"),
    }

    internal const val LOG_TAG = "InteractionManager"
}

/** Span/prop keys aligned with Android InteractionConstant. */
export const INTERACTION_PROP_KEYS = {
  NAME: "pulse.interaction.name",
  CONFIG_ID: "pulse.interaction.config.id",
  LAST_EVENT_TIME_IN_NANO: "pulse.interaction.last_event_time",
  LOCAL_EVENTS: "pulse.internal.events",
  MARKER_EVENTS: "pulse.internal.marker_events",
  APDEX_SCORE: "pulse.interaction.apdex_score",
  USER_CATEGORY: "pulse.interaction.user_category",
  TIME_TO_COMPLETE_IN_NANO: "pulse.interaction.complete_time",
  IS_ERROR: "pulse.interaction.is_error",
  ERROR_TYPE: "pulse.interaction.error.type",
  ERROR_MESSAGE: "pulse.interaction.error.message",
} as const;

export const INTERACTION_TIME_CATEGORY = {
  EXCELLENT: "Excellent",
  GOOD: "Good",
  AVERAGE: "Average",
  POOR: "Poor",
} as const;

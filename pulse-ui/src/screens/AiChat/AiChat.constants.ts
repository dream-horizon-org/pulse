export const AI_CHAT_TEXTS = {
  TITLE: "AI Assistant",
  NEW_CHAT: "New Chat",
  PLACEHOLDER: "Ask about your app's performance...",
  WELCOME_TITLE: "Pulse AI Assistant",
  WELCOME_SUBTITLE:
    "Ask questions about your app's performance, crash rates, network health, and more.",
  ERROR_GENERIC: "Something went wrong. Please try again.",
  SESSIONS_TITLE: "Conversations",
  NO_SESSIONS: "No conversations yet",
  NEW_CONVERSATION: "New conversation",
  FAILED_RESPONSE: "Failed to get response.",
  LOADING_SESSIONS: "Loading sessions...",
  GENERATED_SQL: "Generated SQL",
  COPIED: "Copied",
  COPY: "Copy",
  THINKING: "Thinking",
  CHART_RENDER_ERROR: "Chart could not be rendered",
  TABLE_RENDER_ERROR: "Table could not be rendered",
  ROWS_SUFFIX: " rows",
  UNKNOWN_AGENT_ERROR: "Unknown agent error",
  NO_RESPONSE_BODY: "No response body",
  STREAM_FAILED: "Stream reading failed",
  NETWORK_ERROR: "Network error",
};

export const SUGGESTED_QUERIES = [
  "Top 5 screens by load time in the last 24 hours",
  "Crash rate trend for the last 7 days",
  "Slowest network APIs by p95 latency",
  "Show ANR count by app version",
  "Average session duration today",
  "Screen load time comparison: iOS vs Android",
];

export const AI_API_PATHS = {
  RUN_SSE: "/v1/ai/run_sse",
  SESSIONS: "/v1/ai/sessions",
} as const;

export const AI_CHAT_LIMITS = {
  TITLE_MAX_LENGTH: 50,
  SIDEBAR_TITLE_TRUNCATE: 30,
  SIDEBAR_DESC_TRUNCATE: 40,
  CHAT_INPUT_MIN_ROWS: 1,
  CHAT_INPUT_MAX_ROWS: 6,
  COPY_TOOLTIP_TIMEOUT_MS: 2000,
  SESSIONS_STALE_TIME_MS: 30_000,
  CHART_HEIGHT: 300,
} as const;

export const CHART_GRID_DEFAULTS = {
  top: 30,
  left: 50,
  right: 30,
  bottom: 50,
  containLabel: true,
} as const;

export const CHART_Y_AXIS_DEFAULTS = {
  nameLocation: "middle" as const,
  nameGap: 45,
  nameTextStyle: {
    fontSize: 12,
  },
} as const;

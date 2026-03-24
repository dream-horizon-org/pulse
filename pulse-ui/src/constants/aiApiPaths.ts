/**
 * Pulse server AI proxy routes (pulse-server → pulse_ai).
 * Single source of truth for path segments; combine with {@link API_BASE_URL} for full URLs.
 */
export const AI_API_PATHS = {
  RUN_SSE: "/v1/ai/run_sse",
  SESSIONS: "/v1/ai/sessions",
} as const;

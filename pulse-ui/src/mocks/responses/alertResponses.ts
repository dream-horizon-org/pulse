/**
 * Alerts Management Mock Responses
 * Based on: Pulse-Alert API Documentation
 * 
 * Endpoints:
 * - GET /v1/alert/scopes
 * - GET /v1/alert/severity
 * - GET /v1/alert/metrics?scope={scopeId}
 * - GET /v1/alert/notificationChannels
 */

// GET /v1/alert/scopes
export const mockAlertScopes = {
  scopes: [
    { id: "interaction", label: "Interactions" },
    { id: "network_api", label: "Network APIs" },
    { id: "app_vitals", label: "App Vitals" },
    { id: "screen", label: "Screen" },
  ],
};

/**
 * GET /v1/alert/severity
 * @see backend AlertSeverityResponseDto.java
 * Note: 'name' is Integer in backend (severity level), not String
 */
export const mockAlertSeverities = {
  severity: [
    { severity_id: 1, name: 1, description: "Critical alerts require immediate attention" },
    { severity_id: 2, name: 2, description: "Warning alerts should be reviewed soon" },
    { severity_id: 3, name: 3, description: "Informational alerts for tracking" },
  ],
};

// GET /v1/alert/metrics?scope={scopeId}
export const mockAlertMetrics: Record<string, { scope: string; metrics: string[] }> = {
  interaction: {
    scope: "interaction",
    metrics: [
      "INTERACTION_SUCCESS_COUNT",
      "INTERACTION_ERROR_COUNT",
      "INTERACTION_ERROR_DISTINCT_USERS",
      "INTERACTION_CATEGORY_POOR",
      "INTERACTION_CATEGORY_AVERAGE",
      "INTERACTION_CATEGORY_GOOD",
      "INTERACTION_CATEGORY_EXCELLENT",
      "INTERACTION_TIME_P99",
    ],
  },
  network_api: {
    scope: "network_api",
    metrics: [
      "API_RESPONSE_TIME_P50",
      "API_RESPONSE_TIME_P95",
      "API_RESPONSE_TIME_P99",
      "API_ERROR_RATE",
      "API_SUCCESS_RATE",
      "API_TIMEOUT_COUNT",
      "API_4XX_COUNT",
      "API_5XX_COUNT",
    ],
  },
  app_vitals: {
    scope: "app_vitals",
    metrics: [
      "CRASH_RATE",
      "ANR_RATE",
      "CRASH_FREE_USERS",
      "APP_START_TIME_COLD",
      "APP_START_TIME_WARM",
      "MEMORY_USAGE_AVG",
      "BATTERY_DRAIN_RATE",
    ],
  },
  screen: {
    scope: "screen",
    metrics: [
      "SCREEN_LOAD_TIME_P50",
      "SCREEN_LOAD_TIME_P95",
      "SCREEN_LOAD_TIME_P99",
      "SCREEN_RENDER_TIME",
      "SCREEN_TTI",
      "SCREEN_FPS_AVG",
      "SCREEN_FROZEN_FRAMES",
    ],
  },
};

/**
 * GET /v1/alert/notificationChannels
 * @see backend AlertNotificationChannelResponseDto.java
 */
export const mockNotificationChannels = [
  { notification_channel_id: 1, name: "Slack - #alerts", notification_webhook_url: "https://hooks.slack.com/services/xxx" },
  { notification_channel_id: 2, name: "Email - Engineering Team", notification_webhook_url: "mailto:engineering@example.com" },
  { notification_channel_id: 3, name: "PagerDuty - On-Call", notification_webhook_url: "https://events.pagerduty.com/v2/enqueue" },
  { notification_channel_id: 4, name: "Webhook - Custom Integration", notification_webhook_url: "https://api.example.com/webhooks/alerts" },
];

// Legacy exports for backwards compatibility
export const mockAlertResponses = {
  getAlerts: {
    data: {
      total_alerts: 0,
      alerts: [],
      limit: 10,
      offset: 0,
    },
    status: 200,
  },
  createAlert: {
    data: {
      id: Date.now(),
      name: "New Mock Alert",
      description: "Mock alert description",
      severity: 3,
      currentState: "ACTIVE",
      createdAt: new Date().toISOString(),
    },
    status: 201,
  },
  updateAlert: {
    data: { success: true },
    status: 200,
  },
  deleteAlert: {
    data: { success: true },
    status: 200,
  },
  snoozeAlert: {
    data: { success: true },
    status: 200,
  },
  resumeAlert: {
    data: { success: true },
    status: 200,
  },
};

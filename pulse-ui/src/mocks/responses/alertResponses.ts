/**
 * Alerts Management Mock Responses
 * Matches backend AlertController.java endpoints
 * All data structures match their corresponding backend DTOs
 */

// =============================================================================
// GET /v1/alert/scopes - AlertScopesResponseDto
// =============================================================================
// Scope IDs must match AlertScopeType enum values in the frontend
// Interface: { id: number, name: string, label: string }
export const mockAlertScopes = {
  scopes: [
    { id: 1, name: "interaction", label: "Interactions" },
    { id: 2, name: "network_api", label: "Network APIs" },
    { id: 3, name: "app_vitals", label: "App Vitals" },
    { id: 4, name: "screen", label: "Screen" },
  ],
};

// =============================================================================
// GET /v1/alert/severity - List<AlertSeverityResponseDto>
// Note: 'name' is Integer (severity level) in backend
// =============================================================================
export const mockAlertSeverities = [
  { severity_id: 1, name: 1, description: "Critical - Requires immediate attention" },
  { severity_id: 2, name: 2, description: "Warning - Should be reviewed soon" },
  { severity_id: 3, name: 3, description: "Info - For tracking purposes" },
];

// =============================================================================
// GET /v1/alert/metrics?scope={scopeId} - AlertMetricsResponseDto
// =============================================================================
// Keys must match scope names (lowercase, matches AlertScopeType enum)
export const mockAlertMetrics: Record<string, { scope: string; metrics: Array<{ id: number; name: string; label: string }> }> = {
  interaction: {
    scope: "interaction",
    metrics: [
      { id: 1, name: "APDEX", label: "APDEX value [0,1]" },
      { id: 2, name: "INTERACTION_SUCCESS_COUNT", label: "INTERACTION_SUCCESS_COUNT value >= 0" },
      { id: 3, name: "INTERACTION_ERROR_COUNT", label: "INTERACTION_ERROR_COUNT value >= 0" },
      { id: 4, name: "INTERACTION_ERROR_DISTINCT_USERS", label: "INTERACTION_ERROR_DISTINCT_USERS value >= 0" },
      { id: 5, name: "DURATION_P99", label: "DURATION_P99 value >= 0" },
      { id: 6, name: "DURATION_P95", label: "DURATION_P95 value >= 0" },
      { id: 7, name: "DURATION_P50", label: "DURATION_P50 value >= 0" },
      { id: 8, name: "ERROR_RATE", label: "ERROR_RATE value [0,1]" },
    ],
  },
  network_api: {
    scope: "network_api",
    metrics: [
      { id: 9, name: "NET_0", label: "NET 0 value >= 0" },
      { id: 10, name: "NET_2XX", label: "NET 2XX value >= 0" },
      { id: 11, name: "NET_3XX", label: "NET 3XX value >= 0" },
      { id: 12, name: "NET_4XX", label: "NET 4XX value >= 0" },
      { id: 13, name: "NET_5XX", label: "NET 5XX value >= 0" },
      { id: 14, name: "NET_4XX_RATE", label: "NET 4XX RATE value [0,1]" },
      { id: 15, name: "NET_5XX_RATE", label: "NET 5XX RATE value [0,1]" },
      { id: 16, name: "DURATION_P99", label: "DURATION P99 value >= 0" },
      { id: 17, name: "DURATION_P95", label: "DURATION P95 value >= 0" },
      { id: 18, name: "DURATION_P50", label: "DURATION P50 value >= 0" },
      { id: 19, name: "ERROR_RATE", label: "ERROR RATE value [0,1]" },
      { id: 20, name: "NET_COUNT", label: "NET COUNT value >= 0" },
    ],
  },
  app_vitals: {
    scope: "app_vitals",
    metrics: [
      { id: 29, name: "CRASH_FREE_USERS_PERCENTAGE", label: "CRASH FREE USERS PERCENTAGE value [0,1]" },
      { id: 30, name: "CRASH_FREE_SESSIONS_PERCENTAGE", label: "CRASH FREE SESSIONS PERCENTAGE value [0,1]" },
      { id: 31, name: "CRASH_USERS", label: "CRASH USERS value >= 0" },
      { id: 32, name: "CRASH_SESSIONS", label: "CRASH SESSIONS value >= 0" },
      { id: 33, name: "ALL_USERS", label: "ALL USERS value >= 0" },
      { id: 34, name: "ALL_SESSIONS", label: "ALL SESSIONS value >= 0" },
      { id: 35, name: "ANR_FREE_USERS_PERCENTAGE", label: "ANR FREE USERS PERCENTAGE value [0,1]" },
      { id: 36, name: "ANR_FREE_SESSIONS_PERCENTAGE", label: "ANR FREE SESSIONS PERCENTAGE value [0,1]" },
      { id: 37, name: "ANR_USERS", label: "ANR USERS value >= 0" },
      { id: 38, name: "ANR_SESSIONS", label: "ANR SESSIONS value >= 0" },
      { id: 39, name: "NON_FATAL_FREE_USERS_PERCENTAGE", label: "NON FATAL FREE USERS PERCENTAGE value [0,1]" },
      { id: 40, name: "NON_FATAL_FREE_SESSIONS_PERCENTAGE", label: "NON FATAL FREE SESSIONS PERCENTAGE value [0,1]" },
      { id: 41, name: "NON_FATAL_USERS", label: "NON FATAL USERS value >= 0" },
      { id: 42, name: "NON_FATAL_SESSIONS", label: "NON FATAL SESSIONS value >= 0" },
    ],
  },
  screen: {
    scope: "screen",
    metrics: [
      { id: 17, name: "SCREEN_DAILY_USERS", label: "SCREEN DAILY USERS value >= 0" },
      { id: 18, name: "ERROR_RATE", label: "SCREEN ERROR RATE value [0,1]" },
      { id: 19, name: "SCREEN_TIME", label: "SCREEN TIME value >= 0" },
      { id: 20, name: "LOAD_TIME", label: "LOAD TIME value >= 0" },
    ],
  },
};

// =============================================================================
// GET /v1/alert/notificationChannels - List<AlertNotificationChannelResponseDto>
// =============================================================================
export const mockNotificationChannels = [
  { notification_channel_id: 1, name: "Slack - #alerts-critical", notification_webhook_url: "https://hooks.slack.com/services/xxx" },
  { notification_channel_id: 2, name: "Email - Engineering Team", notification_webhook_url: "mailto:engineering@example.com" },
  { notification_channel_id: 3, name: "PagerDuty - On-Call", notification_webhook_url: "https://events.pagerduty.com/v2/enqueue" },
  { notification_channel_id: 4, name: "Webhook - Custom Integration", notification_webhook_url: "https://api.example.com/webhooks/alerts" },
];

// =============================================================================
// GET /v1/alert/filters - AlertFiltersResponseDto
// =============================================================================
export const mockAlertFilters = {
  job_id: ["job_1", "job_2", "job_3"],
  created_by: ["chirag@example.com", "john@example.com", "admin@example.com"],
  updated_by: ["chirag@example.com", "john@example.com", "admin@example.com"],
  scope: ["interaction", "network_api", "app_vitals", "screen"],
  current_state: ["ACTIVE", "FIRING", "SNOOZED"],
};

// =============================================================================
// Alert condition templates for realistic data
// =============================================================================
const alertTemplates = [
  {
    name: "Payment Flow - High P99 Latency",
    description: "Latency exceeds 4s for payment interactions",
    scope: "interaction",
    metric: "DURATION_P99",
    operator: "GREATER_THAN",
    thresholds: { "PaymentSubmit": 4000, "PaymentConfirm": 3500, "PaymentOTP": 3000 },
    severity_id: 1,
  },
  {
    name: "Checkout APIs - Error Rate",
    description: "Error rate above threshold for checkout",
    scope: "network_api",
    metric: "ERROR_RATE",
    operator: "GREATER_THAN",
    thresholds: { "/checkout/initiate": 0.05, "/checkout/confirm": 0.03, "/payment/process": 0.02 },
    severity_id: 1,
  },
  {
    name: "App Crash Rate - Critical",
    description: "Crash rate exceeds acceptable threshold",
    scope: "app_vitals",
    metric: "CRASH_RATE",
    operator: "GREATER_THAN",
    thresholds: { "Android": 0.02, "iOS": 0.015 },
    severity_id: 1,
  },
  {
    name: "Home & Product Screens - Load Time",
    description: "Screen load time above 3 seconds",
    scope: "screen",
    metric: "SCREEN_LOAD_TIME_P95",
    operator: "GREATER_THAN",
    thresholds: { "HomeScreen": 3000, "ProductListScreen": 2500, "CategoryScreen": 2000 },
    severity_id: 2,
  },
  {
    name: "Login Flow - Error Spike",
    description: "High error count for login interactions",
    scope: "interaction",
    metric: "INTERACTION_ERROR_COUNT",
    operator: "GREATER_THAN",
    thresholds: { "LoginSubmit": 100, "OTPVerify": 50, "BiometricAuth": 30 },
    severity_id: 2,
  },
  {
    name: "Search & Suggest APIs - Latency",
    description: "Search APIs latency exceeding threshold",
    scope: "network_api",
    metric: "DURATION_P99",
    operator: "GREATER_THAN",
    thresholds: { "/search/products": 2000, "/search/suggest": 500 },
    severity_id: 2,
  },
  {
    name: "ANR Rate - Warning",
    description: "ANR rate tracking across platforms",
    scope: "app_vitals",
    metric: "ANR_RATE",
    operator: "GREATER_THAN",
    thresholds: { "Android": 0.01, "iOS": 0.005 },
    severity_id: 2,
  },
  {
    name: "Product Detail Screen - Performance",
    description: "Product detail load time monitoring",
    scope: "screen",
    metric: "SCREEN_LOAD_TIME_P50",
    operator: "GREATER_THAN",
    thresholds: { "ProductDetailScreen": 1500, "ProductImageGallery": 1200 },
    severity_id: 3,
  },
  {
    name: "Cart Interactions - APDEX",
    description: "APDEX score below acceptable threshold",
    scope: "interaction",
    metric: "APDEX",
    operator: "LESS_THAN",
    thresholds: { "AddToCart": 0.85, "UpdateCart": 0.90, "RemoveFromCart": 0.88 },
    severity_id: 3,
  },
  {
    name: "Notification APIs - 5XX Errors",
    description: "Server errors in notification service",
    scope: "network_api",
    metric: "NET_5XX_RATE",
    operator: "GREATER_THAN",
    thresholds: { "/notifications/send": 0.01, "/notifications/status": 0.02 },
    severity_id: 2,
  },
];

// =============================================================================
// GET /v1/alert - AlertDetailsPaginatedResponseDto (list of alerts)
// =============================================================================
const generateMockAlerts = () => {
  const alerts = [];
  const users = ["chirag@example.com", "john@example.com", "admin@example.com"];

  for (let i = 1; i <= 25; i++) {
    const template = alertTemplates[(i - 1) % alertTemplates.length];
    const isFiring = i % 5 === 1; // Every 5th alert starting from 1 is firing
    const isSnoozed = i === 3 || i === 11; // Alert 3 and 11 are snoozed for easy testing
    const createdDaysAgo = Math.floor(Math.random() * 30) + 1;
    const lastTriggered = isFiring ? new Date(Date.now() - Math.random() * 3600000).toISOString() : null;
    
    // Snooze until 4 hours from now for better visibility
    const snoozeUntil = Date.now() + 4 * 60 * 60 * 1000;

    alerts.push({
      alert_id: i,
      name: i <= alertTemplates.length ? template.name : `${template.name} #${i}`,
      description: template.description,
      scope: template.scope,
      dimension_filter: null,
      alerts: [
        {
          alias: "A",
          metric: template.metric,
          metric_operator: template.operator,
          threshold: template.thresholds,
        },
      ],
      condition_expression: "A",
      evaluation_period: [300, 600, 900][i % 3],
      evaluation_interval: [60, 120, 180][i % 3],
      severity_id: template.severity_id,
      notification_channel_id: (i % 4) + 1,
      notification_webhook_url: mockNotificationChannels[i % 4].notification_webhook_url,
      created_by: users[i % users.length],
      updated_by: users[(i + 1) % users.length],
      created_at: new Date(Date.now() - createdDaysAgo * 86400000).toISOString(),
      updated_at: new Date(Date.now() - Math.random() * 86400000 * 7).toISOString(),
      is_active: true,
      status: isFiring ? "FIRING" : isSnoozed ? "SNOOZED" : "NORMAL",
      is_snoozed: isSnoozed,
      last_snoozed_at: isSnoozed ? new Date().toISOString() : null,
      snoozed_from: isSnoozed ? Date.now() : null,
      snoozed_until: isSnoozed ? snoozeUntil : null,
      last_triggered_at: lastTriggered,
    });
  }
  return alerts;
};

export const mockAlertsList = generateMockAlerts();

export const getMockAlertsPage = (offset: number, limit: number, filters?: { name?: string; scope?: string; created_by?: string }) => {
  let filtered = [...mockAlertsList];
  
  if (filters?.name) {
    filtered = filtered.filter(a => a.name.toLowerCase().includes(filters.name!.toLowerCase()));
  }
  if (filters?.scope) {
    filtered = filtered.filter(a => a.scope === filters.scope);
  }
  if (filters?.created_by) {
    filtered = filtered.filter(a => a.created_by === filters.created_by);
  }

  return {
    total_alerts: filtered.length,
    alerts: filtered.slice(offset, offset + limit),
    offset,
    limit,
  };
};

// =============================================================================
// GET /v1/alert/{id} - AlertDetailsResponseDto (single alert)
// =============================================================================
export const getMockAlertDetails = (alertId: number) => {
  return mockAlertsList.find(a => a.alert_id === alertId) || null;
};

// =============================================================================
// GET /v1/alert/{id}/evaluationHistory - List<AlertEvaluationHistoryResponseDto>
// =============================================================================
export const getMockEvaluationHistory = (alertId: number) => {
  const history = [];
  for (let i = 0; i < 10; i++) {
    history.push({
      evaluation_id: i + 1,
      alert_id: alertId,
      evaluated_at: new Date(Date.now() - i * 3600000).toISOString(),
      result: i % 3 === 0 ? "FIRING" : "OK",
      metric_value: Math.random() * 100,
      threshold_value: 50,
    });
  }
  return history;
};

// =============================================================================
// GET /v1/alert/tag - List<AlertTagsResponseDto>
// =============================================================================
export const mockAlertTags = [
  { tag_id: 1, tag: "production" },
  { tag_id: 2, tag: "critical" },
  { tag_id: 3, tag: "payment" },
  { tag_id: 4, tag: "user-facing" },
];

// =============================================================================
// Metric display names for UI
// =============================================================================
export const metricDisplayNames: Record<string, string> = {
  APDEX: "APDEX Score",
  CRASH: "Crash Count",
  ANR: "ANR Count",
  CRASH_RATE: "Crash Rate",
  ANR_RATE: "ANR Rate",
  FROZEN_FRAME: "Frozen Frames",
  FROZEN_FRAME_RATE: "Frozen Frame Rate",
  DURATION_P99: "P99 Latency",
  DURATION_P95: "P95 Latency",
  DURATION_P50: "P50 Latency",
  ERROR_RATE: "Error Rate",
  INTERACTION_SUCCESS_COUNT: "Success Count",
  INTERACTION_ERROR_COUNT: "Error Count",
  INTERACTION_ERROR_DISTINCT_USERS: "Affected Users",
  SCREEN_LOAD_TIME_P99: "Load Time P99",
  SCREEN_LOAD_TIME_P95: "Load Time P95",
  SCREEN_LOAD_TIME_P50: "Load Time P50",
  NET_4XX: "4XX Errors",
  NET_5XX: "5XX Errors",
  NET_4XX_RATE: "4XX Rate",
  NET_5XX_RATE: "5XX Rate",
};

// =============================================================================
// Operator display names for UI
// =============================================================================
export const operatorDisplayNames: Record<string, string> = {
  GREATER_THAN: ">",
  LESS_THAN: "<",
  GREATER_THAN_OR_EQUAL: "≥",
  LESS_THAN_OR_EQUAL: "≤",
  EQUAL: "=",
  NOT_EQUAL: "≠",
};

// =============================================================================
// Legacy exports for backwards compatibility
// =============================================================================
export const mockAlertResponses = {
  getAlerts: {
    data: getMockAlertsPage(0, 12),
    status: 200,
  },
  createAlert: {
    data: { alert_id: Date.now(), success: true },
    status: 201,
  },
  updateAlert: {
    data: { success: true },
    status: 200,
  },
  deleteAlert: {
    data: true,
    status: 200,
  },
  snoozeAlert: {
    data: { success: true },
    status: 200,
  },
  resumeAlert: {
    data: { message: "success" },
    status: 200,
  },
};

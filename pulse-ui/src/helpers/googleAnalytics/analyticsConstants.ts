/**
 * Analytics Constants
 * 
 * Centralized constants for all analytics events, labels, and screen names.
 * This ensures consistency across the application and makes it easy to update
 * event names in one place.
 */

/**
 * Screen Names
 * Used for screen context in analytics events
 */
export const AnalyticsScreen = {
  HOME: "Home",
  INTERACTION_LIST: "InteractionList",
  INTERACTION_DETAILS: "InteractionDetails",
  APP_VITALS: "AppVitals",
  SCREEN_LIST: "ScreenList",
  SCREEN_DETAILS: "ScreenDetails",
  NETWORK_LIST: "NetworkList",
  NETWORK_DETAILS: "NetworkDetails",
  SDK_CONFIG: "SdkConfig",
  SETTINGS: "Settings",
  UNIVERSAL_QUERY: "UniversalQuery",
  USER_ENGAGEMENT: "UserEngagement",
} as const;

/**
 * Event Labels
 * Common labels used across multiple tracking functions
 */
export const AnalyticsLabels = {
  // Screen/Page names
  HOME: "Home",
  INTERACTION_LIST: "Interaction List",
  APP_VITALS: "App Vitals",
  SCREENS: "Screens",
  NETWORK_APIS: "Network APIs",
  SDK_CONFIG_LIST: "SDK Config List",
  USER_ENGAGEMENT: "User Engagement",
  UNIVERSAL_QUERY: "Universal Query",
  SETTINGS: "Settings",
  
  // Common actions
  VIEW_JSON: "View JSON",
  USER_LOGGED_OUT: "User logged out",
  UNKNOWN: "unknown",
  
  // Prefixes for dynamic labels
  CRASH_PREFIX: "Crash:",
  ANR_PREFIX: "ANR:",
  VERSION_PREFIX: "Version",
  FILTER_MODE_PREFIX: "Filter Mode:",
  SAMPLING_RATE_PREFIX: "Sampling Rate:",
  QUICK_ACTION_PREFIX: "Quick Action:",
  ATTEMPT_PREFIX: "Attempt:",
  SUCCESS_PREFIX: "Success:",
  FAILED_PREFIX: "Failed:",
  BASED_ON_PREFIX: "Based on v",
  NEW: "New",
  
  // Timing categories
  SCREEN_TIME: "Screen Time",
} as const;

/**
 * Filter Types
 * Common filter types used across the application
 */
export const AnalyticsFilterType = {
  APP_VERSION: "appVersion",
  OS_VERSION: "osVersion",
  DEVICE: "device",
  DATE_RANGE: "dateRange",
  SEARCH_FILTER_TYPE: "searchFilterType",
  INTERACTION_NAME: "interactionName",
  SCREEN_NAME: "screenName",
} as const;

/**
 * Tab Names
 * Common tab names for tab switch tracking
 */
export const AnalyticsTab = {
  CRASHES: "crashes",
  ANRS: "anrs",
  NON_FATALS: "non_fatals",
  NETWORK: "network",
  ISSUES: "issues",
} as const;

/**
 * Error Types
 * Common error types for error tracking
 */
export const AnalyticsErrorType = {
  QUERY_ERROR: "QueryError",
  OPERATION_ERROR: "OperationError",
  NETWORK_ERROR: "NetworkError",
  VALIDATION_ERROR: "ValidationError",
  UNKNOWN_ERROR: "UnknownError",
} as const;

/**
 * Label Formatters
 * Helper functions to create consistent labels
 */
export const AnalyticsLabelFormatter = {
  crash: (crashId: string) => `${AnalyticsLabels.CRASH_PREFIX} ${crashId}`,
  anr: (anrId: string) => `${AnalyticsLabels.ANR_PREFIX} ${anrId}`,
  version: (version: number) => `${AnalyticsLabels.VERSION_PREFIX} ${version}`,
  filterMode: (mode: string) => `${AnalyticsLabels.FILTER_MODE_PREFIX} ${mode}`,
  samplingRate: (rate: number) => `${AnalyticsLabels.SAMPLING_RATE_PREFIX} ${rate}%`,
  quickAction: (action: string) => `${AnalyticsLabels.QUICK_ACTION_PREFIX} ${action}`,
  attempt: (method: string) => `${AnalyticsLabels.ATTEMPT_PREFIX} ${method}`,
  success: (method: string) => `${AnalyticsLabels.SUCCESS_PREFIX} ${method}`,
  failed: (method: string, reason: string) => `${AnalyticsLabels.FAILED_PREFIX} ${method} - ${reason}`,
  basedOnVersion: (version: number) => `${AnalyticsLabels.BASED_ON_PREFIX}${version}`,
  settingChange: (setting: string, value: string) => `${setting}: ${value}`,
} as const;

/**
 * Event Parameter Keys
 * Consistent parameter names for additional event data
 */
export const AnalyticsParams = {
  SCREEN: "screen",
  SCREEN_NAME: "screen_name",
  FILTER_TYPE: "filter_type",
  FEATURE_NAME: "feature_name",
  NAVIGATION_METHOD: "navigation_method",
  ITEM_TYPE: "item_type",
  PATH: "path",
} as const;

/**
 * Timing Categories
 * Categories for performance timing events
 */
export const AnalyticsTimingCategory = {
  SCREEN_TIME: AnalyticsLabels.SCREEN_TIME,
  OPERATION: "Operation",
  API_CALL: "API Call",
  RENDER: "Render",
} as const;

/**
 * Type exports for TypeScript
 */
export type AnalyticsScreenType = typeof AnalyticsScreen[keyof typeof AnalyticsScreen];
export type AnalyticsLabelType = typeof AnalyticsLabels[keyof typeof AnalyticsLabels];
export type AnalyticsFilterTypeType = typeof AnalyticsFilterType[keyof typeof AnalyticsFilterType];
export type AnalyticsTabType = typeof AnalyticsTab[keyof typeof AnalyticsTab];
export type AnalyticsErrorTypeType = typeof AnalyticsErrorType[keyof typeof AnalyticsErrorType];


/**
 * Constants for hardcoded strings used throughout SessionReplayDetail components
 */

// Tab Labels
export const TABS = {
  ALL: "all",
  EVENTS: "events",
  CONSOLE: "console",
  NETWORK: "network",
  PERFORMANCE: "performance",
} as const;

export const TAB_LABELS = {
  ALL: "All",
  EVENTS: "Events",
  CONSOLE: "Console",
  NETWORK: "Network",
  PERFORMANCE: "Performance",
} as const;

// View Modes
export const VIEW_MODES = {
  TEXT: "text",
  GRAPH: "graph",
} as const;

export const NETWORK_VIEW_MODES = {
  WATERFALL: "waterfall",
  STATUS: "status",
  DURATION: "duration",
} as const;

// Section Headers
export const HEADERS = {
  CONVERSION_STATUS: "Conversion Status",
  JOURNEY_TIMING: "Journey Timing",
  PATTERN_DETECTION: "Pattern Detection",
  USER_SEGMENTATION: "User Segmentation",
  AB_TEST_ASSIGNMENT: "A/B Test Assignment",
  FEATURE_ENGAGEMENT: "Feature Engagement",
  PRODUCT_ACTIONS: "Product Actions",
  ISSUE_QUICK_FACTS: "Issue Quick Facts",
  CUSTOMER_IMPACT: "Customer Impact",
  SIMILAR_ISSUES_TODAY: "Similar Issues Today",
  KNOWN_ISSUE: "Known Issue",
  QUICK_ACTIONS: "Quick Actions",
  PREVIOUS_ISSUES: "Previous Issues",
  ROOT_CAUSE_ANALYSIS: "Root Cause Analysis",
  ERROR_PROPAGATION_CHAIN: "Error Propagation Chain",
  CODE_REFERENCES: "Code References",
  ERROR_GROUP_INFO: "Error Group Info",
  RELATED_ISSUES_PRS: "Related Issues & PRs",
  REPRODUCIBILITY: "Reproducibility",
  ENVIRONMENT_INFO: "Environment Info",
  RAW_SESSION_EVENTS: "Raw Session Events",
  NETWORK_REQUESTS_VISUALIZATION: "Network Requests Visualization",
  CRITICAL_INTERACTIONS: "Critical Interactions",
  USER_JOURNEY: "User Journey",
  NETWORK_REQUESTS: "Network Requests",
} as const;

// Field Labels
export const LABELS = {
  GOAL: "Goal",
  STAGE: "Stage",
  FUNNEL_PROGRESS: "Funnel Progress",
  TRANSACTION_VALUE: "Transaction Value",
  ACTUAL_DURATION: "Actual Duration",
  EXPECTED_DURATION: "Expected Duration",
  USER_JOURNEY: "User Journey",
  SIMILAR_SESSIONS_TODAY: "Similar Sessions Today",
  SAME_ERROR_TODAY: "Same Error Today",
  SEGMENT: "Segment",
  COHORT: "Cohort",
  SESSION_TYPE: "Session Type",
  LIFETIME_VALUE: "Lifetime Value",
  USER_STATUS: "User Status",
  USER_ID: "User ID",
  SESSION_DURATION: "Session Duration",
  SESSION_QUALITY: "Session Quality",
  ATTEMPTED_TRANSACTION: "Attempted Transaction",
  QUALITY_SCORE: "Quality Score",
  SESSION_TIME: "Session Time",
  DURATION: "Duration",
  SPEED: "Speed",
  BACK: "Back",
  AFFECTED_USERS: "Affected Users",
  STATUS: "Status",
  WORKAROUND_AVAILABLE: "Workaround Available",
  USERS_AFFECTED: "Users Affected",
  SUCCESSFUL: "Successful",
  COMPONENT: "Component",
  ERROR: "Error",
  FILE: "File",
  LINE: "Line",
  FUNCTION: "Function",
  REPRODUCTION_SCORE: "Reproduction Score",
  APP_VERSION: "App Version",
  BUILD_NUMBER: "Build Number",
  DEPLOYED_AT: "Deployed At",
  FEATURE_FLAGS: "Feature Flags",
  ENGINEERING_ACTIONS: "Engineering Actions",
} as const;

// Status Labels
export const STATUS_LABELS = {
  COMPLETED: "Completed",
  ABANDONED: "Abandoned",
  ANONYMOUS: "Anonymous",
  IDENTIFIED: "Identified",
  FIRST_SESSION: "First Session",
  RETURNING_USER: "Returning User",
  RESOLVED: "Resolved",
  UNRESOLVED: "Unresolved",
  SUCCESS: "SUCCESS",
  FAILED: "FAILED",
  NOT_ATTEMPTED: "NOT ATTEMPTED",
} as const;

// Messages
export const MESSAGES = {
  NO_BUSINESS_CONTEXT: "No business context available for this session.",
  NO_TECHNICAL_CONTEXT: "No technical context available for this session.",
  NO_CRITICAL_ISSUES: "No Critical Issues",
  NO_CRITICAL_ISSUES_DESCRIPTION:
    "Session completed without major errors. User was able to complete their actions successfully.",
  ABANDONED_AT: "Abandoned at:",
  SLOWER_THAN_EXPECTED: "slower",
  FASTER_THAN_EXPECTED: "faster",
  THAN_EXPECTED: "than expected",
  UNKNOWN_FEATURE: "Unknown Feature",
  UNKNOWN: "Unknown",
  ERROR_DETECTED: "Error detected",
  PATTERN_DETECTION_MESSAGE:
    "This is a <strong>pattern</strong>! {count} other users experienced the same issue today. Consider escalating to engineering.",
  AB_TEST_MESSAGE:
    "This user was in <strong>{variant}</strong>. Compare performance against control group.",
  LOADING_SESSION_REPLAY_IMAGES: "Loading session replay images...",
  NO_IMAGE_FOUND: "No image found for time {time}ms ({count} images available)",
  SESSION_REPLAY_PLAYER: "Session Replay Player",
  SESSION_RECORDING_DESCRIPTION:
    "This area will display the session recording.",
  SESSION_RECORDING_DETAILS:
    "For web: rrweb DOM replay | For mobile: Wireframe reconstruction",
  INTEGRATION_READY: "Integration Ready",
  SYNCED_TO: "Synced to:",
} as const;

// Button Labels
export const BUTTON_LABELS = {
  CREATE_FUNNEL_ANALYSIS: "Create Funnel Analysis",
  FIND_SIMILAR_DROP_OFFS: "Find Similar Drop-offs",
  ADD_TO_WATCH_LIST: "Add to Watch List",
  VIEW_SIMILAR_SESSIONS: "View Similar Sessions",
  VIEW_IN_GITHUB: "View in GitHub",
} as const;

// Event Types
export const EVENT_TYPES = {
  SESSION_START: "session_start",
  APP_LIFECYCLE: "app_lifecycle",
  SCREEN_LOAD: "screen_load",
  CRITICAL_INTERACTION: "critical_interaction",
  API_CALL: "api_call",
  INTERACTION_TAP: "interaction_tap",
  DB_QUERY: "db_query",
  NETWORK_PERFORMANCE: "network_performance",
  CONSOLE_LOG: "console_log",
} as const;

// Event Descriptions
export const EVENT_DESCRIPTIONS = {
  SESSION_STARTED: "Session Started",
  APP_LIFECYCLE_INIT: "App Lifecycle Init",
  INTERACTION_TAP_PREFIX: "Interaction Tap -",
  SCREEN_LOAD_PREFIX: "Screen Load -",
  API_CALL_PREFIX: "API Call -",
  CRITICAL_INTERACTION_PREFIX: "Critical Interaction -",
  CRITICAL_INTERACTION_SUFFIX_SUCCESS: "Success",
  CRITICAL_INTERACTION_SUFFIX_STARTED: "Started",
  NETWORK_PERFORMANCE_SLOW: "Network Performance Slow -",
} as const;

// Chart Labels
export const CHART_LABELS = {
  REQUEST_DURATION: "Request Duration",
  DURATION: "Duration",
  TIME_MS: "Time (ms)",
  DURATION_MS: "Duration (ms)",
  STATUS_2XX_SUCCESS: "2xx Success",
  STATUS_4XX_CLIENT_ERROR: "4xx Client Error",
  STATUS_5XX_SERVER_ERROR: "5xx Server Error",
} as const;

// Chart Tooltips
export const CHART_TOOLTIPS = {
  WATERFALL_FORMAT: "{method} {name}<br/>Status: {status}<br/>Duration: {duration}ms",
  STATUS_FORMAT: "{b}: {c} ({d}%)",
  DURATION_FORMAT: "{name}<br/>Duration: {value}ms",
} as const;

// Network View Mode Labels
export const NETWORK_VIEW_MODE_LABELS = {
  WATERFALL: "Waterfall",
  STATUS: "Status",
  DURATION: "Duration",
} as const;

// Placeholder/Default Values
export const DEFAULTS = {
  SESSION_ID_UNKNOWN: "session_unknown",
  EXPECTED_DURATION_MS: 120000,
} as const;

// Format Strings
export const FORMAT_STRINGS = {
  QUALITY_SCORE: "{score}/10",
  CRITICAL_INTERACTION_FORMAT:
    "{displayName} CII - {status}",
  API_CALL_FORMAT: "{method} {url}",
  NETWORK_PERFORMANCE_FORMAT: "{duration}ms",
  SUCCESSFUL_COUNT: "{success}/{total} Successful",
  USERS_AFFECTED: "{count} Users Affected",
  REPRODUCIBILITY_SCORE: "{score}% Reproducible",
  FEATURE_FLAG_ON: "ON",
  FEATURE_FLAG_OFF: "OFF",
} as const;

// Additional constants
export const STATUS_LABELS_EXTENDED = {
  ...STATUS_LABELS,
  IDENTIFIED_UPPERCASE: "IDENTIFIED",
  MERGED: "merged",
  OPEN: "open",
  SUSPECT: "Suspect",
} as const;

export const MESSAGES_EXTENDED = {
  ...MESSAGES,
  COPIED: "Copied",
  COPY: "Copy",
  COPIED_EXCLAMATION: "Copied!",
} as const;

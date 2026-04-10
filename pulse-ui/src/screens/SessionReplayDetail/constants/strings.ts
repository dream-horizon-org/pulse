/**
 * Constants for hardcoded strings used throughout SessionReplayDetail components
 */

// Tab Labels
export const TABS = {
  ALL: "all",
  INTERACTION: "interaction",
  CONSOLE: "console",
  NETWORK: "network",
  PERFORMANCE: "performance",
  USER_JOURNEY: "user-journey",
} as const;

export const TAB_LABELS = {
  ALL: "All",
  INTERACTION: "Interaction",
  CONSOLE: "Console",
  NETWORK: "Network",
  PERFORMANCE: "App Vitals",
  USER_JOURNEY: "User Journey",
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
  RAW_SESSION_EVENTS: "",
  SESSION_TIMELINE: "Session timeline",
  SESSION_REPLAY_NETWORK_TITLE: "Network requests",
  SESSION_REPLAY_APP_VITALS_TITLE: "App vitals",
  SESSION_REPLAY_USER_JOURNEY_TITLE: "User journey",
  SESSION_REPLAY_CONSOLE_TITLE: "Console",
  NETWORK_REQUESTS_VISUALIZATION: "Network Requests Visualization",
  CRITICAL_INTERACTIONS: "Interaction",
  USER_JOURNEY: "User Journey",
  NETWORK_REQUESTS: "Network Requests",
  ENGINEERING_ACTIONS: "Engineering Actions",
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
  SESSION_ID: "Session ID",
  USER_ID: "User ID",
  SESSION_DURATION: "Session Duration",
  SESSION_QUALITY: "Session Quality",
  ATTEMPTED_TRANSACTION: "Attempted Transaction",
  QUALITY_SCORE: "Quality Score",
  QUALITY: "Quality",
  SESSION_TIME: "Session Time",
  START_TIME: "Start Time",
  PLATFORM: "Platform",
  QUALITY_RANGE_HINT: " (0-1)",
  DURATION: "Duration",
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
  OCCURRENCES: "Occurrences",
  FIRST_SEEN: "First Seen",
  TREND: "Trend",
  REPRODUCTION_STEPS: "Reproduction Steps",
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
  NO_CRITICAL_INTERACTIONS:
    "No critical interactions were recorded for this session.",
  SESSION_TIMELINE_DESCRIPTION:
    "Unified stream of interactions, network, console, and errors aligned to replay time.",
  CRITICAL_INTERACTIONS_DESCRIPTION:
    "Tracked interactions with outcome, latency, and Apdex. Select a row to seek the replay to that moment.",
  SESSION_REPLAY_NETWORK_DESCRIPTION:
    "HTTP calls made during this session. Switch between List and Graph for timing views.",
  SESSION_REPLAY_APP_VITALS_DESCRIPTION:
    "Exceptions and crashes with stack traces and trace identifiers when available.",
  SESSION_REPLAY_USER_JOURNEY_DESCRIPTION:
    "Navigation sequence and screens visited during this session. Steps align to replay time.",
  SESSION_REPLAY_CONSOLE_DESCRIPTION:
    "Console logs will be available here in a future update.",
} as const;

// Button Labels
export const BUTTON_LABELS = {
  COPY_REPRO_STEPS: "Copy Reproduction Steps",
  CREATE_JIRA_TICKET: "Create Jira Ticket",
  LINK_TO_PR: "Link to PR",
  VIEW_ERROR_GROUP: "View Error Group",
  CREATE_FUNNEL_ANALYSIS: "Create Funnel Analysis",
  FIND_SIMILAR_DROP_OFFS: "Find Similar Drop-offs",
  ADD_TO_WATCH_LIST: "Add to Watch List",
  VIEW_SIMILAR_SESSIONS: "View Similar Sessions",
  VIEW_IN_GITHUB: "View in GitHub",
  VIEW_ALL_OCCURRENCES: "View All Occurrences",
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

// Raw Session Events: category display names and dot colors (Type: Content: Status)
export const RAW_EVENT_CATEGORIES = {
  INTERACTION: { label: "Interaction", color: "#8b5cf6" },
  EVENT: { label: "Event", color: "#0ea5e9" },
  CONSOLE: { label: "Console", color: "#64748b" },
  NETWORK: { label: "Network", color: "#10b981" },
  PERFORMANCE: { label: "Performance", color: "#f59e0b" },
  ERROR: { label: "Error", color: "#ef4444" },
  SESSION: { label: "Session", color: "#6b7280" },
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
  WATERFALL_FORMAT:
    "{method} {name}<br/>Status: {status}<br/>Duration: {duration}ms",
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
  QUALITY_SCORE: "{score}",
  CRITICAL_INTERACTION_FORMAT: "{displayName} CII - {status}",
  API_CALL_FORMAT: "{method} {url}",
  NETWORK_PERFORMANCE_FORMAT: "{duration}ms",
  SUCCESSFUL_COUNT: "{success}/{total} Successful",
  SUCCESSFUL_COUNT_CAPS: "{success}/{total} SUCCESSFUL",
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

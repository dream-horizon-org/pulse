/**
 * Session List page – copy, config, and magic numbers in one place.
 */

import type { SortField } from "../../../services/sessionReplay";

/** Sort fields wired to clickable column headers on the session list */
export const SESSION_LIST_SORT_FIELD = {
  START_TIME: "START_TIME",
  DURATION: "DURATION",
  QUALITY_SCORE: "QUALITY_SCORE",
} as const satisfies {
  START_TIME: SortField;
  DURATION: SortField;
  QUALITY_SCORE: SortField;
};

export const DEFAULT_DATE_PRESET = "24h";

/** Sessions started within this window are still ongoing and excluded from preset filters */
export const ONGOING_SESSION_BUFFER_MS = 5 * 60 * 1000;

export const TIME_RANGE_OPTIONS = [
  { value: "1h", label: "Last hour" },
  { value: "24h", label: "Last 24 hours" },
  { value: "7d", label: "Last 7 days" },
  { value: "30d", label: "Last 30 days" },
  { value: "custom", label: "Custom Range" },
] as const;

export const SEARCH_DEBOUNCE_MS = 500;

/** Quality score thresholds for color (teal / orange / red) */
export const QUALITY_THRESHOLDS = {
  HIGH: 0.8,
  MEDIUM: 0.6,
} as const;

/** Max journey segments shown in table before truncation (legacy) */
export const JOURNEY_DISPLAY_LIMIT = 3;

/** Max impacted screen names shown in table before truncation */
export const IMPACTED_SCREENS_DISPLAY_LIMIT = 3;

/** Fixed height (px) for each virtualized session row; chip columns clip with ellipsis */
export const SESSION_LIST_ROW_HEIGHT_PX = 72;

/** Horizontal gap between chips inside Issues / Impacted cells */
export const SESSION_LIST_CHIP_ROW_GAP_PX = 12;

/** Padding between Issues and Impacted columns (prevents chip overlap at column boundary) */
export const SESSION_LIST_ISSUES_IMPACTED_GUTTER_PX = 16;

/** Gap between chip group and trailing "…" in a cell */
export const SESSION_LIST_CHIP_ELLIPSIS_GAP_PX = 8;

export const SESSION_LIST_LABELS = {
  backToInsights: "Back to Insights",
  pageTitle: "Session List",
  pageSubtitle:
    "Watch reconstructed user sessions to understand why interactions failed, conversions dropped, or users got frustrated. Click a session to open the replay and see the full journey.",
  emptyStateSubtitleFiltered: "Filtered sessions based on your selection",
  emptyStateTitle: "No Sessions Found",
  emptyStateDescriptionWithFilters:
    "Try adjusting your filters to see more results.",
  emptyStateDescriptionDefault:
    "Session replay data will appear here once your app starts sending telemetry.",
  clearAllFilters: "Clear All Filters",
  removeLastFilter: "Remove Last Filter",
  timeRangeLabel: "Time range:",
  timeRangePlaceholder: "Select range",
  fromDatePlaceholder: "From date",
  toDatePlaceholder: "To date",
  sectionTitle: "Sessions for Investigation",
  sectionDescription:
    "Click on any session to watch the replay and understand the full user journey",
  sessionsCountSuffix: "SESSIONS",
  quickFiltersLabel: "Quick filters:",
  advancedFilters: "Advanced Filters",
  searchPlaceholder: "Search by User ID or Session ID",
  activeFiltersCount: "active",
  previous: "Previous",
  next: "Next",
  loading: "Loading sessions...",
  anonymousUser: "Anonymous",
  noQuality: "Na",
  noImpactedScreens: "Na",
  watchSession: "Watch session",
  openInNewTab: "Open in new tab",
  clean: "Clean",
  /** Shown when a row truncates chips (Issues / Impacted Interactions) */
  truncationEllipsis: "…",
  crashed: "Crashed",
  failed: "Failed",
  error: "Error",
  errors: "Errors",
  slow: "Slow",
  filteredViewTitle: "Filtered View",
  filteredViewMessage: "Showing sessions for:",
  /** Section header for the list of applied advanced filter chips */
  advancedFiltersSection: "Advanced filters",
} as const;

export const TABLE_COLUMN_LABELS = {
  startTime: "Start Time",
  duration: "Duration",
  user: "User",
  quality: "Quality",
  issues: "Issues",
  platform: "Platform",
  impactedScreens: "Impacted Interactions",
} as const;

export const PLATFORM_COLORS: Record<string, string> = {
  iOS: "blue",
  Android: "green",
};

export const DEFAULT_PLATFORM_COLOR = "gray";

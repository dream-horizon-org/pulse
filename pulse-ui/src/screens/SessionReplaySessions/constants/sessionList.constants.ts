/**
 * Session List page – copy, config, and magic numbers in one place.
 */

export const DEFAULT_DATE_PRESET = "7d";

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

export const SESSION_LIST_LABELS = {
  backToInsights: "Back to Insights",
  pageTitle: "Session List",
  pageSubtitle:
    "Watch reconstructed user sessions to understand why interactions failed, conversions dropped, or users got frustrated",
  emptyStateSubtitleFiltered: "Filtered sessions based on your selection",
  emptyStateTitle: "No Sessions Found",
  emptyStateDescriptionWithFilters:
    "Try adjusting your filters to see more results.",
  emptyStateDescriptionDefault:
    "Session replay data will appear here once your app starts sending telemetry.",
  clearAllFilters: "Clear All Filters",
  timeRangeLabel: "Time range:",
  timeRangePlaceholder: "Select range",
  fromDatePlaceholder: "From date",
  toDatePlaceholder: "To date",
  sectionTitle: "Sessions for Investigation",
  sectionDescription:
    "Click on any session to watch the replay and understand the full user journey",
  quickFiltersLabel: "Quick filters:",
  advancedFilters: "Advanced Filters",
  searchPlaceholder: "Search by userId, sessionId...",
  activeFiltersCount: "active",
  previous: "Previous",
  next: "Next",
  loading: "Loading sessions...",
  moreAvailable: "(more available)",
  anonymousUser: "Anonymous",
  noQuality: "—",
  watchSession: "Watch session",
  openInNewTab: "Open in new tab",
  clean: "Clean",
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
  impactedScreens: "Impacted Screens",
  actions: "Actions",
} as const;

export const PLATFORM_COLORS: Record<string, string> = {
  iOS: "blue",
  Android: "green",
};

export const DEFAULT_PLATFORM_COLOR = "gray";

export const ACTIONS_COLUMN_WIDTH = 100;

export interface UseAnalyticsOptions {
  /** Auto-track time spent on screen when unmounting */
  trackTimeSpent?: boolean;
  /** Additional properties to include with all events */
  defaultProperties?: Record<string, string | number | boolean>;
}

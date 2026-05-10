/**
 * Configuration constants for App Vitals
 */

export const ISSUE_TYPES = {
  CRASHES: "crashes",
  ANRS: "anrs",
  NON_FATALS: "nonFatals",
  WEB_VITALS: "webVitals",
} as const;

export type IssueType = (typeof ISSUE_TYPES)[keyof typeof ISSUE_TYPES];

export const GRAPH_CONFIGS = {
  [ISSUE_TYPES.CRASHES]: {
    title: "Crashes Trend",
    color: "#ef4444", // Red
  },
  [ISSUE_TYPES.ANRS]: {
    title: "ANRs Trend",
    color: "#f59e0b", // Orange
  },
  [ISSUE_TYPES.NON_FATALS]: {
    title: "Non-Fatal Issues Trend",
    color: "#3b82f6", // Blue
  },
  [ISSUE_TYPES.WEB_VITALS]: {
    title: "Web Vitals Trend",
    color: "#10b981", // Emerald
  },
};

export const APP_VERSIONS = [
  { value: "all", label: "All Versions" },
  { value: "2.4.0", label: "2.4.0" },
  { value: "2.3.5", label: "2.3.5" },
  { value: "2.3.0", label: "2.3.0" },
  { value: "2.2.0", label: "2.2.0" },
  { value: "2.1.0", label: "2.1.0" },
];

export const OS_VERSIONS = [
  { value: "all", label: "All OS Versions" },
  { value: "android-13", label: "Android 13" },
  { value: "android-12", label: "Android 12" },
  { value: "android-11", label: "Android 11" },
  { value: "android-10", label: "Android 10" },
];

export const DEVICES = [
  { value: "all", label: "All Devices" },
  { value: "samsung", label: "Samsung" },
  { value: "google", label: "Google Pixel" },
  { value: "oneplus", label: "OnePlus" },
  { value: "xiaomi", label: "Xiaomi" },
  { value: "oppo", label: "Oppo" },
];

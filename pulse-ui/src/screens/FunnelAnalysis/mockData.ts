export const CONVERSION_WINDOW_OPTIONS = [
  { value: "300", label: "5 Minutes" },
  { value: "900", label: "15 Minutes" },
  { value: "1800", label: "30 Minutes" },
  { value: "3600", label: "1 Hour" },
  { value: "14400", label: "4 Hours" },
  { value: "86400", label: "24 Hours" },
  { value: "259200", label: "3 Days" },
  { value: "604800", label: "7 Days" },
];

export const DATE_RANGE_OPTIONS = [
  { value: "today", label: "Today" },
  { value: "yesterday", label: "Yesterday" },
  { value: "7d", label: "Last 7 Days" },
  { value: "14d", label: "Last 14 Days" },
  { value: "30d", label: "Last 30 Days" },
  { value: "custom", label: "Custom Range" },
];

export const GROUP_BY_OPTIONS = [
  { value: "none", label: "No grouping" },
  { value: "OS", label: "Group by OS" },
];

export function formatDuration(seconds: number): string {
  if (seconds < 60) return `${seconds.toFixed(1)}s`;
  if (seconds < 3600) return `${(seconds / 60).toFixed(1)}m`;
  return `${(seconds / 3600).toFixed(1)}h`;
}

export function getDateRangeFromPreset(preset: string): { start: string; end: string } {
  const now = new Date();
  const end = now.toISOString();
  let start: string;
  switch (preset) {
    case "today":
      start = new Date(now.getFullYear(), now.getMonth(), now.getDate()).toISOString();
      break;
    case "yesterday": {
      const y = new Date(now);
      y.setDate(y.getDate() - 1);
      start = new Date(y.getFullYear(), y.getMonth(), y.getDate()).toISOString();
      break;
    }
    case "14d":
      start = new Date(now.getTime() - 14 * 86400000).toISOString();
      break;
    case "30d":
      start = new Date(now.getTime() - 30 * 86400000).toISOString();
      break;
    case "7d":
    default:
      start = new Date(now.getTime() - 7 * 86400000).toISOString();
      break;
  }
  return { start, end };
}

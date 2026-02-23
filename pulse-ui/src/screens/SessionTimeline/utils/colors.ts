/**
 * Color utilities for the Session Timeline
 * Provides consistent color generation for pulse types, spans, logs, and exceptions
 */

/**
 * Color palette for span types from legacy spanType/statusCode fields.
 * These are kept to support older instrumentation payloads.
 */
const SPAN_TYPE_COLORS = {
  http: "#42a5f5",
  network: "#42a5f5",
  database: "#66bb6a",
  db: "#66bb6a",
  internal: "#ab47bc",
  screen: "#ffa726",
  activity: "#ffa726",
  interaction: "#26c6da",
  crash: "#ff4d4d",
  anr: "#ff9800",
  exception: "#ff4d4d",
} as const;

/**
 * Color palette for exception types
 */
const EXCEPTION_TYPE_COLORS = {
  crash: "#ef4444",
  anr: "#f97316",
  non_fatal: "#eab308",
} as const;

/**
 * Default colors
 */
const DEFAULT_COLORS = {
  blue: "#64b5f6",
  red: "#ef4444",
  grey: "#9e9e9e",
} as const;

/**
 * Get color based on PulseType using deterministic hash
 * Generates consistent colors for filtering - same pulseType always gets same color
 * 
 * @param pulseType - The pulse type string
 * @returns HSL color string
 */
export function getColorForPulseType(pulseType: string): string {
  if (!pulseType) {
    return DEFAULT_COLORS.blue;
  }

  // Generate a deterministic color based on the type string
  let hash = 0;
  for (let i = 0; i < pulseType.length; i++) {
    hash = pulseType.charCodeAt(i) + ((hash << 5) - hash);
  }
  const hue = Math.abs(hash % 360);
  const saturation = 55 + (hash % 20); // 55-75%
  const lightness = 45 + (hash % 15); // 45-60%
  return `hsl(${hue}, ${saturation}%, ${lightness}%)`;
}

/**
 * Get color based on span type or status (legacy mapping).
 * Used for spans that only provide spanType/statusCode without pulseType.
 */
export function getSpanColor(spanType: string, statusCode: string): string {
  // Error states take precedence
  if (statusCode?.toLowerCase() === "error") {
    return "#ff4d4d";
  }

  const normalizedType = spanType.toLowerCase();
  for (const [key, color] of Object.entries(SPAN_TYPE_COLORS)) {
    if (normalizedType.includes(key)) {
      return color;
    }
  }

  return DEFAULT_COLORS.blue;
}

/**
 * Get color for log entries based on severity
 * 
 * @param severityText - The severity text (error, warn, info, debug)
 * @returns Hex color string
 */
export function getLogColor(severityText: string): string {
  const severity = severityText.toLowerCase();
  if (severity.includes("error") || severity.includes("fatal")) {
    return "#ff4d4d";
  }
  if (severity.includes("warn")) {
    return "#ffa726";
  }
  if (severity.includes("info")) {
    return "#66bb6a";
  }
  if (severity.includes("debug")) {
    return "#78909c";
  }
  return "#90a4ae";
}

/**
 * Get color for exception entries based on event type
 * 
 * @param eventName - The event name (crash, anr, non_fatal)
 * @returns Hex color string
 */
export function getExceptionColor(eventName: string): string {
  const normalizedName = eventName.toLowerCase();
  for (const [key, color] of Object.entries(EXCEPTION_TYPE_COLORS)) {
    if (normalizedName.includes(key)) {
      return color;
    }
  }
  return DEFAULT_COLORS.red;
}

/**
 * Get the default orphan color (grey)
 */
export function getOrphanColor(): string {
  return DEFAULT_COLORS.grey;
}

import {
  QUALITY_THRESHOLDS,
  JOURNEY_DISPLAY_LIMIT,
  PLATFORM_COLORS,
  DEFAULT_PLATFORM_COLOR,
} from "../constants/sessionList.constants";

export function formatTimestamp(isoString: string): string {
  const date = new Date(isoString);
  return date.toLocaleString("en-US", {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    hour12: true,
  });
}

export function formatDuration(ms: number): string {
  const seconds = Math.floor(ms / 1000);
  const minutes = Math.floor(seconds / 60);
  const remainingSeconds = seconds % 60;
  if (minutes === 0) return `${seconds}s`;
  return `${minutes}m ${remainingSeconds}s`;
}

export function getQualityColor(score: number): "teal" | "orange" | "red" {
  if (score >= QUALITY_THRESHOLDS.HIGH) return "teal";
  if (score >= QUALITY_THRESHOLDS.MEDIUM) return "orange";
  return "red";
}

export function getPlatformColor(platform: string | undefined): string {
  if (!platform) return DEFAULT_PLATFORM_COLOR;
  return PLATFORM_COLORS[platform] ?? DEFAULT_PLATFORM_COLOR;
}

export function formatJourneyPreview(journey: string[] | undefined): string {
  const list = journey ?? [];
  const segment = list.slice(0, JOURNEY_DISPLAY_LIMIT).join(" → ");
  if (list.length <= JOURNEY_DISPLAY_LIMIT) return segment || "—";
  return `${segment} ...`;
}

export function formatJourneyTooltip(journey: string[] | undefined): string {
  const list = journey ?? [];
  return list.join(" → ") || "—";
}

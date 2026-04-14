import {
  QUALITY_THRESHOLDS,
  JOURNEY_DISPLAY_LIMIT,
  IMPACTED_SCREENS_DISPLAY_LIMIT,
  PLATFORM_COLORS,
  DEFAULT_PLATFORM_COLOR,
  SESSION_LIST_LABELS,
} from "../constants/sessionList.constants";
import type { ImpactedScreens } from "../../../services/sessionReplay/types";

function parseSessionDateTimeMs(input: string): number {
  const t = input.trim();
  if (!t) return NaN;
  if (/^\d{10,}$/.test(t)) {
    const n = Number(t);
    if (!Number.isFinite(n)) return NaN;
    if (n >= 1e12) return n;
    if (n >= 1e9 && n < 1e12) return n * 1000;
    return NaN;
  }
  if (/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}/.test(t)) {
    const iso = t.replace(" ", "T");
    const withZone =
      /Z$/i.test(iso) || /[+-]\d{2}:?\d{2}$/.test(iso) ? iso : `${iso}Z`;
    return new Date(withZone).getTime();
  }
  return new Date(t).getTime();
}

export function formatSessionDisplayTimeMs(ms: number): string {
  if (!Number.isFinite(ms)) return "—";
  const date = new Date(ms);
  if (Number.isNaN(date.getTime())) return "—";
  return date.toLocaleString("en-US", {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: true,
  });
}

export function formatTimestamp(isoString: string): string {
  const ms = parseSessionDateTimeMs(isoString);
  return formatSessionDisplayTimeMs(ms);
}

/** Start instant + span duration, same display style as listing / detail tabs. */
export function formatSessionDisplayEndTime(
  startIsoOrSql: string,
  durationMs: number,
): string {
  const startMs = parseSessionDateTimeMs(startIsoOrSql);
  if (!Number.isFinite(startMs) || !Number.isFinite(durationMs)) return "—";
  return formatSessionDisplayTimeMs(startMs + durationMs);
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

/** Badge color by issue type (CRASH/ANR → red, network/non-fatal → orange, slow/frozen → yellow). */
export function getIssueBadgeColor(issueType: string): string {
  switch (issueType) {
    case "CRASH":
    case "ANR":
    case "INTERACTION_ERROR":
      return "red";
    case "NETWORK_ERROR":
    case "NON_FATAL":
      return "orange";
    case "SLOW_INTERACTION":
    case "FROZEN_FRAME":
      return "yellow";
    default:
      return "gray";
  }
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

/** Flatten impacted screens into a single list with type prefix for display. */
function flattenImpactedScreens(impacted: ImpactedScreens | null): string[] {
  if (!impacted) return [];
  const out: string[] = [];
  if (impacted.crashes?.length) {
    impacted.crashes.forEach((s) => out.push(`Crashes: ${s}`));
  }
  if (impacted.anrs?.length) {
    impacted.anrs.forEach((s) => out.push(`ANRs: ${s}`));
  }
  if (impacted.nonFatals?.length) {
    impacted.nonFatals.forEach((s) => out.push(`Non-fatals: ${s}`));
  }
  return out;
}

/** Lines shown in the session list "Impacted Interactions" column when path data is used (no interaction names). */
export function listImpactedScreensLines(
  impactedScreens: ImpactedScreens | null | undefined,
): string[] {
  return flattenImpactedScreens(impactedScreens ?? null);
}

export function formatImpactedScreensPreview(
  impactedScreens: ImpactedScreens | null | undefined,
): string {
  const list = flattenImpactedScreens(impactedScreens ?? null);
  const segment = list.slice(0, IMPACTED_SCREENS_DISPLAY_LIMIT).join(", ");
  if (list.length <= IMPACTED_SCREENS_DISPLAY_LIMIT) {
    return segment || SESSION_LIST_LABELS.noImpactedScreens;
  }
  return `${segment} ...`;
}

export function formatImpactedScreensTooltip(
  impactedScreens: ImpactedScreens | null | undefined,
): string {
  const list = flattenImpactedScreens(impactedScreens ?? null);
  return list.join("\n") || SESSION_LIST_LABELS.noImpactedScreens;
}

export function formatImpactedInteractionsCellTooltip(
  names: string[] | undefined,
  impactedScreens: ImpactedScreens | null | undefined,
): string {
  const pathLines = flattenImpactedScreens(impactedScreens ?? null);
  const nameBlock = names?.length ? names.join("\n") : "";
  if (nameBlock && pathLines.length) {
    return `${nameBlock}\n\n${pathLines.join("\n")}`;
  }
  if (nameBlock) return nameBlock;
  return pathLines.join("\n") || SESSION_LIST_LABELS.noImpactedScreens;
}

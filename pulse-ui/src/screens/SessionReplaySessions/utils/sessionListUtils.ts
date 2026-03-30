import {
  QUALITY_THRESHOLDS,
  JOURNEY_DISPLAY_LIMIT,
  IMPACTED_SCREENS_DISPLAY_LIMIT,
  PLATFORM_COLORS,
  DEFAULT_PLATFORM_COLOR,
  SESSION_LIST_LABELS,
} from "../constants/sessionList.constants";
import type {
  ImpactedScreens,
  IssueItem,
  SessionItem,
} from "../../../services/sessionReplay/types";

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

/** Higher = more important for session list (top-N display). */
const ISSUE_SEVERITY_RANK: Record<string, number> = {
  CRASH: 100,
  ANR: 90,
  INTERACTION_ERROR: 85,
  NETWORK_ERROR: 75,
  NON_FATAL: 65,
  SLOW_INTERACTION: 55,
  FROZEN_FRAME: 45,
};

/**
 * Most important issues first (for table: show top {@link ISSUES_DISPLAY_LIMIT}).
 */
export function sortIssuesBySeverity(issues: IssueItem[]): IssueItem[] {
  return [...issues].sort((a, b) => {
    const ra = ISSUE_SEVERITY_RANK[a.type] ?? 0;
    const rb = ISSUE_SEVERITY_RANK[b.type] ?? 0;
    if (rb !== ra) return rb - ra;
    return b.count - a.count;
  });
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

type ImpactedLine = { path: string; line: string; rank: number };

/**
 * Human-readable interaction name from a route path (mock + API use paths; UI shows names).
 * e.g. /tab/contests → "Contests", /contest-detail → "Contest Detail"
 */
export function pathToInteractionName(path: string): string {
  const trimmed = path.trim();
  if (!trimmed) return "";
  const parts = trimmed.replace(/\/+$/, "").split("/").filter(Boolean);
  const last = parts[parts.length - 1] ?? trimmed;
  return last
    .replace(/[-_]/g, " ")
    .replace(/\b\w/g, (c) => c.toUpperCase());
}

/** rank: lower = higher severity (used when same path appears in multiple buckets). */
function impactedInteractionsSorted(
  impacted: ImpactedScreens | null,
): ImpactedLine[] {
  if (!impacted) return [];
  const raw: ImpactedLine[] = [];
  if (impacted.crashes?.length) {
    impacted.crashes.forEach((p) =>
      raw.push({
        path: p,
        line: `Crashes: ${pathToInteractionName(p)}`,
        rank: 0,
      }),
    );
  }
  if (impacted.anrs?.length) {
    impacted.anrs.forEach((p) =>
      raw.push({
        path: p,
        line: `ANRs: ${pathToInteractionName(p)}`,
        rank: 1,
      }),
    );
  }
  if (impacted.nonFatals?.length) {
    impacted.nonFatals.forEach((p) =>
      raw.push({
        path: p,
        line: `Non-fatals: ${pathToInteractionName(p)}`,
        rank: 2,
      }),
    );
  }
  raw.sort((a, b) => {
    const cmp = a.path.localeCompare(b.path, undefined, {
      sensitivity: "base",
    });
    if (cmp !== 0) return cmp;
    return a.rank - b.rank;
  });
  const seen = new Set<string>();
  const deduped: ImpactedLine[] = [];
  for (const row of raw) {
    if (seen.has(row.path)) continue;
    seen.add(row.path);
    deduped.push(row);
  }
  return deduped;
}

export function formatImpactedScreensPreview(
  impactedScreens: ImpactedScreens | null | undefined,
): string {
  const list = impactedInteractionsSorted(impactedScreens ?? null);
  const segment = list
    .slice(0, IMPACTED_SCREENS_DISPLAY_LIMIT)
    .map((r) => r.line)
    .join(", ");
  if (list.length === 0) {
    return SESSION_LIST_LABELS.noImpactedScreens;
  }
  if (list.length <= IMPACTED_SCREENS_DISPLAY_LIMIT) {
    return segment;
  }
  return `${segment} ...`;
}

export function formatImpactedScreensTooltip(
  impactedScreens: ImpactedScreens | null | undefined,
): string {
  const list = impactedInteractionsSorted(impactedScreens ?? null);
  return (
    list.map((r) => r.line).join("\n") || SESSION_LIST_LABELS.noImpactedScreens
  );
}

/**
 * Session list “Critical interactions” column — same Pulse names as session detail
 * Interaction tab when {@link SessionItem.criticalInteractionNames} is set.
 */
export function formatCriticalInteractionsPreview(
  session: Pick<SessionItem, "criticalInteractionNames" | "impactedScreens">,
): string {
  const names = session.criticalInteractionNames;
  if (names?.length) {
    return names
      .slice(0, IMPACTED_SCREENS_DISPLAY_LIMIT)
      .join(", ");
  }
  return formatImpactedScreensPreview(session.impactedScreens);
}

export function formatCriticalInteractionsTooltip(
  session: Pick<SessionItem, "criticalInteractionNames" | "impactedScreens">,
): string {
  const names = session.criticalInteractionNames;
  if (names?.length) {
    return names.join("\n");
  }
  return formatImpactedScreensTooltip(session.impactedScreens);
}

export type CriticalInteractionChip =
  | { kind: "pulse"; name: string }
  | { kind: "legacy"; line: string; rank: 0 | 1 | 2 };

/** Up to {@link IMPACTED_SCREENS_DISPLAY_LIMIT} chips for the session list column. */
export function getCriticalInteractionChips(
  session: Pick<SessionItem, "criticalInteractionNames" | "impactedScreens">,
): CriticalInteractionChip[] {
  const names = session.criticalInteractionNames;
  if (names?.length) {
    return names
      .slice(0, IMPACTED_SCREENS_DISPLAY_LIMIT)
      .map((name) => ({ kind: "pulse" as const, name }));
  }
  const sorted = impactedInteractionsSorted(session.impactedScreens ?? null);
  return sorted.slice(0, IMPACTED_SCREENS_DISPLAY_LIMIT).map((row) => ({
    kind: "legacy" as const,
    line: row.line,
    rank: row.rank as 0 | 1 | 2,
  }));
}

/** Full list for tooltips (may be longer than visible chips). */
export function getCriticalInteractionsTooltipLines(
  session: Pick<SessionItem, "criticalInteractionNames" | "impactedScreens">,
): string[] {
  const names = session.criticalInteractionNames;
  if (names?.length) {
    return names;
  }
  return impactedInteractionsSorted(session.impactedScreens ?? null).map(
    (r) => r.line,
  );
}

export function legacyRankToBadgeColor(rank: 0 | 1 | 2): string {
  switch (rank) {
    case 0:
      return "red";
    case 1:
      return "orange";
    default:
      return "gray";
  }
}

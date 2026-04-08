import type {
  HeatmapInteractionElementRegion,
  HeatmapPulseInteractionScore,
} from "./heatmap.types";
import { normalizeInteractionRegions, regionAverageScore } from "./heatmapInteractionUtils";
import { formatInteractionScore01 } from "./heatmapInteractionScores";

export interface PulseInteractionAggregateRow {
  key: string;
  displayName: string;
  score01: number;
  /** How many UI regions reference this interaction */
  elementTouches: number;
}

function rowKey(row: HeatmapPulseInteractionScore, fallback: number): string {
  return row.interaction_id ?? row.name ?? `interaction-${fallback}`;
}

/**
 * Collapse regions → unique Pulse interactions; mean score across occurrences (unweighted).
 */
export function aggregatePulseInteractionsForScreen(
  regions: HeatmapInteractionElementRegion[],
): PulseInteractionAggregateRow[] {
  const norm = normalizeInteractionRegions(regions);
  const acc = new Map<
    string,
    { displayName: string; scoreWeighted: number; w: number; touches: number }
  >();
  let anon = 0;

  for (const r of norm) {
    const scores = r.interaction_scores ?? [];
    for (const row of scores) {
      const key = rowKey(row, anon++);
      const prev = acc.get(key) ?? {
        displayName: row.name ?? row.interaction_id ?? key,
        scoreWeighted: 0,
        w: 0,
        touches: 0,
      };
      prev.displayName = row.name ?? row.interaction_id ?? prev.displayName;
      prev.scoreWeighted += row.score;
      prev.w += 1;
      prev.touches += 1;
      acc.set(key, prev);
    }
  }

  return Array.from(acc.entries()).map(([mapKey, v]) => ({
    key: mapKey,
    displayName: v.displayName,
    score01: v.w > 0 ? Math.round((v.scoreWeighted / v.w) * 10_000) / 10_000 : 0,
    elementTouches: v.touches,
  }));
}

/** Mean of per–element average scores (0–1). */
export function screenPulseInteractionAverage01(
  regions: HeatmapInteractionElementRegion[],
): number | null {
  const norm = normalizeInteractionRegions(regions);
  if (!norm.length) return null;
  const withScores = norm.filter(
    (r) => r.interaction_scores?.length || r.avg_score != null,
  );
  if (!withScores.length) return null;
  const sum = withScores.reduce((s, r) => s + regionAverageScore(r), 0);
  return Math.round((sum / withScores.length) * 10_000) / 10_000;
}

export function formatPulseScore(n: number | null): string {
  return formatInteractionScore01(n);
}

import type {
  HeatmapDataResponse,
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

/** Prefer `interaction_map` regions; otherwise rows from `interactions_metadata`. */
export function pulseInteractionRowsForKeyLens(
  payload: HeatmapDataResponse,
): PulseInteractionAggregateRow[] {
  const regions = payload.layers.interaction_map?.regions ?? [];
  if (regions.length > 0) {
    return aggregatePulseInteractionsForScreen(regions);
  }
  const meta = payload.layers.interactions_metadata ?? [];
  if (meta.length === 0) return [];
  return meta.map((r, i) => ({
    key: `metadata-${i}-${r.interaction_name}`,
    displayName: r.interaction_name,
    score01: Number(r.avg_score),
    elementTouches: 1,
  }));
}

export function screenPulseInteractionAverageFromPayload(
  payload: HeatmapDataResponse,
): number | null {
  const regions = payload.layers.interaction_map?.regions ?? [];
  if (regions.length > 0) {
    return screenPulseInteractionAverage01(regions);
  }
  const meta = payload.layers.interactions_metadata ?? [];
  if (meta.length === 0) return null;
  const sum = meta.reduce((s, r) => s + Number(r.avg_score), 0);
  return Math.round((sum / meta.length) * 10_000) / 10_000;
}

export function keyLensHasInteractionOverlay(
  payload: HeatmapDataResponse,
): boolean {
  return (payload.layers.interaction_map?.regions ?? []).length > 0;
}

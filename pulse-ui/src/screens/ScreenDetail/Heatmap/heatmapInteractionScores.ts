import type {
  HeatmapDataResponse,
  HeatmapGlowPoint,
} from "./heatmap.types";

/** Clamp to [0, 1] with stable rounding for display. */
function clampScore01(raw: number): number {
  const x = Math.min(1, Math.max(0, raw));
  return Math.round(x * 10_000) / 10_000;
}

/**
 * Per-layer readability on **0–1** scale: same blend as map quality — coverage in bins
 * vs total_events and single-bin dominance. Map quality UI still uses 0–100; interaction
 * scores are normalized to 0–1.
 */
export function scoreForInteractionLayer(
  points: HeatmapGlowPoint[],
  totalEvents: number,
): number | null {
  if (!points.length || !Number.isFinite(totalEvents) || totalEvents < 1) {
    return null;
  }
  const sumW = points.reduce((s, p) => s + p.weight, 0);
  const maxW = Math.max(...points.map((p) => p.weight));
  const coverage = Math.min(1, sumW / totalEvents);
  const dominance = sumW > 0 ? maxW / sumW : 0;
  return clampScore01(0.35 * coverage + 0.65 * dominance);
}

/** Format a 0–1 interaction score for UI (two decimal places). */
export function formatInteractionScore01(n: number | null | undefined): string {
  if (n == null || !Number.isFinite(n)) return "—";
  return n.toFixed(2);
}

/** Per-layer and aggregate scores on **[0, 1]** (same formula as map quality, ÷100). */
export interface InteractionLayerScores {
  tap: number | null;
  rage: number | null;
  dead: number | null;
  /** Mean of layers that have a score. */
  average: number | null;
}

export function getInteractionLayerScores(
  payload: HeatmapDataResponse | null | undefined,
): InteractionLayerScores {
  if (!payload) {
    return { tap: null, rage: null, dead: null, average: null };
  }
  const te = Math.max(1, payload.metadata.total_events);
  const glow = payload.layers.glow_map ?? [];
  const rage =
    payload.layers.frustration_map?.rage?.map((r) => ({
      x: r.x,
      y: r.y,
      weight: r.weight,
    })) ?? [];
  const dead =
    payload.layers.frustration_map?.dead?.map((r) => ({
      x: r.x,
      y: r.y,
      weight: r.weight,
    })) ?? [];

  const tap = scoreForInteractionLayer(glow, te);
  const rageS = scoreForInteractionLayer(rage, te);
  const deadS = scoreForInteractionLayer(dead, te);
  const present = [tap, rageS, deadS].filter(
    (x): x is number => x != null,
  );
  const average =
    present.length > 0
      ? clampScore01(
          present.reduce((a, b) => a + b, 0) / present.length,
        )
      : null;

  return { tap, rage: rageS, dead: deadS, average };
}

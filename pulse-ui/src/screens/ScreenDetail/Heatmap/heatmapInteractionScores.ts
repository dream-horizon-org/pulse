import type {
  HeatmapDataResponse,
  HeatmapGlowPoint,
} from "./heatmap.types";

/**
 * Per-layer readability (0–100): same blend as map quality — coverage in bins
 * vs total_events and single-bin dominance. Computed per interaction type.
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
  return Math.min(
    100,
    Math.max(0, Math.round(100 * (0.35 * coverage + 0.65 * dominance))),
  );
}

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
      ? Math.round(present.reduce((a, b) => a + b, 0) / present.length)
      : null;

  return { tap, rage: rageS, dead: deadS, average };
}

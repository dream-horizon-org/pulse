import type { HeatmapDataResponse } from "./heatmap.types";
import type { HeatmapSignal } from "./heatmapPanelUtils";
import { glowLayerForSignal } from "./heatmapPanelUtils";

/** Sum of `weight` across points (aggregated bin weights from the API). */
export function sumWeights(points: Array<{ weight: number }>): number {
  return points.reduce((s, p) => s + p.weight, 0);
}

export interface HeatmapAggregateSnapshot {
  signal: HeatmapSignal;
  /** Number of bins in the layer used for the selected signal. */
  selectedLayerBins: number;
  /** Sum of weights in that layer. */
  selectedLayerWeightSum: number;
  /** Combined glow_map bins (all interactions layer). */
  glowMapBins: number;
  glowMapWeightSum: number;
  rageBins: number;
  rageWeightSum: number;
  deadBins: number;
  deadWeightSum: number;
  totalEventsReported: number | null;
}

export function buildHeatmapAggregateSnapshot(
  payload: HeatmapDataResponse,
  signal: HeatmapSignal,
): HeatmapAggregateSnapshot {
  const layer = glowLayerForSignal(payload, signal);
  const glow = payload.layers.glow_map ?? [];
  const rage = payload.layers.frustration_map?.rage ?? [];
  const dead = payload.layers.frustration_map?.dead ?? [];

  return {
    signal,
    selectedLayerBins: layer.length,
    selectedLayerWeightSum: sumWeights(layer),
    glowMapBins: glow.length,
    glowMapWeightSum: sumWeights(glow),
    rageBins: rage.length,
    rageWeightSum: sumWeights(rage),
    deadBins: dead.length,
    deadWeightSum: sumWeights(dead),
    totalEventsReported:
      payload.metadata.total_events != null
        ? payload.metadata.total_events
        : null,
  };
}

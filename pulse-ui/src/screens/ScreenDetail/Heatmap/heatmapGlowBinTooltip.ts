import type { HeatmapDataResponse, HeatmapGlowPoint } from "./heatmap.types";
import { HEATMAP_SIGNALS, type HeatmapSignal } from "./heatmapPanelUtils";

/** Max squared distance in normalized 0–1 space to treat a bin as “at this spot”. */
const NEIGHBOR_MAX_D2 = 0.055 * 0.055;

/** Relative to the strongest bin on the active layer (for this screen). */
const ZONE_HIGH = 0.55;
const ZONE_MODERATE = 0.18;

function nearestBinWeight(
  nx: number,
  ny: number,
  points: HeatmapGlowPoint[],
  maxD2: number,
): number {
  let bestW = 0;
  let bestD = Infinity;
  for (const p of points) {
    const d = (p.x - nx) ** 2 + (p.y - ny) ** 2;
    if (d <= maxD2 && d < bestD) {
      bestD = d;
      bestW = p.weight;
    }
  }
  return bestW;
}

function asGlowPoints(
  xs: Array<{ x: number; y: number; weight: number }>,
): HeatmapGlowPoint[] {
  return xs.map((p) => ({ x: p.x, y: p.y, weight: p.weight }));
}

function maxLayerWeight(pts: HeatmapGlowPoint[]): number {
  if (!pts.length) return 0;
  return pts.reduce((m, p) => Math.max(m, p.weight), 0);
}

/**
 * Intensity vs other bins on the same layer — plain language, not “hot/cold”.
 */
function activityZoneLabel(weight: number, layerMax: number): string {
  if (layerMax <= 0 || weight <= 0) {
    return "Light activity";
  }
  const ratio = weight / layerMax;
  if (ratio >= ZONE_HIGH) {
    return "High activity";
  }
  if (ratio >= ZONE_MODERATE) {
    return "Moderate activity";
  }
  return "Light activity";
}

export interface GlowBinTooltipModel {
  /** Taps & movement (glow_map) at this spot — “clicks” in product language. */
  totalClicks: number;
  layerLabel: string;
  /** Active map layer count at this bin (matches heatmap). */
  layerValue: number;
  zoneLabel: string;
}

export function buildGlowBinTooltipModel(
  payload: HeatmapDataResponse,
  signal: HeatmapSignal,
  hit: HeatmapGlowPoint,
): GlowBinTooltipModel {
  const tapPts = payload.layers.glow_map ?? [];
  const ragePts = asGlowPoints(payload.layers.frustration_map?.rage ?? []);
  const deadPts = asGlowPoints(payload.layers.frustration_map?.dead ?? []);

  const totalClicks = nearestBinWeight(hit.x, hit.y, tapPts, NEIGHBOR_MAX_D2);

  const layerPts =
    signal === "rage" ? ragePts : signal === "dead" ? deadPts : tapPts;
  const layerMax = maxLayerWeight(layerPts);
  const layerValue = hit.weight;

  return {
    totalClicks,
    layerLabel: HEATMAP_SIGNALS.find((s) => s.id === signal)?.label ?? signal,
    layerValue,
    zoneLabel: activityZoneLabel(layerValue, layerMax),
  };
}

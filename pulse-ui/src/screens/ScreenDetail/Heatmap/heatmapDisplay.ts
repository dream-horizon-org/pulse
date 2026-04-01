import type { HeatmapGlowPoint } from "./heatmap.types";

export function topGlowBinsByWeight(
  points: HeatmapGlowPoint[],
  budget: number,
): HeatmapGlowPoint[] {
  if (!points.length || budget <= 0) {
    return [];
  }
  if (points.length <= budget) {
    return [...points];
  }
  return [...points]
    .sort((a, b) => b.weight - a.weight)
    .slice(0, budget);
}

export interface HeatmapJsDatum {
  x: number;
  y: number;
  value: number;
}

function clamp01(n: number): number {
  if (!Number.isFinite(n)) return 0;
  return Math.min(1, Math.max(0, n));
}

/**
 * Map API coordinates onto 0–1. Supports:
 * - Already normalized 0–1
 * - Percentage 0–100 or pixel ranges (any max > 1 → divide by per-axis max)
 */
export function normalizedGlowXY(points: HeatmapGlowPoint[]): Array<{
  x: number;
  y: number;
  weight: number;
}> {
  if (!points.length) return [];
  const maxX = Math.max(...points.map((p) => p.x));
  const maxY = Math.max(...points.map((p) => p.y));
  const scaleX = maxX > 1;
  const scaleY = maxY > 1;
  const denomX = scaleX ? Math.max(maxX, 1e-9) : 1;
  const denomY = scaleY ? Math.max(maxY, 1e-9) : 1;

  return points.map((p) => ({
    x: clamp01(scaleX ? p.x / denomX : p.x),
    y: clamp01(scaleY ? p.y / denomY : p.y),
    weight: p.weight,
  }));
}

/**
 * heatmap.js needs a spread of numeric `value`; rounding tiny weights to integers
 * collapses everything to 1 and washes the canvas. We either scale against
 * `sharedWeightMax` (compare mode) or min–max the visible bins (single screen).
 */
export function buildHeatmapJsPayload(
  points: HeatmapGlowPoint[],
  width: number,
  height: number,
  sharedWeightMax?: number,
): { max: number; data: HeatmapJsDatum[] } {
  if (!points.length) {
    return { max: 1, data: [] };
  }

  const norm = normalizedGlowXY(points);
  const maxLevel = 100;

  const toPixel = (x: number, y: number) => ({
    x: Math.round(clamp01(x) * Math.max(0, width - 1)),
    y: Math.round(clamp01(y) * Math.max(0, height - 1)),
  });

  if (sharedWeightMax != null && sharedWeightMax > 0) {
    return {
      max: maxLevel,
      data: norm.map((p) => {
        const { x, y } = toPixel(p.x, p.y);
        return {
          x,
          y,
          value: Math.max(
            1,
            Math.min(
              maxLevel,
              Math.round((maxLevel * p.weight) / sharedWeightMax),
            ),
          ),
        };
      }),
    };
  }

  const weights = norm.map((p) => p.weight);
  const wMin = Math.min(...weights);
  const wMax = Math.max(...weights);
  const spread = wMax - wMin;

  if (!Number.isFinite(spread) || spread <= 0) {
    const mid = Math.round(maxLevel / 2);
    return {
      max: maxLevel,
      data: norm.map((p) => {
        const { x, y } = toPixel(p.x, p.y);
        return { x, y, value: mid };
      }),
    };
  }

  return {
    max: maxLevel,
    data: norm.map((p) => {
      const t = (p.weight - wMin) / spread;
      const { x, y } = toPixel(p.x, p.y);
      return {
        x,
        y,
        value: Math.max(
          1,
          Math.min(maxLevel, Math.round(1 + t * (maxLevel - 1))),
        ),
      };
    }),
  };
}

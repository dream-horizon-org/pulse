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
 * Bin center in overlay pixel space — same normalization and mapping as {@link buildHeatmapJsPayload}
 * (0-based x,y within width × height). `layerPoints` must be the same array passed to the heatmap
 * (shared max-X / max-Y scaling).
 */
export function glowBinCenterInOverlayPixels(
  point: HeatmapGlowPoint,
  overlayWidth: number,
  overlayHeight: number,
  layerPoints: HeatmapGlowPoint[],
): { x: number; y: number } {
  const w = Math.max(0, overlayWidth);
  const h = Math.max(0, overlayHeight);
  if (w < 1 || h < 1) return { x: 0, y: 0 };
  const src = layerPoints.length > 0 ? layerPoints : [point];
  const norms = normalizedGlowXY(src);
  const idx = layerPoints.indexOf(point);
  const norm = idx >= 0 ? norms[idx]! : normalizedGlowXY([point])[0];
  return {
    x: Math.round(clamp01(norm.x) * Math.max(0, w - 1)),
    y: Math.round(clamp01(norm.y) * Math.max(0, h - 1)),
  };
}

/**
 * API coordinates are already normalized (nx/ny). Values > 1 are intentional
 * below-fold clicks — do not re-scale. clamp01 is applied at render time.
 */
export function normalizedGlowXY(points: HeatmapGlowPoint[]): Array<{
  x: number;
  y: number;
  weight: number;
}> {
  if (!points.length) return [];
  return points.map((p) => ({ x: p.x, y: p.y, weight: p.weight }));
}

/**
 * heatmap.js needs a spread of numeric `value`; rounding tiny weights to integers
 * collapses everything to 1 and washes the canvas. We either scale against
 * `sharedWeightMax` when set (compare mode **and** single-screen) scales weights to that max;
 * otherwise min–max visible bins (legacy fallback).
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

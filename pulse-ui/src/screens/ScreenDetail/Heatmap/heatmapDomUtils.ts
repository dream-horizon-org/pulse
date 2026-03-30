import { normalizedGlowXY } from "./heatmapDisplay";
import type { HeatmapGlowPoint } from "./heatmap.types";

export function plumeSizePx(weight: number, maxWeight: number): number {
  if (maxWeight <= 0) return 48;
  return 48 + (weight / maxWeight) * 140;
}

const DOM_BUCKET_DIM = 24;
const DOM_RAW_POINT_SOFT_CAP = 384;

/**
 * One div per point is expensive; bucket into a grid and keep max weight per cell.
 */
export function bucketGlowForDom(points: HeatmapGlowPoint[]): HeatmapGlowPoint[] {
  if (points.length <= DOM_RAW_POINT_SOFT_CAP) return points;
  const dim = DOM_BUCKET_DIM;
  const map = new Map<string, HeatmapGlowPoint>();
  for (const p of points) {
    const gx = Math.min(dim - 1, Math.floor(p.x * dim));
    const gy = Math.min(dim - 1, Math.floor(p.y * dim));
    const key = `${gx},${gy}`;
    const x = (gx + 0.5) / dim;
    const y = (gy + 0.5) / dim;
    const prev = map.get(key);
    if (!prev) map.set(key, { x, y, weight: p.weight });
    else prev.weight = Math.max(prev.weight, p.weight);
  }
  return Array.from(map.values());
}

/** Bucketed points, strongest first, capped by `binBudget`. */
export function domGlowMapForBudget(
  glowMap: HeatmapGlowPoint[],
  binBudget: number,
): HeatmapGlowPoint[] {
  const norm = normalizedGlowXY(glowMap);
  const bucketed = bucketGlowForDom(norm);
  const sorted = [...bucketed].sort((a, b) => b.weight - a.weight);
  return sorted.slice(0, Math.min(binBudget, sorted.length));
}

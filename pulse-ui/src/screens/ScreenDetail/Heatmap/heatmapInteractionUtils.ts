import type { HeatmapInteractionElementRegion } from "./heatmap.types";

function clamp01(n: number): number {
  if (!Number.isFinite(n)) return 0;
  return Math.min(1, Math.max(0, n));
}

/**
 * Normalize element bounds to 0–1 like glow_map when API sends pixel space.
 */
export function normalizeInteractionRegions(
  regions: HeatmapInteractionElementRegion[],
): HeatmapInteractionElementRegion[] {
  if (!regions.length) return [];
  const coords = regions.flatMap((r) => [r.minX, r.minY, r.maxX, r.maxY]);
  const peak = Math.max(...coords.map((c) => Math.abs(c)));
  const asPixels = peak > 1.0001;
  if (!asPixels) {
    return regions.map((r) => ({
      ...r,
      minX: clamp01(r.minX),
      minY: clamp01(r.minY),
      maxX: clamp01(r.maxX),
      maxY: clamp01(r.maxY),
    }));
  }
  const d = Math.max(peak, 1e-9);
  return regions.map((r) => ({
    ...r,
    minX: clamp01(r.minX / d),
    minY: clamp01(r.minY / d),
    maxX: clamp01(r.maxX / d),
    maxY: clamp01(r.maxY / d),
  }));
}

export function regionAverageScore(
  r: HeatmapInteractionElementRegion,
): number {
  if (r.avg_score != null && Number.isFinite(r.avg_score)) {
    return r.avg_score;
  }
  const s = r.interaction_scores;
  if (!s.length) return 0;
  return s.reduce((acc, x) => acc + x.score, 0) / s.length;
}

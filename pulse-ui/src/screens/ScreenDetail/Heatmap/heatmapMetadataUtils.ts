import type { HeatmapMetadata } from "./heatmap.types";

/**
 * Ordered list of background screenshots for the heatmap underlay.
 * When `screenshot_urls` is non-empty, it is the carousel (same glow_map for all).
 * Otherwise falls back to a single `screenshot_url`.
 */
export function screenshotUrlsFromMetadata(meta: HeatmapMetadata): string[] {
  const list = (meta.screenshot_urls ?? [])
    .map((u) => (typeof u === "string" ? u.trim() : ""))
    .filter(Boolean);
  if (list.length > 0) {
    const seen = new Set<string>();
    return list.filter((u) => (seen.has(u) ? false : !!seen.add(u)));
  }
  const primary = meta.screenshot_url?.trim();
  return primary ? [primary] : [];
}

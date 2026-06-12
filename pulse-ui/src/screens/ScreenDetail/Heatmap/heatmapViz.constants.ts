/**
 * Default heatmap underlay — Pulse React Native telemetry test screen
 * (`public/heatmap-pulse-rn-telemetry-underlay.png`). Honors `PUBLIC_URL` when set.
 */
export const HEATMAP_DEFAULT_UNDERLAY_URL =
  (process.env.PUBLIC_URL || "") + "/heatmap-pulse-rn-telemetry-underlay.png";

/** When the primary `screenshot_url` fails (CORS, 404, etc.). */
export const HEATMAP_SCREEN_FALLBACK_URL =
  "https://placehold.co/390x844/1a1b1e/c1c2c5/png?text=Screen+underlay";

/**
 * heatmap.js palette — opaque `rgb()` so intensity drives visibility (library
 * applies `minOpacity`/`maxOpacity`). Saturated, darker cool end reads on white UIs.
 */
export const HEATMAP_JS_GRADIENT: Record<string, string> = {
  "0.0": "rgb(0, 75, 130)",
  "0.22": "rgb(0, 128, 142)",
  "0.45": "rgb(30, 165, 125)",
  "0.65": "rgb(255, 165, 55)",
  "0.85": "rgb(235, 95, 55)",
  "1.0": "rgb(200, 45, 55)",
};

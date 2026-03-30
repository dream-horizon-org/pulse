/**
 * Default heatmap underlay — Pulse React Native telemetry test screen
 * (`public/heatmap-pulse-rn-telemetry-underlay.png`). Honors `PUBLIC_URL` when set.
 */
export const HEATMAP_DEFAULT_UNDERLAY_URL =
  (process.env.PUBLIC_URL || "") + "/heatmap-pulse-rn-telemetry-underlay.png";

/**
 * Client-demo wireframe underlay — local SVG, no third-party masking or broken PNG fallbacks.
 */
export const HEATMAP_DEMO_UNDERLAY_URL =
  (process.env.PUBLIC_URL || "") + "/heatmap-demo-underlay.svg";

/** @deprecated Prefer {@link HEATMAP_DEMO_UNDERLAY_URL} for mocks */
export const HEATMAP_HOME_UNDERLAY_URL = HEATMAP_DEMO_UNDERLAY_URL;

/** When the primary `screenshot_url` fails (CORS, 404, etc.). */
export const HEATMAP_SCREEN_FALLBACK_URL =
  "https://placehold.co/390x844/1a1b1e/c1c2c5/png?text=Screen+underlay";

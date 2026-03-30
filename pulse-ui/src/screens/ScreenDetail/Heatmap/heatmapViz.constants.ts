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

/**
 * Demo underlay when the API omits `screenshot_url` or the image fails to load —
 * generic e‑commerce checkout wireframe (`public/heatmap-checkout-underlay.svg`).
 */
export const HEATMAP_CHECKOUT_UNDERLAY_URL =
  (process.env.PUBLIC_URL || "") + "/heatmap-checkout-underlay.svg";

/**
 * Inline SVG last resort if checkout asset fails to load (avoids redirect loops).
 */
export const HEATMAP_UNDERLAY_LAST_RESORT =
  "data:image/svg+xml," +
  encodeURIComponent(
    '<svg xmlns="http://www.w3.org/2000/svg" width="390" height="844"><rect fill="#ffffff" width="100%" height="100%"/><text x="195" y="420" text-anchor="middle" font-family="system-ui,sans-serif" font-size="15" fill="#495057">Checkout</text></svg>',
  );

/** @deprecated Prefer {@link HEATMAP_CHECKOUT_UNDERLAY_URL} for fallbacks. */
export const HEATMAP_SCREEN_FALLBACK_URL = HEATMAP_CHECKOUT_UNDERLAY_URL;

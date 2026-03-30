/**
 * Toggle cohesive ecommerce demo data for mock mode (interactions first; extend later).
 * Requires REACT_APP_USE_MOCK_SERVER=true to take effect in the UI.
 */
export function isEcommerceMockThemeEnabled(): boolean {
  return process.env.REACT_APP_ECOMMERCE_MOCK_THEME === "true";
}

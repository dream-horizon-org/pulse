# QA context refresh log (append-only)

Agents and humans: append **one line per refresh** after updating `DEMO-QA-MAP.md` or equivalent when the demo or SDK install path changes.

| Date (UTC) | Summary |
|------------|---------|
| 2026-05-04 | Initial log. Added `DEMO-QA-MAP.md` + ecommerce-demo-manual-qa skill. |
| 2026-05-04 | Guardian review: `trackEvent` custom_events + interaction; interaction install order; `graphify update . --no-viz` in `pulse-web-otel/`. |
| 2026-05-04 | Web Vitals: always-on FCP/FID/TTFB with LCP/INP/CLS; App.tsx master `enabled` only; manual docs + QA map updated. |
| 2026-05-04 | Home: `WebVitalsManualTriggers` — CLS toggle box, INP ~70ms handler, TTFB reload + copy; DEMO-QA-MAP + MANUAL-WEB-VITALS-DEMO updated. |
| 2026-05-05 | Added `/network-lab` demo route with 15 fetch/XHR scenarios (methods, query, headers/body, 404/500-ish, abort/timeout/no-cors) and updated DEMO-QA-MAP for manual network QA. |
| 2026-05-05 | Added `MANUAL-NETWORK-LAB-SCENARIOS.md` documenting all current `network.ts` tested scenarios (E2E-covered + manual-only) and linked it from DEMO-QA-MAP. |

# Web SDK test run log (append-only)

Append E2E / gate results for Web SDK work (per `pulse-web-sdk-sanity`).

## Network review follow-up — 2026-05-06

| Date | Command | Browser | Result | Notes |
|------|---------|---------|--------|-------|
| 2026-05-06 | `yarn vitest run` (cwd `pulse-web-otel`) | — | pass (433/433) | `requestHeaderGetter` plain-object/array; sensitive query redaction; `NetworkInstrumentation` idempotency; `network-instrumentation.test.ts`; XHR stub `DONE=4` on `m1` / `user-identity` mocks. |
| 2026-05-06 | `yarn playwright test --config e2e/playwright.config.ts e2e/m4-network.spec.ts --project=chromium` (cwd `examples/ecommerce-demo`) | Chromium | pass (15/15) | Added Network Lab XHR timeout/abort; `page.route` 10s stall on `https://httpstat.us/**` + `pollLastNetworkZeroTransportErrorSpan` (empty `url.full` on XHR error). |
| 2026-05-06 | `graphify update . --no-viz` (cwd `pulse-web-otel`) | — | pass | 763 nodes / 1018 edges / 113 communities; `graph-cache.md` refreshed. |
| 2026-05-06 | `yarn e2e:web-sdk-gates` (cwd `examples/ecommerce-demo`) | Chromium | pass (166/166) | Full gate after M4 + Network Lab changes. |

### 2026-05-06 — XHR E2E first attempt (symptom → cause → fix)

**Symptom:** `pollProbeHttpSpan(otlp, "httpstat.us")` timed out after Network Lab XHR timeout/abort clicks.

**Cause:** On timeout/abort, `xhr.responseURL` is often empty → `applyPulseHttpClientSpanAttributes` returns early with no `url.full` → substring probe never matched.

**Fix:** Stall third-party URL via `page.route`; poll last span with `pulse.type=network.0` and `error.type=network_error`.

## DB seed / demo static parity — 2026-04-30

| Date | Command | Browser | Result | Notes |
|------|---------|---------|--------|-------|
| 2026-05-05 | `yarn workspace ecommerce-demo playwright test --config e2e/playwright.config.ts e2e/m4-network.spec.ts --project=chromium` | Chromium | pass (13/13) | Added Network Lab UI E2E cases: click `network-lab-fetch-get-local` (`network.200`) and `network-lab-fetch-404` (`network.404` with route-fulfilled 404), plus existing M4 assertions. |
| 2026-05-05 | `yarn workspace ecommerce-demo e2e:web-sdk-gates` | Chromium | fail (149/151) | Unrelated M1 identity fallback regressions: `m1.spec.ts` localStorage/sessionStorage fallback `session.start` timeout (same area pre-existing, not touched by Network Lab changes). |
| 2026-05-05 | `yarn workspace ecommerce-demo playwright test --config e2e/playwright.config.ts e2e/m1.spec.ts --project=chromium -g "falls back"` | Chromium | fail (1/2) | Recheck of failing gate tests: `installation.id falls back to sessionStorage when localStorage throws` still times out waiting `session.start`; in-memory fallback passed. |
| 2026-05-05 | `yarn workspace ecommerce-demo test && yarn workspace ecommerce-demo build` (cwd `pulse-web-otel`) | — | pass | Added `/network-lab` manual QA route with 15 API call variants (fetch/xhr, methods, 404/500-ish, abort/timeout/no-cors); updated demo QA map + refresh log; `graphify update . --no-viz`. |
| 2026-05-05 | `yarn test:run src/__tests__/network-http.test.ts` + `yarn playwright test … e2e/m4-network.spec.ts --project=chromium` (cwd `examples/ecommerce-demo`) | Chromium | pass | M4 hardening: `pagehide` flush helper, OTLP span status, E3 cors / E4 abort / E5 AbortController, C1 `session.start` absent; **`getOtelHttpUrlFromSpan`** fixes empty `url.full` on failed fetch (OTel passes `RequestInit` only). Vitest `network-http` 24/24. `graphify update . --no-viz`. |
| 2026-05-03 | Review polish: PLAN-C P2.4 doc fix, error-path Vitest, E1 `session.id`/`screen.name`, P1 `server.port` finite; PLAN-B deferrals + E2E table | — | pass | Vitest 382; M4 8/8. |
| 2026-05-03 | PLAN-C OTel alignment (`sanitize` credentials, `server.port` 80/443, `network.protocol.version`) | — | pass | Vitest 378; M4 8/8; `yarn lint`. |
| 2026-05-03 | `pulse.type` parity (`AMENDMENT-pulse-type-parity.md`): `networkPulseType`, `findAllNetworkSpans`; removed `PulseType.HTTP` | — | — | Vitest 373 pass; M4 Playwright 8/8; `graphify update . --no-viz`. |
| 2026-05-02 | `yarn test:run` (cwd `pulse-web-otel`) | — | pass | Network `network-http.test.ts` + full Vitest suite (v3-network instrumentation close-out). |
| 2026-05-02 | `yarn playwright test … e2e/m4-network.spec.ts` (cwd `examples/ecommerce-demo`) | Chromium | pass (2/2) | Assert on probe `url.full` — excludes config-fetch `http` spans (404). |
| 2026-05-02 | `yarn workspace ecommerce-demo e2e:web-sdk-gates` | Chromium | pass (142/142) | M4 expanded (P3/P5/E1/E2/C1 + G1); replaces prior 136-row gate run. |
| 2026-05-02 | `yarn playwright test … e2e/m4-network.spec.ts` (8 cases, cwd `examples/ecommerce-demo`) | Chromium | pass (8/8) | P3 XHR, P5 OTLP-ignore, E1 4xx/5xx, E2 `pulse_network_enabled=0`, C1 consent; `App.tsx` query for local network off. |
| 2026-04-30 | `yarn test:run src/__tests__/interactions-config-fetcher.test.ts` | — | pass (7/7) | After aligning MySQL seeds + `interaction-config.json` with mock; no E2E rerun (seed/static JSON only). |
| 2026-05-02 | `yarn test:run` (cwd `pulse-web-otel`) | — | pass (342/342) | Full Vitest suite (web-sdk-guardian / all unit+integration tests). |
| 2026-05-02 | `yarn workspace ecommerce-demo e2e:web-sdk-gates` | Chromium | pass (130/130) | m1 + m2-interactions + web-vitals. |
| 2026-04-30 | `yarn test:run` (cwd `pulse-web-otel`) | — | pass (357/357) | After merge `chore/web-vital-instrumentation` → `feat/web-sdk-clicks`; registry resolves clicks + web vitals. |
| 2026-04-30 | `yarn workspace ecommerce-demo e2e:web-sdk-gates` | Chromium | pass (132/132) | m1 + m2-interactions + web-vitals + m3-clicks (combined gate script). |
| 2026-04-30 | `yarn test:run` (cwd `pulse-web-otel`) | — | pass (360/360) | v2-clicks: `ClickEventBuffer` + wired `clicks.ts`, `click-rage-buffer.test.ts`, m8 pagehide budget (no extra `pagehide` on clicks). |
| 2026-04-30 | `yarn workspace ecommerce-demo e2e:web-sdk-gates` | Chromium | pass (134/134) | After v2-clicks buffer + m3 rage + click gate-off (m1+m2+m3+web-vitals). |
| 2026-04-30 | `yarn workspace ecommerce-demo e2e:web-sdk-gates` | Chromium | pass (134/134) | M3 D2 close-out: tests 1–2 assert `session.id`, `screen.name`, finite numeric coords (skill assertion floor). |
| 2026-04-30 | `yarn playwright test … e2e/m3-clicks.spec.ts` (cwd `examples/ecommerce-demo`) | Chromium | pass (4/4) | M3: rage path asserts body + `click.type` good + widget + finite coords; good-click uses `otlp.reset()` after `session.start`. |
| 2026-04-30 | `yarn workspace ecommerce-demo run e2e:web-sdk-gates` (cwd `pulse-web-otel`) | Chromium | pass (134/134) | Same M3 changes — full gate. |
| 2026-05-04 | `yarn test:run` (cwd `pulse-web-otel`) | — | pass (341/341) | TTFB + always-on FCP/FID; Vitest web-vitals mock includes `onTTFB`. |
| 2026-05-04 | `yarn workspace ecommerce-demo e2e:web-sdk-gates` | Chromium | pass (134/134) | Web vitals: TTFB, FCP, LCP, INP, FID, CLS + gate-off. |
| 2026-05-04 | `yarn test:run` + `yarn workspace ecommerce-demo e2e:web-sdk-gates` (cwd `pulse-web-otel`) | Chromium (E2E) | pass (341/341, 134/134) | Added `examples/web-sdk-docs` vanilla demo; no SDK `src/` changes. |

## Web Vitals (v2) — reserved

| Date | Command | Browser | Result | Notes |
|------|---------|---------|--------|-------|
| 2026-04-30 | `yarn workspace ecommerce-demo e2e:web-sdk-gates` | Chromium | pass | See **Playwright OTLP decode + screen.name** below. |
| 2026-05-02 | `yarn workspace ecommerce-demo e2e:web-sdk-gates` | Chromium | pass (130/130) | 3 WebVitals specs added: LCP full contract, INP tab-hide, gate-disabled. See **INP headless spin-loop** below. |
| 2026-05-04 | `yarn workspace ecommerce-demo e2e:web-sdk-gates` | Chromium | pass (134/134) | Added TTFB, FCP, FID (Chromium), CLS (layout shift + visibility); D2b unchanged. |
| 2026-05-02 | `yarn playwright test --config e2e/playwright.config.ts e2e/web-vitals.spec.ts --project=chromium` (cwd `examples/ecommerce-demo`) | Chromium | pass (3/3) | Lifecycle P1 close-out: INP asserts `web_vital.value` (finite), `session.id`, `screen.name` (parity with LCP test). |

## Error instrumentation rerun (v1-errors) — 2026-05-05

| Date | Command | Browser | Result | Notes |
|------|---------|---------|--------|-------|
| 2026-05-05 | `yarn test:run src/__tests__/m3.test.ts` | — | pass (20/20) | Targeted unit pass after dependency bootstrap in `pulse-web-otel/`. |
| 2026-05-05 | `yarn workspace ecommerce-demo e2e -- e2e/m3-errors.spec.ts --project=chromium` | chromium/firefox/webkit | fail (27 pass, 3 fail) | Failed only on render-boundary assertion expecting fallback text not present in demo app boundary configuration. |
| 2026-05-05 | `yarn workspace ecommerce-demo e2e -- e2e/m3-errors.spec.ts --project=chromium` | chromium/firefox/webkit | pass (30/30) | Fixed assertion to validate `react.component_stack` on `device.crash` instead of non-existent fallback text. |
| 2026-05-05 | `yarn workspace ecommerce-demo e2e:web-sdk-gates` | Chromium | pass (137/137) | Gates now include `e2e/m3-errors.spec.ts`; m1 + m2 + m3 all green. |
| 2026-05-05 | `yarn workspace ecommerce-demo e2e -- e2e/m3-errors.spec.ts --project=chromium` (cwd `pulse-web-otel`) | chromium/firefox/webkit | fail (36 pass, 3 fail) | New coexistence test expected exact listener message; browser prefixes (`Error:`/`Uncaught Error:`) broke strict equality. |
| 2026-05-05 | `yarn playwright test --config e2e/playwright.config.ts e2e/m3-errors.spec.ts --project=chromium` (cwd `examples/ecommerce-demo`) | Chromium | pass (13/13) | Updated coexistence assertion to substring match; expanded suite includes dedupe-different-fingerprint and timestamp checks. |
| 2026-05-05 | `yarn workspace ecommerce-demo e2e:web-sdk-gates` | Chromium | pass (140/140) | Revalidated full gates after expanding M3 errors scenarios and coexistence assertion fix. |
| 2026-05-05 | `yarn workspace ecommerce-demo e2e -- e2e/m3-errors.spec.ts` | chromium/firefox/webkit | pass (39/39) | Final tri-browser validation after coexistence assertion fix. |

### 2026-05-05 — render-boundary E2E mismatch (symptom -> cause -> fix)

**Symptom:** `m3-errors.spec.ts` timed out waiting for `getByText("Render error caught by PulseErrorBoundary")` in all three browser engines.

**Cause:** Demo app does not render that fallback text. `PulseErrorBoundary` fallback is not configured with this string in ecommerce demo route tree; behavior is crash log emission rather than textual fallback UI.

**Fix:** Updated test assertion to check `react.component_stack` on emitted `device.crash` log, which is the actual product contract for render-boundary crash capture.

### 2026-05-05 — error listener coexistence string variance (symptom -> cause -> fix)

**Symptom:** New coexistence test (`existing window error listener still receives events`) failed in Chromium/Firefox/WebKit even though crash exports were present.

**Cause:** Browser `error` event message formatting differs by engine (`Error: ...` vs `Uncaught Error: ...`), so strict `toContain(exactMessage)` on the array did not hold.

**Fix:** Changed assertion to substring match (`some(message => message.includes("Demo uncaught error from ErrorDemo"))`) while still verifying shared listener execution across engines.

### 2026-04-30 — Playwright OTLP decode + screen.name (try this first on similar failures)

**Symptom:** `waitForLog("session.start")` (or any `otlp.waitForLog`) timed out at 8s. Not specific to Web Vitals — any spec using the shared `e2e/fixture.ts` OTLP capture looked “broken.”

**Root cause:** `examples/ecommerce-demo/src/App.tsx` sets `export.format` to **`protobuf`** when `VITE_PULSE_FORMAT` is unset. The Playwright fixture (`attachOtlpCapture` → `decodeBody`) only **`JSON.parse`**’s the POST body (with optional gzip). Protobuf payloads do not decode to `resourceLogs` / `logRecords`, so `captured` stayed effectively empty → no `pulse.type` to match → timeout.

**Fix applied:** `examples/ecommerce-demo/.env.test` (loaded by Vite `--mode test`, used by Playwright `webServer`) now sets:

- `VITE_PULSE_FORMAT=json` — OTLP/JSON bodies the fixture can parse.
- `VITE_PULSE_COMPRESSION=none` — avoids gzip edge cases; fixture supports gzip, but plain JSON is simpler for E2E.

Also removed duplicate `VITE_PULSE_BATCH_DELAY_MS` line in `.env.test`.

**Secondary symptom (after JSON fix):** Two M1 tests expected `screen.name` **`/products`** for URLs `/products/123` and `/products/<uuid>`.

**Cause:** `GlobalAttributesProcessor.resolveScreenName()` normalizes dynamic path segments to **`:id`**, producing **`/products/:id`** (documented in that file). E2E titles/expectations were outdated vs that heuristic.

**Fix applied:** `e2e/m1.spec.ts` — expectations and test names updated to **`/products/:id`**.

**Web Vitals spec only:** Redundant `blockActiveConfigFetch(page)` before `goto` was removed; the `otlp` fixture already calls `attachDefaultSdkConfigStub`. Extra routes were not the primary bug (protobuf was), but avoiding duplicate active-config routing keeps behavior obvious.

### 2026-05-02 — INP headless spin-loop

**Symptom:** `expect(inp).toBeDefined()` failed. `PerformanceEventTiming` entries for `page.click()` in headless Chromium have duration < 5ms — below the 40ms minimum threshold `PerformanceObserver` applies for `event` entries.

**Root cause:** Headless Playwright clicks are dispatched synthetically; there's no real rendering pipeline involvement, so the browser records near-zero processing time. `web-vitals` v4 `onINP` only receives entries with `duration >= 40ms` — nothing to report.

**Fix applied:** `e2e/web-vitals.spec.ts` INP test now injects a one-shot `click` listener via `page.evaluate` that spins the main thread for 70ms. This inflates `PerformanceEventTiming.duration` above threshold, making the interaction a valid INP candidate. A `waitForTimeout(300)` after the click lets the `PerformanceObserver` callback process the entry before `visibilitychange` triggers the report.

**Checklist for next time (INP not captured):**

1. Confirm the click is on a real DOM element (not `body`).
2. Confirm processing time is >= 40ms — inject a spin-loop listener if needed.
3. Confirm `visibilitychange: hidden` is dispatched using `Object.defineProperty(document, "visibilityState", { get: () => "hidden", configurable: true })` (getter form, not `value` form).
4. Add `waitForTimeout(300)` between click and hide to let `PerformanceObserver` flush.
5. Test is `test.skip`-ed for non-Chromium — `PerformanceEventTiming` support varies.

**Phase 6 minimum set (INP):** After spin-loop + `visibilitychange`, assert on the INP log record the same floor as LCP: `pulse.type`, numeric finite `web_vital.value`, `web_vital.rating` enum, truthy `session.id` and `screen.name` (global attrs processor).

**Checklist for next time (timeouts / empty `otlp.captured`):**

1. Confirm demo test mode uses **JSON** OTLP if assertions parse JSON in `fixture.ts` → set `VITE_PULSE_FORMAT=json` in `.env.test` (or equivalent).
2. If CORS/preflight on config is suspected, compare with `attachDefaultSdkConfigStub` (OPTIONS + GET) vs tests that add later `page.route` handlers.
3. If `screen.name` assertions fail after URL changes, read **`resolveScreenName`** in `pulse-web-otel/src/processors/global-attrs-processor.ts` — expect **`:id`** placeholders, not collapsed parent paths like `/products`.
4. Run `yarn workspace ecommerce-demo e2e:web-sdk-gates` (Chromium) after `.env.test` or fixture changes.

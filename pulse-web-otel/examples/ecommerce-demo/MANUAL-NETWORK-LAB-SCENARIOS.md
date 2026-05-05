# Network Lab — tested scenarios

Manual + E2E scenario index for `examples/ecommerce-demo/src/routes/NetworkLab.tsx`
and `examples/ecommerce-demo/e2e/m4-network.spec.ts`.

## Preconditions

- Demo running from `pulse-web-otel/`:
  - `yarn workspace ecommerce-demo dev` (manual)
  - `yarn workspace ecommerce-demo playwright test --config e2e/playwright.config.ts e2e/m4-network.spec.ts --project=chromium` (automated)
- Consent allowed (default), unless testing denied-consent case.
- Network instrumentation enabled (default), unless testing gate/local-off case.

## E2E-covered scenarios

1. **Network Lab button — fetch local GET success**
   - action: click `network-lab-fetch-get-local`
   - expected: span `pulse.type=network.200`, method `GET`, `http.response.status_code=200`

2. **Network Lab button — fetch 404**
   - action: click `network-lab-fetch-404` (route-fulfilled deterministic 404 in spec)
   - expected: `pulse.type=network.404`, `error.type=4xx`, span status ERROR

3. **Fetch success with query params (privacy)**
   - action: fetch `/pulse-e2e-network/data?token=secret`
   - expected: `network.200`, query stripped from `url.full`

4. **XHR success**
   - action: XHR GET `/pulse-e2e-network/xhr-probe?z=1`
   - expected: `network.200`, method `GET`, status `200`

5. **OTLP self-export ignore**
   - action: normal SDK export cycle
   - expected: no network spans for `/v1/logs`, `/v1/traces`, `/v1/metrics`

6. **Remote gate off (`network_instrumentation`)**
   - action: seed config with `sessionSampleRate=0` for network feature, perform fetch
   - expected: zero network spans

7. **Fetch 404 error path**
   - action: fetch `/pulse-e2e-network/err-404`
   - expected: `network.404`, `error.type=4xx`, span status ERROR

8. **Fetch 500 error path**
   - action: fetch `/pulse-e2e-network/err-500`
   - expected: `network.500`, `error.type=5xx`, span status ERROR

9. **Cross-origin no-cors opaque response**
   - action: fetch `http://127.0.0.1:3099/...` with `mode: "no-cors"`
   - expected: `network.0`, `error.type=cors_error`, span status ERROR

10. **Transport failure / abort at route layer**
    - action: route abort("failed"), fetch probe URL
    - expected: `network.0`, `error.type=network_error`, span status ERROR

11. **AbortController abort**
    - action: fetch with signal, immediate `ac.abort()`
    - expected: `network.0`, `error.type=network_error`, span status ERROR

12. **Local instrumentation override off**
    - action: open `/?pulse_network_enabled=0`, then fetch probe
    - expected: zero network spans

13. **Consent denied**
    - action: open `/?pulse_consent=denied`
    - expected: no `session.start`, no network spans

## Manual-only Network Lab scenarios (UI buttons present, not E2E asserted yet)

1. Fetch GET local with query params (`network-lab-fetch-get-query`)
2. Fetch POST JSON (`network-lab-fetch-post-json`)
3. Fetch PUT JSON (`network-lab-fetch-put-json`)
4. Fetch DELETE (`network-lab-fetch-delete`)
5. Fetch 500 remote endpoint (`network-lab-fetch-500`)
6. Fetch immediate abort (`network-lab-fetch-abort`)
7. Fetch timeout-style abort (`network-lab-fetch-timeout`)
8. Fetch no-cors opaque (`network-lab-fetch-no-cors`)
9. XHR GET local JSON (`network-lab-xhr-get-local`)
10. XHR POST JSON (`network-lab-xhr-post-json`)
11. XHR 404 (`network-lab-xhr-404`)
12. XHR timeout (`network-lab-xhr-timeout`)
13. XHR abort (`network-lab-xhr-abort`)

## Quick verify checklist (manual)

- Open `/network-lab`
- Trigger a button
- Open Pulse debug panel (`Shift+P`)
- Validate for the new network span:
  - `pulse.type`
  - `http.request.method`
  - `http.response.status_code` (if present)
  - `error.type` on error paths
  - `session.id`
  - `screen.name`

# ecommerce-demo

Vite + React storefront used as the **Pulse Web SDK** harness (Playwright E2E, manual labs).

**Design spec (HLD + LLD):** [docs/web-vitals-stress-and-synthetic-e2e/SPEC.md](docs/web-vitals-stress-and-synthetic-e2e/SPEC.md) — Web Vitals stress query contract, harness behavior, and synthetic Playwright journey.

## Scripts

| Command | Purpose |
|--------|---------|
| `yarn dev` | Dev server (default port from Vite; E2E uses `3099` via `playwright.config.ts`) |
| `yarn e2e` | All Playwright specs |
| `yarn e2e:web-sdk-gates` | PR gate bundle (M1–M4, web vitals, clicks, network, screen navigation) — **unchanged** when adding optional specs |
| `yarn e2e:synthetic` | Long “synthetic user” journey (Chromium only); OTLP **mocked** in-browser (no ClickHouse) |
| `yarn e2e:synthetic:ingest` | Same journey with **real OTLP** to your collector (e.g. local `:4318` → ClickHouse); start OTEL collector first |
| `yarn e2e:headed` | Debug: `yarn e2e:headed -- e2e/synthetic-user.spec.ts` |

## Web Vitals stress (URL)

Opt-in layout / paint / INP stress for manual QA or synthetic runs. Parsed in `src/webVitalsStressConfig.ts`, applied inside `<main>` by `WebVitalsStressHarness` (wraps `<Suspense><Routes/></Suspense>`).

| Query | Alias | Values / default |
|-------|--------|-------------------|
| `pulse_wv_stress` | — | `off` (absent = off), `cls`, `lcp`, `fcp`, `inp`, `all` |
| `pulse_wv_stress_p` | `_p` | Arm probability per navigation `0…1` (default `0.35` when stress mode ≠ `off`) |
| `pulse_wv_stress_seed` | `_seed` | Optional integer — reproducible RNG with route key |
| `pulse_wv_stress_severity` | `_severity` | `mild` (default) or `severe` — delay / CLS timer bands |

Example (deterministic, always arm):

`/?pulse_wv_stress=all&pulse_wv_stress_seed=42&pulse_wv_stress_p=1`

Manual button-driven CLS/INP on **Home** still lives in `WebVitalsManualTriggers` (README you are reading now, this section).

## Synthetic user E2E

`yarn e2e:synthetic` runs `e2e/synthetic-user.spec.ts` (Chromium): all main routes, navbar navigation, Shop Now / add-to-cart / checkout / Network Lab / render-error → Home, history, reload, periodic storage+cookie clear.

| Env | Default | Meaning |
|-----|---------|--------|
| `SYNTHETIC_ITERATIONS` | `5` | Loop count |
| `SYNTHETIC_CLEAR_STORAGE_EVERY` | `2` | Every N iterations: clear `localStorage` / `sessionStorage`, cookies, reload `/` |
| `SYNTHETIC_STRESS` | unset | Set to `1` to append `pulse_wv_stress=all&pulse_wv_stress_seed=<iter>&pulse_wv_stress_p=1` on navigations |

| `E2E_REAL_OTLP` / `E2E_REAL_OTLP_INGEST` | unset | Set to `1` to skip OTLP `page.route` interception (used by `yarn e2e:synthetic:ingest`). Implies real collector; dev API keys in `.env.test` resolve to `http://localhost:4318`. |

| `E2E_STUB_ACTIVE_CONFIG` | unset | With real OTLP: set to `1` to keep the active-config 404 stub (pulse-server not running). Default is **no** stub so `/v1/configs/active` hits pulse-server. |

At least one iteration should run **without** stress in CI smoke; use `SYNTHETIC_STRESS=1` locally or in nightly when validating the harness.

**Do not** set `E2E_REAL_OTLP` when running `yarn e2e:web-sdk-gates` or any spec that asserts on `otlp.captured`.

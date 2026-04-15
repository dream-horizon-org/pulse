# @dreamhorizon/pulse-web

Pulse observability SDK for web. OpenTelemetry-based RUM — errors, network, clicks, web vitals, navigation, interactions.

## Setup (first time)

```bash
# Node ≥18.13.0 required
corepack enable          # activates Yarn 4 (packageManager field in package.json)
yarn install
```

## Dev loop

```bash
yarn build                                  # tsup → dist/
yarn workspace ecommerce-demo dev           # demo at http://localhost:3002
yarn test:run                               # Vitest unit tests (src/__tests__/)
yarn lint                                   # tsc --noEmit
yarn size-limit                             # bundle size check (core < 30 KB)
```

## E2E tests

```bash
# Install browsers once (from the demo directory):
cd examples/ecommerce-demo
yarn playwright install --with-deps chromium firefox webkit

# Run per milestone:
yarn e2e --grep "@M1"   # session lifecycle, identity, OTLP pipeline
yarn e2e --grep "@M2"   # interactions, APDEX, SDK config, React
yarn e2e --grep "@M3"   # all 5 auto-instrumentations
yarn e2e                # full suite (all browsers)

# Or from SDK root:
yarn workspace ecommerce-demo e2e --grep "@M1"
```

## Implementation milestones

| Milestone | Status | Plan |
|-----------|--------|------|
| P0 — Scaffold + demo | ✅ Done | `.claude/plans/web-sdk-p0-scaffold.md` |
| M1 — Foundation pipeline | ⬜ Next | `.claude/plans/web-sdk-m1-foundation.md` |
| M2 — Interactions + React | ⬜ | `.claude/plans/web-sdk-m2-interactions.md` |
| M3 — Auto-instrumentations | ⬜ | `.claude/plans/web-sdk-m3-instrumentations.md` |
| M4 — Next.js + CDN + CI/CD | ⬜ | `.claude/plans/web-sdk-m4-build.md` |

Live exit criteria: `web-sdk-plan/v1/MILESTONES.md`

## Ecommerce demo routes

| Route | What it exercises |
|-------|-------------------|
| `/` | Landing page |
| `/products` | Product grid — RageClickButton for M3 rage-click test |
| `/products/:id` | Product detail — heuristic screen.name (`products/:id`) |
| `/cart` | Cart — add/remove click targets |
| `/checkout` | 3-step wizard — fires `checkout_step_1/2/3` trackEvent calls |
| `/error-demo` | Throws uncaught, unhandled promise, React render error |

## Environment variables (demo)

Copy `examples/ecommerce-demo/.env.example` → `.env.local`:

```
VITE_PULSE_ENDPOINT_BASE_URL=https://ingest.pulse.io
VITE_PULSE_API_KEY=your-key
VITE_PULSE_SERVICE_NAME=ecommerce-demo
```

---
paths:
  - "pulse-web-otel/**/*.ts"
  - "pulse-web-otel/**/*.tsx"
  - "pulse-web-otel/**/*.js"
---

# Web SDK Conventions

## Package Identity

- **npm:** `@dreamhorizon/pulse-web`
- **Repo:** `pulse-web-otel/` in monorepo root
- **OTLP:** `{endpointBaseUrl}/v1/traces|logs|metrics` with `x-api-key` header
- **Platform tag:** `platform = 'web'` on every signal — never omit

## File Map

```
src/
├── index.ts                        # Only public API surface — all exports here
├── sdk.ts                          # PulseWebSDK singleton
├── config.ts                       # PulseWebConfig + PulseDataCollectionConsent
├── session.ts                      # Installation ID (3-tier) + Session Provider
├── resource.ts                     # OTEL Resource builder (static browser attrs)
├── exporters.ts                    # OTLP exporters + BatchProcessor + gzip + sendBeacon
├── consent.ts                      # Consent guard
├── remote-config.ts                # SDK Config fetcher
├── feature-gate.ts                 # Per-instrumentation enable/disable
├── instrumentation-registry.ts     # install() / uninstall() lifecycle
├── version.ts                      # __SDK_VERSION__ replaced at build time
├── utils/ua-parser.ts
├── utils/compression.ts
├── persistence/indexed-db.ts       # IndexedDB signal buffer
├── processors/global-attrs-processor.ts
├── processors/sampling-processor.ts
├── processors/signal-filter-processor.ts
├── instrumentations/session.ts
├── instrumentations/errors.ts
├── instrumentations/network.ts
├── instrumentations/clicks.ts
├── instrumentations/web-vitals.ts
├── instrumentations/navigation.ts
├── interactions/config-fetcher.ts
├── interactions/interaction-matcher.ts
├── interactions/interaction-manager.ts
├── interactions/interaction-span.ts
└── integrations/react/             # PulseProvider, PulseErrorBoundary, useRouterTracking
    integrations/nextjs/            # PulseNextProvider (App + Pages Router)
    integrations/cdn/               # Async snippet + queue drain
```

## Data Contract — `pulse.type` Values

These must match Android/iOS exactly — they drive the Pulse dashboard.

| `pulse.type` | Kind | Required attrs |
|---|---|---|
| `session.start` | log | `session.id`, `installation.id`, `platform='web'` |
| `session.end` | log | `session.id`, `session.duration_ms`, `screens_visited` |
| `device.crash` | log | `exception.type`, `exception.message`, `exception.stacktrace`, `error.filename` |
| `non_fatal` | log | `exception.type`, `exception.message`, `exception.stacktrace`, `non_fatal.is_manual` |
| `http` | span | `http.method`, `http.url`, `http.status_code`, `http.duration`, `net.peer.name` |
| `app.click` | span | `view.target.class_name`, `view.target.id`, `touch.coordinates.x/y`, `rage_click` |
| `web_vital` | metric gauge | `metric.name`, `metric.value`, `metric.rating` |
| `screen_load` | span | `screen.name`, `ttfb_ms`, `fcp_ms`, `load.duration_ms` |
| `screen_interactive` | span | `screen.name`, `tti_ms` |
| `screen_session` | span | `screen.name`, `previous_screen.name`, `duration_ms` |

## Global Attributes

Injected on every signal via `processors/global-attrs-processor.ts`:
`session.id` · `installation.id` · `screen.name` · `url.path` · `page.url` · `browser.name` · `browser.version` · `os.name` · `os.version` · `device.type` · `network.connection.type` · `rum.sdk.version` · `project.id` · `platform='web'`

## TypeScript Rules

- `strict: true` in tsconfig — no `any`, use `unknown` + type guards
- ESM-first; tsup handles dual ESM + CJS output
- All public API exported from `src/index.ts` only — never import from internal paths
- `__SDK_VERSION__` replaced at build time via tsup `define` — never hardcode version strings

## Instrumentation Pattern

Every instrumentation implements:
```typescript
export interface PulseInstrumentation {
  readonly name: string;
  install(sdk: PulseWebSDK): void;
  uninstall(): void;
}
```
Register via `InstrumentationRegistry` — never call `install()` directly from `sdk.ts`.

## SDK Singleton Guard

`PulseWeb.start()` must be a no-op if already initialised. React StrictMode double-invokes effects — the `if (this.initialized) return` guard handles this. Test this explicitly.

## Bundle Size Budget

- Core (`dist/index.js`): < 30 KB gzip
- React integration: < 2 KB gzip above core
- CDN UMD bundle: < 80 KB gzip
- Check before adding any new dependency.

## Testing

- Vitest + JSDOM
- Mock `window.localStorage`, `navigator`, `crypto.randomUUID` as needed
- Do not mock OTLP exporters — use MSW or a fake HTTP server
- Test files in `src/__tests__/` named `m1.test.ts`, `m2.test.ts`, `m3.test.ts`

## Plan & Spec Docs

- Implementation plan: `.claude/plans/web-sdk-v1.md`
- Live milestone state (checkboxes): `pulse-web-otel/web-sdk-plan/v1/MILESTONES.md`
- Stable context + nav guide: `pulse-web-otel/web-sdk-plan/WEB-SDK-AGENT-CONTEXT.md`
- **Pinned dependency versions:** `pulse-web-otel/web-sdk-plan/v1/00-setup/dependency-versions.md`
- Per-phase specs: `pulse-web-otel/web-sdk-plan/v1/01-foundation/` … `v1/05-build-distribution/`

Do NOT load `pulse-web-otel/web-sdk-plan/pulse-web-sdk-plan.md` or `pulse-web-otel/web-sdk-plan/PLAN-OVERVIEW.md` — human planning docs only.

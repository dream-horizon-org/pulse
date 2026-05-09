# pulse-web-otel — Package context

Per-package operating manual. Auto-loaded by Claude Code (hierarchical CLAUDE.md) alongside the repo-root constitution.

## Fast facts

- Package: `@dreamhorizon/pulse-web` (npm), `0.1.0-alpha` (pre-release)
- Build: `tsup` → `dist/`
- Test: `vitest` — `yarn test:run` (CI) or `yarn test` (watch)
- Typecheck: `yarn lint` (= `tsc --noEmit`, *not* eslint)
- Bundle gate: `yarn size-limit`
- Demo: `examples/ecommerce-demo/` (Vite app on :3002)

## Layer rules

See `.cursor/rules/web-sdk.mdc` and `.cursor/rules/pulse-web-otel.mdc` for the canonical layer + naming conventions.
Highlights worth restating because they change agent behaviour:

- `platform = 'web'` on every signal. Not optional.
- `pulse.type` enum: `session.start | session.end | device.crash | non_fatal | http | app.click | web_vital | screen_load | screen_session`. Don't invent new values without an ADR.
- `src/instrumentations/<name>.ts` is the registration surface; never touch `io.opentelemetry.*` namespacing — that's upstream.
- Public API: only what `src/index.ts` exports. Consumers don't pin internal paths.

## Where things live

| Concern | Path |
|---|---|
| OTLP exporter wiring | `src/exporters.ts` |
| Sampling rules | `src/sampling/` |
| Instrumentation registry | `src/instrumentation-registry.ts` |
| Interaction (click/heatmap) | `src/instrumentations/interaction.ts` |
| Session lifecycle | `src/instrumentations/session.ts` |
| Remote config | `src/remote-config.ts` |
| Consent gate | `src/consent.ts` |
| Persistence (IDB/LS) | `src/persistence/` |
| Tests | `src/__tests__/` (co-located in some sub-dirs too) |

## Test patterns

- Vitest with `--run` for one-shot. Use file paths to scope: `yarn test:run src/instrumentations/interaction.test.ts`.
- Mock at the OTLP exporter boundary — never mock OTel SDK internals; they're upstream and the test will silently rot.
- Use `fake-indexeddb` for IDB persistence tests; never hit real IDB.
- Network spans: assert on `pulse.type === 'http'` and `http.method`/`http.status_code` semconv attributes, not custom names.

## Sharp edges (start short — agent appends here)

- **Don't read `localStorage` synchronously at module init.** Some host apps load us in a Web Worker context where `window.localStorage` is undefined. Use the persistence module which guards.
- **Screen navigation signals** (`screen_load`, `screen_session`) are emitted from [`src/instrumentations/navigation.ts`](src/instrumentations/navigation.ts) when `PulseFeature.screen_navigation` is enabled (see [`web-sdk-plan/v4-screen-signals/FINAL-PLAN.md`](web-sdk-plan/v4-screen-signals/FINAL-PLAN.md)). Initial load puts **`tti`** on **`screen_load`**; Web Vitals stay separate. Router hooks still call `Pulse.setScreenName` so `screen.name` / `last.screen.name` stay consistent on other telemetry.
- **Click heatmap is deferred.** `web-sdk-plan/interactions/INTERACTION-SCENARIO-MATRIX.md` has the full deferred matrix. Don't implement until the UI team picks it up.
- **Session-storage size limits.** `localStorage` quota = ~5MB on most browsers. The persistence module truncates oldest spans first; don't add unbounded queues.

## Recurring agent failures (start empty — agent appends here)

<!-- When Ralph fails the same way twice, append a one-liner here so future iters skip it. -->

## Do X here

| Task | Where to do it |
|---|---|
| Add a new instrumentation | `src/instrumentations/<name>.ts` + register in `src/instrumentation-registry.ts` + test in `src/__tests__/` |
| Add a new `pulse.type` value | Update `src/constants/` enum + add ADR under `web-sdk-plan/` first |
| Add an exporter integration | `src/integrations/` (per-framework adapter) |
| Tweak sampling | `src/sampling/` — keep deterministic across page loads |
| Add a feature gate | `src/feature-gate.ts` |

## Do NOT do here

- Don't add lodash, moment, or any heavy dep. Bundle size is gated by `size-limit`.
- Don't import from `@opentelemetry/sdk-node` — Node-only, breaks in browser builds.
- Don't write to `console.*` outside `src/pulse-web-logger.ts` — every log routes through the logger so consumers can silence us.
- Don't change `src/index.ts` exports without bumping the version. It's a public API surface.

## Reference docs

- `web-sdk-plan/WEB-SDK-AGENT-CONTEXT.md` — full data contract + phase plan
- `web-sdk-plan/v1/MILESTONES.md` — milestone exit criteria + verification queries
- `web-sdk-plan/interactions/INTERACTION-SCENARIO-MATRIX.md` — deferred interactions matrix
- `.cursor/rules/web-sdk.mdc` and `.cursor/rules/pulse-web-otel.mdc` — repo-level conventions

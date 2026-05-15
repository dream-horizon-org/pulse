# pulse-web-otel — Package context

Per-package operating manual. Auto-loaded by Claude Code (hierarchical CLAUDE.md) alongside the repo-root constitution.

## Fast facts

- Package: `@dreamhorizonorg/pulse-web` (`package.json` `name`), `0.1.0-alpha.2` (pre-release)
- Build: `tsup` → `dist/`
- Test: `vitest` — `yarn test:run` (CI) or `yarn test` (watch)
- Typecheck: `yarn lint` (= `tsc --noEmit`, *not* eslint)
- Bundle gate: `yarn size-limit`
- Demo: `examples/ecommerce-demo/` (Vite app on :3002)

## Layer rules

See **`.agents/rules/pulse-web-otel-contract.mdc`** and **`.agents/rules/pulse-web-otel-conventions.mdc`** (symlinked as `.cursor/rules/web-sdk.mdc` and `.cursor/rules/pulse-web-otel.mdc`) for contract vs conventions.
Highlights worth restating because they change agent behaviour:

- `platform = 'web'` on every signal. Not optional.
- `pulse.type` enum: see `src/semconv.ts` / **`docs/sdk-core/data-contract/SPEC.md`**. Don't invent new values without an ADR.
- `src/instrumentations/<name>.ts` is the registration surface; never touch `io.opentelemetry.*` namespacing — that's upstream.
- Public API: only what `src/index.ts` exports. Consumers don't pin internal paths.

## Where things live

| Concern | Path |
|---|---|
| OTLP exporter wiring | `src/exporters.ts` |
| Sampling rules | `src/sampling/` |
| Instrumentation registry | `src/instrumentation-registry.ts` |
| Interaction (sequences) | `src/instrumentations/interaction.ts` + `src/interactions/` |
| Session lifecycle | `src/instrumentations/session.ts` |
| Remote config | `src/remote-config.ts` |
| Consent gate | `src/consent.ts` |
| Persistence (IDB/LS) | `src/persistence/` |
| Tests | `src/__tests__/` (co-located in some sub-dirs too) |

## Test patterns

- After substantive SDK edits: **`yarn e2e:web-sdk-gates`** from this package (Playwright gate bundle: M1–M4, web vitals, clicks, network, screen navigation; Chromium). Requires demo deps installed.
- Vitest with `--run` for one-shot. Use file paths to scope: `yarn test:run src/instrumentations/interaction.test.ts`.
- Mock at the OTLP exporter boundary — never mock OTel SDK internals; they're upstream and the test will silently rot.
- Use `fake-indexeddb` for IDB persistence tests; never hit real IDB.
- Network client spans: assert `pulse.type` (`network.<status>` pattern), `http.request.method`, `http.response.status_code` — see **`docs/instrumentations/network/SPEC.md`**.

## Sharp edges (start short — agent appends here)

- **Don't read `localStorage` synchronously at module init.** Some host apps load us in a Web Worker context where `window.localStorage` is undefined. Use the persistence module which guards.
- **Screen navigation:** `screen_load` + `screen_session` OTLP **spans** from [`src/instrumentations/navigation.ts`](src/instrumentations/navigation.ts) when enabled. **Web does not** emit a separate `screen_interactive` span — **`tti`** lives on **`screen_load`**. See **`docs/instrumentations/screen-signals/SPEC.md`**. Router hooks call `Pulse.setScreenName` for `screen.name` on other telemetry.
- **Click heatmap is deferred.** See **`docs/instrumentations/interactions/SPEC.md` §7 / §9**.
- **Session-storage size limits.** `localStorage` quota = ~5MB on most browsers. The persistence module truncates oldest spans first; don't add unbounded queues.

## Recurring agent failures (start empty — agent appends here)

<!-- When Ralph fails the same way twice, append a one-liner here so future iters skip it. -->

## Do X here

| Task | Where to do it |
|---|---|
| Add a new instrumentation | `src/instrumentations/<name>.ts` + register in `src/instrumentation-registry.ts` + test in `src/__tests__/` |
| Add a new `pulse.type` value | Update `src/constants/` / semconv + ADR in `docs/` or repo planning |
| Add an exporter integration | `src/integrations/` (per-framework adapter) |
| Tweak sampling | `src/sampling/` — keep deterministic across page loads |
| Add a feature gate | `src/feature-gate.ts` |

## Do NOT do here

- Don't add lodash, moment, or any heavy dep. Bundle size is gated by `size-limit`.
- Don't import from `@opentelemetry/sdk-node` — Node-only, breaks in browser builds.
- Don't write to `console.*` outside `src/pulse-web-logger.ts` — every log routes through the logger so consumers can silence us.
- Don't change `src/index.ts` exports without bumping the version. It's a public API surface.

## Reference docs

- **`prd/README.md`** — where PRDs live; symlink `PRD.md` → `prd/<slug>.md` for Ralph (or `PRD_PATH`)
- **`docs/instrumentations/integration/SPEC.md`** — host-app integration entry (exports, init, framework pointers)
- **`docs/sdk-core/SPEC.md`** — sdk-core index (links to split topics)
- **`docs/sdk-core/data-contract/SPEC.md`** — `pulse.type` + shared attributes
- **`docs/instrumentations/`** — per-feature holy-grail SPECs (errors, network, …)
- `.agents/rules/` contract + conventions (same rules via `.cursor/rules/` symlinks)

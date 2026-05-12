# instrumentations/screen-signals

## 1. Purpose

The umbrella "screen telemetry" surface: `screen_load`, `screen_interactive`, and `screen_session`. Built on top of `NavigationInstrumentation` (spans + screen-name resolution) and the global-attrs processor (stamps `screen.name` on every signal so clicks, errors, network spans, and web-vital logs all join back to a screen).

## 2. Source location

- `pulse-web-otel/src/instrumentations/navigation.ts` — `screen_load` + `screen_session` emission, timing data, BFCache, debounce
- `pulse-web-otel/src/processors/global-attrs-processor.ts` — `resolveScreenNameFromUrl`, global `screen.name` injection
- `pulse-web-otel/src/integrations/react/PulseRouterEvents.tsx` and `next/PulseRouterEvents.tsx` — bridge framework router events into `pulse:route`

## 3. Public surface

No new export — composed from `NavigationInstrumentation` + framework integrations. Configurable via `instrumentations.navigation.{ enabled, screenNameMap }` and `setScreenName(name)` on the SDK facade (where exposed).

## 4. Internal design

Three signals:

| Signal | Kind | Emitted by | Trigger |
|---|---|---|---|
| `screen_load` | span | navigation.ts | initial document `load` (or BFCache `pageshow`) |
| `screen_interactive` | span | navigation.ts (TTI calc) | first long-task-free idle window after `screen_load` |
| `screen_session` | span | navigation.ts | one per logical screen; closed on next route or `pagehide` |

`screen.name` is resolved via:

1. Caller-supplied map from `instrumentations.navigation.screenNameMap`.
2. Framework hint (React Router `useRouterTracking`, Next.js `useNextAppRouterTracking` / `useNextPagesRouterTracking`).
3. URL heuristic in `resolveScreenNameFromUrl`.

Recent additions: BFCache restore path emits `screen_session` with `navigation.type = back_forward` without rotating the SDK session; the trailing-debounce window collapses History bursts to a single signal (see `screen-signals/SPEC.md` § R2a); SPA naming derives from the framework hooks first to avoid noisy URL fragments.

## 5. Dependencies

- `@opentelemetry/api` (spans)
- `session.ts` (for `screen_session` boundaries)
- Framework integrations under `src/integrations/`

## 6. Data contracts

`pulse.type ∈ { screen_load, screen_interactive, screen_session }`. Attribute keys: `screen.name`, `page.title`, `page.url`, `url.path`, `navigation.type`, `last.screen.name`. Span kind `INTERNAL`.

Cross-signal: `screen.name` is injected on every other signal by `global-attrs-processor.ts`, so a click can be joined to its screen even though clicks are logs not spans.

## 7. Tests

- `src/__tests__/screen-name-resolution.test.ts`
- `src/__tests__/pulse-router-events.test.tsx`
- `src/__tests__/use-next-pages-router-tracking.test.tsx`
- E2E: `examples/ecommerce-demo/e2e/screen-navigation.spec.ts`, `m16-ch.spec.ts`

## 8. History / decisions

Canonical SPEC: `pulse-web-otel/docs/instrumentations/screen-signals/SPEC.md`. BFCache + trailing-debounce + SPA-route naming were added in recent commits; see git log under `pulse-web-otel/src/instrumentations/navigation.ts`.

## 9. Rebuild recipe

1. Implement navigation per `instrumentations/navigation.md`.
2. In `global-attrs-processor.ts`, expose a setter that the navigation instrumentation calls on route change; the processor stamps the current value into every span/log.
3. Add framework hooks that emit `window.dispatchEvent(new CustomEvent("pulse:route", { detail: { screenName } }))`.
4. Add a fallback URL → screen-name heuristic and a caller-supplied override map.

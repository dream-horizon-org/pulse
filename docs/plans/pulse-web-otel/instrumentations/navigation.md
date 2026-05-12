# instrumentations/navigation

## 1. Purpose

Track SPA route changes and initial page loads, emitting `screen_load` and `screen_session` spans plus deriving the `screen.name` global attribute. Handles `pushState` / `replaceState` / `popstate`, BFCache restore, and debounces History bursts.

## 2. Source location

- `pulse-web-otel/src/instrumentations/navigation.ts` — `NavigationInstrumentation` (~668 lines)
- `pulse-web-otel/src/processors/global-attrs-processor.ts` — `resolveScreenNameFromUrl`

## 3. Public surface

```ts
class NavigationInstrumentation implements PulseInstrumentation {
  readonly name = PulseInstrumentationName.NAVIGATION; // "navigation"
  install(sdk: SdkContext): void;
  uninstall(): void;
}
```

Gated by `PulseFeature.SCREEN_NAVIGATION`.

## 4. Internal design

- On `install()`, monkey-patches `history.pushState` and `history.replaceState`; attaches `popstate`, `pagehide`, `pageshow`, and (if loading) a `load` listener.
- Span names are **fixed literals** (`screen_load`, `screen_session`) so ClickHouse queries can filter by `SpanName` instead of route strings; the route is on the `screen.name` attribute.
- Timing data: `pageLoadTime`, `ttfb`, `dnsTime`, `tcpTime`, `domProcessingTime`, `tti` are taken from PerformanceTiming.
- `NavigationTimingType` enum: `cold` | `reload` | `back_forward` (from Navigation Timing API on first load).
- BFCache: `pageshow.persisted === true` is treated as a soft restore — emits a fresh `screen_session` with `navigation.type = back_forward`, without rotating the SDK session.
- Debounce: `navigationRateLimitMs = 100` collapses History bursts; `routeTrailingTimer` resolves to the final URL after a quiet window.
- `enteredFromScreenName` is stamped as `last.screen.name` on exit/session spans.

## 5. Dependencies

- `@opentelemetry/api` (Span, SpanKind, SpanStatusCode)
- `processors/global-attrs-processor.ts` for screen-name resolution

## 6. Data contracts

`pulse.type ∈ { screen_load, screen_session }`. Attribute keys: `screen.name`, `page.title`, `page.url`, `url.path`, `navigation.type`. Span kind `INTERNAL`.

Also drives the `screen.name` global attribute injected on every span/log by the processor.

## 7. Tests

- `src/__tests__/screen-name-resolution.test.ts`
- `src/__tests__/pulse-router-events.test.tsx`
- E2E: `examples/ecommerce-demo/e2e/screen-navigation.spec.ts`, `m16-ch.spec.ts`

## 8. History / decisions

Canonical SPEC: `pulse-web-otel/docs/instrumentations/screen-signals/SPEC.md` (R2a: trailing debounce; BFCache handling). Recent commits added BFCache navigation, the trailing-debounce, and SPA-route naming. See also `instrumentations/screen-signals.md`.

## 9. Rebuild recipe

1. Patch `history.pushState` / `replaceState` to dispatch a synthetic `pulse:route` event.
2. Listen for `popstate` + the synthetic event; coalesce via the trailing-debounce timer.
3. On `load`, capture initial `NavigationTimingType` and `TimingData`, emit `screen_load`.
4. On every route change, close the previous `screen_session` span and start a new one.
5. On `pagehide`, close the active session span. On `pageshow.persisted`, open a fresh one with `navigation.type = back_forward`.

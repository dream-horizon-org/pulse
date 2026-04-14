# Module 2 — Auto-Instrumentations (Index)

**Goal:** Automatically capture errors, network requests, UI interactions, web vitals, page navigation, resource loading, connectivity changes, and long tasks — with zero app code required beyond `PulseWeb.start()`.

**Prerequisites:** Module 1 complete and verified (spans reaching ClickHouse).

---

## Sub-Documents

Each instrumentation has its own deep-dive doc with full attribute contract, implementation code, edge cases, and test cases.

| # | Doc | What It Captures | Signals Produced | Kind | Version |
|---|---|---|---|---|---|
| 02.1 | [Errors](./errors.md) | JS crashes, unhandled rejections, console errors | `device.crash`, `non_fatal` | Log | V1 |
| 02.2 | [Network](./network.md) | Fetch, XHR, GraphQL detection | `http` | Span | V1 |
| 02.3 | [Clicks](./clicks.md) | Clicks, rage clicks, dead clicks | `app.click` | Log | V1 |
| 02.4 | [Web Vitals](./web-vitals.md) | LCP, CLS, INP, TTFB, FCP + attribution | `web_vital` | **Metric** (OTLP Gauge) | V1 |
| 02.5 | [Navigation](./navigation.md) | Page load, SPA route changes, TTI | `screen_load`, `screen_interactive`, `screen_session` | Span | V1 |
| 02.6 | [Long Tasks](../../v2/01-instrumentations/long-tasks.md) | Main thread blocks > 50ms | `app.jank.slow` | Log | V2 |
| 02.7 | [Resource Timing](../../v2/01-instrumentations/resource-timing.md) | JS, CSS, image, font, API load times | `resource_load` | Span | V2 |
| 02.8 | [Visibility & Online](../../v2/01-instrumentations/visibility-online.md) | Tab hidden/visible, offline/online | `app.visibility`, `network.change` | Log | V2 |
| 02.9 | [WebSocket](../../v2/01-instrumentations/websocket.md) | WS open, close, error, message size | `websocket` | Span | V2 |
| 02.10 | [BFCache](../../v2/01-instrumentations/bfcache.md) | Back/forward cache restores | `screen_load` (bfcache variant) | Span | V2 |

---

## Signal → Instrumentation Map

Quick reference: which `pulse.type` comes from which doc.

| `pulse.type` | Kind | Doc |
|---|---|---|
| `device.crash` | Log | [errors.md](./errors.md) |
| `non_fatal` | Log | [errors.md](./errors.md) |
| `http` | Span | [network.md](./network.md) |
| `app.click` | Log | [clicks.md](./clicks.md) |
| `web_vital` | **Metric** (OTLP Gauge) | [web-vitals.md](./web-vitals.md) |
| `screen_load` | Span | [navigation.md](./navigation.md), [bfcache.md](../../v2/01-instrumentations/bfcache.md) |
| `screen_interactive` | Span | [navigation.md](./navigation.md) |
| `screen_session` | Span | [navigation.md](./navigation.md) |
| `app.jank.slow` | Log | [long-tasks.md](../../v2/01-instrumentations/long-tasks.md) |
| `resource_load` | Span | [resource-timing.md](../../v2/01-instrumentations/resource-timing.md) |
| `app.visibility` | Log | [visibility-online.md](../../v2/01-instrumentations/visibility-online.md) |
| `network.change` | Log | [visibility-online.md](../../v2/01-instrumentations/visibility-online.md) |
| `websocket` | Span | [websocket.md](../../v2/01-instrumentations/websocket.md) |

**13 signal types from 10 instrumentations.**

---

## Global Attributes on Every Signal

All signals from Module 2 automatically carry the global attributes set up in Module 1:

```
session.id, screen.name, url.path, page.url, page.title,
network.connection.type, network.effective_type,
browser.name, browser.version, os.name, os.version,
device.type, device.screen.width, device.screen.height,
service.name, rum.sdk.version, platform, project.id, installation.id
```

Individual docs only list signal-specific attributes on top of these.

---

## Instrumentation Config

All instrumentations are enabled by default. Each can be disabled:

```typescript
PulseWeb.start({
  instrumentations: {
    errors:          true,
    network:         true,
    clicks:          true,
    webVitals:       true,
    navigation:      true,
    longTasks:       true,
    resourceTiming:  true,
    visibility:      true,
    websocket:       true,
    bfcache:         true,
    consoleErrors:   false,  // opt-in only
  }
})
```

Remote config (from `/v1/configs/active`) can also gate each one server-side without an SDK update.

---

## Module 2 Done Criteria

All sub-doc done criteria must pass, plus:
- [ ] All 13 signal types visible in Pulse dashboard under `platform = 'web'`
- [ ] No signal emitted when `dataCollectionState: DENIED`
- [ ] All instrumentations disabled via config respected correctly
- [ ] No errors thrown in Firefox or Safari for Chromium-only APIs (`longtask`, `resourceTiming`)

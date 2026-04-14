# Phase 2 — Auto-Instrumentations (Index)

**Goal:** Automatically capture errors, network requests, UI interactions, web vitals, page navigation, resource loading, connectivity changes, and long tasks — with zero app code required beyond `PulseWeb.start()`.

**Estimated duration:** Week 3–4
**Prerequisites:** Phase 1 complete and verified (spans reaching ClickHouse).

---

## Sub-Documents

Each instrumentation has its own deep-dive doc with full attribute contract, implementation code, edge cases, and test cases.

| # | Doc | What It Captures | Signals Produced | Priority |
|---|---|---|---|---|
| 02.1 | [Errors](./02.1-errors.md) | JS crashes, unhandled rejections, console errors | `device.crash`, `non_fatal` | v1 |
| 02.2 | [Network](./02.2-network.md) | Fetch, XHR, GraphQL detection | `http` span | v1 |
| 02.3 | [Clicks](./02.3-clicks.md) | Clicks, rage clicks, dead clicks | `app.click` | v1 |
| 02.4 | [Web Vitals](./02.4-web-vitals.md) | LCP, CLS, FID, INP, TTFB, FCP + attribution | `web_vital` metric | v1 |
| 02.5 | [Navigation](./02.5-navigation.md) | Page load, SPA route changes, TTI | `screen_load`, `screen_interactive`, `screen_session` | v1 |
| 02.6 | [Long Tasks](./02.6-long-tasks.md) | Main thread blocks > 50ms | `app.jank.slow` | v1 |
| 02.7 | [Resource Timing](./02.7-resource-timing.md) | JS, CSS, image, font, API load times | `resource_load` | v1 |
| 02.8 | [Visibility & Online](./02.8-visibility-online.md) | Tab hidden/visible, offline/online | `app.visibility`, `network.change` | v1 |
| 02.9 | [WebSocket](./02.9-websocket.md) | WS open, close, error, message size | `websocket` | v1 |
| 02.10 | [BFCache](./02.10-bfcache.md) | Back/forward cache restores | `screen_load` (bfcache variant) | v1 |

---

## Signal → Instrumentation Map

Quick reference: which `pulse.type` comes from which doc.

| `pulse.type` | Kind | Doc |
|---|---|---|
| `device.crash` | Log | 02.1 |
| `non_fatal` | Log | 02.1 |
| `http` | Span | 02.2 |
| `app.click` | Log | 02.3 |
| `web_vital` | Metric | 02.4 |
| `screen_load` | Span | 02.5, 02.10 |
| `screen_interactive` | Span | 02.5 |
| `screen_session` | Span | 02.5 |
| `app.jank.slow` | Log | 02.6 |
| `resource_load` | Span | 02.7 |
| `app.visibility` | Log | 02.8 |
| `network.change` | Log | 02.8 |
| `websocket` | Span | 02.9 |

**13 signal types from 10 instrumentations.**

---

## Global Attributes on Every Signal

All signals from Phase 2 automatically carry the global attributes set up in Phase 1:

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

## Phase 2 Done Criteria

All sub-doc done criteria must pass, plus:
- [ ] All 13 signal types visible in Pulse dashboard under `platform = 'web'`
- [ ] No signal emitted when `dataCollectionState: DENIED`
- [ ] All instrumentations disabled via config respected correctly
- [ ] No errors thrown in Firefox or Safari for Chromium-only APIs (`longtask`, `resourceTiming`)

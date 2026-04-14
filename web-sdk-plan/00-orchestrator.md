# Pulse Web SDK — Orchestrator

This document is the single source of truth for the Web SDK project. It tracks phase status, dependencies, decisions, and risks. Update it as the project progresses.

---

## Phase Status

| # | Phase | Index Doc | Sub-Docs | Status | Owner | Blocker |
|---|---|---|---|---|---|---|
| 1 | Foundation | [01-foundation.md](./01-foundation.md) | [01.1](./01.1-session-instrumentation.md) | Not Started | — | — |
| 2 | Framework Integrations | [05-framework-integrations.md](./05-framework-integrations.md) | [05.1](#) · [05.2](#) · [05.3](#) · [05.4](#) | Not Started | — | Phase 1 |
| 3 | Auto-Instrumentations | [02-auto-instrumentations.md](./02-auto-instrumentations.md) | [02.1](#) · [02.2](#) · [02.3](#) · [02.4](#) · [02.5](#) · [02.6](#) · [02.7](#) · [02.8](#) · [02.9](#) · [02.10](#) | Not Started | — | Phase 1 |
| 3.5 | Interactions | [03-interactions.md](./03-interactions.md) | [03.1](#) · [03.2](#) · [03.3](#) | Not Started | — | Phase 1 |
| 4 | Session Replay | [04-session-replay.md](./04-session-replay.md) | [04.1](#) · [04.2](#) · [04.3](#) | Not Started | — | Phase 1 |
| 5 | SDK Config (Remote Config) | [01.5-sdk-config.md](./01.5-sdk-config.md) | — | Not Started | — | Phase 3 |
| 6 | Build & Distribution | [06-build-distribution.md](./06-build-distribution.md) | — | Not Started | — | Phases 2–4 |
| 7 | Testing & Quality | [07-testing-quality.md](./07-testing-quality.md) | — | Not Started | — | Phase 3 |
| 8 | Backend & UI | [08-backend-ui.md](./08-backend-ui.md) | [08.1](#) · [08.2](#) | Not Started | — | Phase 3 |

**Status values:** `Not Started` → `In Progress` → `In Review` → `Done` → `Blocked`

---

## Sub-Doc Index

### Phase 2 — Auto-Instrumentations

| Doc | File | Signal Type | Implementation Complexity |
|---|---|---|---|
| [02.1](./02.1-errors.md) | `src/instrumentations/errors.ts` | `device.crash`, `non_fatal` | Low |
| [02.2](./02.2-network.md) | `src/instrumentations/network.ts` | `http` span | Medium |
| [02.3](./02.3-clicks.md) | `src/instrumentations/clicks.ts` | `app.click` | Medium |
| [02.4](./02.4-web-vitals.md) | `src/instrumentations/web-vitals.ts` | `web_vital` metric | Low |
| [02.5](./02.5-navigation.md) | `src/instrumentations/navigation.ts` | `screen_load`, `screen_interactive`, `screen_session` | Medium |
| [02.6](./02.6-long-tasks.md) | `src/instrumentations/long-tasks.ts` | `app.jank.slow` | Low |
| [02.7](./02.7-resource-timing.md) | `src/instrumentations/resource-timing.ts` | `resource_load` | Low |
| [02.8](./02.8-visibility-online.md) | `src/instrumentations/visibility-online.ts` | `app.visibility`, `network.change` | Low |
| [02.9](./02.9-websocket.md) | `src/instrumentations/websocket.ts` | `websocket` | Medium |
| [02.10](./02.10-bfcache.md) | `src/instrumentations/bfcache.ts` | `bfcache.restore` | Low |

### Phase 2.5 — Interactions

| Doc | File | Responsibility |
|---|---|---|
| [03.1](./03.1-interaction-config.md) | `src/interactions/config-fetcher.ts` | CDN config fetch + cache |
| [03.2](./03.2-interaction-matching.md) | `src/interactions/interaction-matcher.ts` | State machine, step matching |
| [03.3](./03.3-interaction-span.md) | `src/interactions/interaction-span.ts` | APDEX scoring, span output |

### Phase 3 — Session Replay

| Doc | File | Responsibility |
|---|---|---|
| [04.1](./04.1-replay-recorder.md) | `src/replay/recorder.ts` | rrweb setup, event buffering |
| [04.2](./04.2-replay-privacy.md) | `src/replay/privacy.ts` | Input masking, CSS blocking |
| [04.3](./04.3-replay-transport.md) | `src/replay/transport.ts` | gzip compression, OTLP delivery |

### Phase 7 — Backend & UI

| Doc | File | Responsibility |
|---|---|---|
| [08.1](./08.1-sdk-config-web-support.md) | backend + pulse-ui | Extend SDK Config to support `pulse_web_js` — enums, DTOs, default template, UI components |
| [08.2](./08.2-ui-web-support.md) | pulse-ui | All UI changes for web SDK data — attribute mapping, Web Vitals screen, rrweb player, click heatmap |

### Phase 4 — Framework Integrations

| Doc | File | Framework |
|---|---|---|
| [05.1](./05.1-react.md) | `src/integrations/react/` | React + React Router v6 |
| [05.2](./05.2-nextjs.md) | `src/integrations/nextjs/` | Next.js App + Pages Router |
| [05.3](./05.3-vue.md) | `src/integrations/vue/` | Vue 3 + Vue Router + Nuxt 3 |
| [05.4](./05.4-cdn-vanilla.md) | `src/integrations/cdn/` | Async CDN snippet, Vanilla JS |

---

## Phase Dependency Map

```
Phase 1 (Foundation — incl. batching, persistence, compression, shutdown, session instrumentation)
  ├─→ Phase 2 (Framework Integrations)        ← run early; real app context for testing
  ├─→ Phase 3 (Auto-Instrumentations)         ┐
  ├─→ Phase 3.5 (Interactions)               ├─ parallel once Phase 1 done
  └─→ Phase 4 (Session Replay)               ┘
        └─→ Phase 5 (SDK Config / Remote Config)  ← after instrumentations are stable
              ├─→ Phase 6 (Build & Distribution)
              ├─→ Phase 7 (Testing & Quality)
              └─→ Phase 8 (Backend & UI)
```

Phases 2, 3, 3.5, and 4 run in parallel once Phase 1 is complete.
Phase 5 (SDK Config) starts after Phase 3 is stable — you need real signals flowing before remote config gating makes sense.
Phase 6 requires all of 2–4 stable.

---

## What Each Phase Delivers

| Phase | Deliverable | Verifiable by |
|---|---|---|
| 1 | SDK initialises, a span appears in Pulse dashboard | Engineer checks ClickHouse |
| 2 | Errors, network, clicks, vitals visible in dashboard | QA smoke test |
| 2.5 | Interaction span with APDEX visible in Interactions tab | PM review in UI |
| 3 | Session replay visible in Pulse UI (web platform) | QA playback test |
| 4 | React / Next.js / Vue example apps working end-to-end | Engineer demo |
| 5 | `npm install @dreamhorizon/pulse-web` works; CDN URL live | Anyone can install |
| 6 | CI green; BrowserStack passing; bundle size < 30 KB | CI dashboard |
| 7 | Web data visible in all existing Pulse dashboards | PM review |

---

## Key Decisions Log

| Date | Decision | Rationale | Decided By |
|---|---|---|---|
| — | Session Replay is opt-in (not default) | rrweb adds ~50 KB; keep default bundle lean | — |
| — | npm-first, CDN as secondary | Modern apps need npm; CDN for legacy/snippet use | — |
| — | Same monorepo as Android/iOS SDK | Consistency; shared semconv, CI infra | — |
| — | Start at `0.1.0-alpha`, not `1.0.0` | Need freedom for breaking API changes before GA | — |
| — | Interactions in v1 (not deferred) | High value for web funnels; pure TS, no extra deps | — |

*Add new decisions as they're made.*

---

## Risk Register

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| rrweb bundle too large for customers | Medium | High | Make session replay a separate optional import |
| CORS issues sending OTLP from browser | Medium | High | Backend must allow CORS from customer domains; test early in Phase 1 |
| `sendBeacon` data loss on slow networks | Low | Medium | Test with throttled network in Playwright suite |
| React Native Web + Web SDK double-init | Medium | Medium | Guard with `typeof window !== 'undefined'` + singleton check |
| CDN cache staleness after deploy | Low | High | Use immutable versioned URLs; short TTL on `@latest` alias |
| Interaction config CDN cold start latency | Low | Medium | Fetch in parallel with SDK init; don't block instrumentation start |

---

## Open Questions

- [ ] Do we support SSR (server-side rendering) data capture, or browser-only?
- [ ] Should the Web SDK share the same API key format as mobile, or get a separate web-specific key type?
- [ ] What is the session replay sampling default — 100% or lower?
- [ ] Should `PulseWeb.start()` be async or sync (currently planned as sync with async export)?

---

## Changelog

| Date | Change | Author |
|---|---|---|
| 2026-04-13 | Initial plan created | — |
| 2026-04-13 | Added granular sub-docs for all phases (02.1–02.10, 03.1–03.3, 04.1–04.3, 05.1–05.4) | — |
| 2026-04-13 | Added 01.5-sdk-config.md (remote config, sampling, feature gates) | — |
| 2026-04-13 | Updated 02.5-navigation.md with screen.name resolution chain and route pattern normalization | — |
| 2026-04-13 | Added 08.1-sdk-config-web-support.md (backend + UI changes to support pulse_web_js) | — |
| 2026-04-13 | Added 08.2-ui-web-support.md (full UI gap analysis and plan for web SDK data) | — |


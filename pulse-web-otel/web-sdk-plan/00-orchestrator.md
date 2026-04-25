# Pulse Web SDK — Orchestrator

This document is the single source of truth for the Web SDK project. It tracks phase status, dependencies, decisions, and risks. Update it as the project progresses.

---

## Phase Status

| # | Phase | Index Doc | Sub-Docs | Status | Owner | Blocker |
|---|---|---|---|---|---|---|
| 1 | Foundation | [01-foundation/index.md](./v1/01-foundation/index.md) | [session](./v1/01-foundation/session.md) · [sdk-config](./v1/01-foundation/sdk-config.md) | Not Started | — | — |
| 2 | Framework Integrations | [05-frameworks/index.md](./v1/04-frameworks/index.md) | [react](./v1/04-frameworks/react.md) · [nextjs](./v1/04-frameworks/nextjs.md) · [vue](./v2/03-frameworks/vue.md) · [cdn](./v1/04-frameworks/cdn-vanilla.md) | Not Started | — | Phase 1 |
| 3 | Auto-Instrumentations | [02-instrumentations/index.md](./v1/02-instrumentations/index.md) | [errors](./v1/02-instrumentations/errors.md) · [network](./v1/02-instrumentations/network.md) · [clicks](./v1/02-instrumentations/clicks.md) · [web-vitals](./v1/02-instrumentations/web-vitals.md) · [navigation](./v1/02-instrumentations/navigation.md) · [long-tasks](./v2/01-instrumentations/long-tasks.md) · [resource-timing](./v2/01-instrumentations/resource-timing.md) · [visibility](./v2/01-instrumentations/visibility-online.md) · [websocket](./v2/01-instrumentations/websocket.md) · [bfcache](./v2/01-instrumentations/bfcache.md) | Not Started | — | Phase 1 |
| 3.5 | Interactions | [03-interactions/index.md](./v1/03-interactions/index.md) | [config](./v1/03-interactions/config.md) · [matching](./v1/03-interactions/matching.md) · [span](./v1/03-interactions/span.md) | Not Started | — | Phase 1 |
| 4 | Session Replay | [04-session-replay/index.md](./v2/02-session-replay/index.md) | [recorder](./v2/02-session-replay/recorder.md) · [privacy](./v2/02-session-replay/privacy.md) · [transport](./v2/02-session-replay/transport.md) | Not Started | — | Phase 1 |
| 5 | SDK Config (Remote Config) | [01-foundation/sdk-config.md](./v1/01-foundation/sdk-config.md) | — | Not Started | — | Phase 3 |
| 6 | Build & Distribution | [06-build-distribution/index.md](./v1/05-build-distribution/index.md) | — | Not Started | — | Phases 2–4 |
| 7 | Testing & Quality | [07-testing/index.md](./v2/05-testing/index.md) | — | Not Started | — | Phase 3 |
| 8 | Backend & UI | [08-backend-ui/index.md](./v2/04-backend-ui/index.md) | [sdk-config-support](./v2/04-backend-ui/sdk-config-support.md) · [ui-support](./v2/04-backend-ui/ui-support.md) | Not Started | — | Phase 3 |

**Status values:** `Not Started` → `In Progress` → `In Review` → `Done` → `Blocked`

---

## Sub-Doc Index

### Phase 2 — Auto-Instrumentations

| Doc | File | Signal Type | Version | Complexity |
|---|---|---|---|---|
| [errors](./v1/02-instrumentations/errors.md) | `src/instrumentations/errors.ts` | `device.crash`, `non_fatal` | V1 | Low |
| [network](./v1/02-instrumentations/network.md) | `src/instrumentations/network.ts` | `http` span | V1 | Medium |
| [clicks](./v1/02-instrumentations/clicks.md) | `src/instrumentations/clicks.ts` | `app.click` | V1 | Medium |
| [web-vitals](./v1/02-instrumentations/web-vitals.md) | `src/instrumentations/web-vitals.ts` | `web_vital` metric | V1 | Low |
| [navigation](./v1/02-instrumentations/navigation.md) | `src/instrumentations/navigation.ts` | `screen_load`, `screen_interactive`, `screen_session` | V1 | Medium |
| [long-tasks](./v2/01-instrumentations/long-tasks.md) | `src/instrumentations/long-tasks.ts` | `app.jank.slow` | V2 | Low |
| [resource-timing](./v2/01-instrumentations/resource-timing.md) | `src/instrumentations/resource-timing.ts` | `resource_load` | V2 | Low |
| [visibility-online](./v2/01-instrumentations/visibility-online.md) | `src/instrumentations/visibility-online.ts` | `app.visibility`, `network.change` | V2 | Low |
| [websocket](./v2/01-instrumentations/websocket.md) | `src/instrumentations/websocket.ts` | `websocket` | V2 | Medium |
| [bfcache](./v2/01-instrumentations/bfcache.md) | `src/instrumentations/bfcache.ts` | `bfcache.restore` | V2 | Low |

### Phase 2.5 — Interactions

| Doc | File | Responsibility |
|---|---|---|
| [config](./v1/03-interactions/config.md) | `src/interactions/config-fetcher.ts` | CDN config fetch + cache |
| [matching](./v1/03-interactions/matching.md) | `src/interactions/interaction-matcher.ts` | State machine, step matching |
| [span](./v1/03-interactions/span.md) | `src/interactions/interaction-span.ts` | APDEX scoring, span output |

### Phase 3 — Session Replay

| Doc | File | Responsibility |
|---|---|---|
| [recorder](./v2/02-session-replay/recorder.md) | `src/replay/recorder.ts` | rrweb setup, event buffering |
| [privacy](./v2/02-session-replay/privacy.md) | `src/replay/privacy.ts` | Input masking, CSS blocking |
| [transport](./v2/02-session-replay/transport.md) | `src/replay/transport.ts` | gzip compression, OTLP delivery |

### Phase 7 — Backend & UI

| Doc | File | Responsibility |
|---|---|---|
| [sdk-config-support](./v2/04-backend-ui/sdk-config-support.md) | backend + pulse-ui | Extend SDK Config to support `pulse_web_js` — enums, DTOs, default template, UI components |
| [ui-support](./v2/04-backend-ui/ui-support.md) | pulse-ui | All UI changes for web SDK data — attribute mapping, Web Vitals screen, rrweb player, click heatmap |

### Phase 4 — Framework Integrations

| Doc | File | Framework | Version |
|---|---|---|---|
| [react](./v1/04-frameworks/react.md) | `src/integrations/react/` | React + React Router v6 | V1 |
| [nextjs](./v1/04-frameworks/nextjs.md) | `src/integrations/nextjs/` | Next.js App + Pages Router | V1 |
| [cdn-vanilla](./v1/04-frameworks/cdn-vanilla.md) | `src/integrations/cdn/` | Async CDN snippet, Vanilla JS | V1 |
| [vue](./v2/03-frameworks/vue.md) | `src/integrations/vue/` | Vue 3 + Vue Router + Nuxt 3 | V2 |

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


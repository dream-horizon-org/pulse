# Phase 4 — Framework Integrations (Index)

**Goal:** Idiomatic one-liner integrations for React, Next.js, Vue 3, and CDN/Vanilla JS. Automatic route tracking in each framework with no manual event wiring.

**Estimated duration:** Week 8
**Prerequisites:** Phase 1 complete. Phase 2 navigation instrumentation recommended.

---

## Sub-Documents

| # | Doc | What It Covers |
|---|---|---|
| 05.1 | [React](./react.md) | `PulseProvider`, `PulseErrorBoundary`, React Router v6 hook |
| 05.2 | [Next.js](./nextjs.md) | App Router + Pages Router providers, SSR guard |
| 05.3 | [Vue *(V2)*](../../v2/03-frameworks/vue.md) | `PulseVuePlugin`, vue-router integration, global error handler — **ships in V2** |
| 05.4 | [CDN & Vanilla JS](./cdn-vanilla.md) | Async snippet loader, `window.PulseWeb` global, queue drain |

---

## Package Export Map

```json
{
  ".":        "@dreamhorizon/pulse-web",
  "./react":  "@dreamhorizon/pulse-web/react",
  "./nextjs": "@dreamhorizon/pulse-web/nextjs",
  "./vue":    "@dreamhorizon/pulse-web/vue"
}
```

Each integration is a separate entry point — unused framework code is fully tree-shaken.

---

## Phase 4 Done Criteria

All sub-doc criteria must pass, plus:
- [ ] Each framework integration has a working example app under `examples/`
- [ ] SDK initialises exactly once across all frameworks (singleton guard)
- [ ] Route changes tracked automatically in every framework integration
- [ ] No SSR errors in Next.js (no `localStorage is not defined`)

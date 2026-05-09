# Grill Session Notes — Screen Navigation Signals

**Date:** 2026-05-09  
**Participants:** Jatin (user), Claude (grill agent)  
**Session outcome:** Design locked ✅

---

## Q1: Why split `screen_load` + `screen_interactive`?

**Question:** Both spans come from the same PerformanceNavigationTiming entry on initial page load. Both start at 0. Why not merge them?

**Answer:**
- **Different attributes** — screen_load has full timing (ttfb, dns, tcp, dom); screen_interactive only has tti
- **Android parity** — Android emits both separately
- **Analytics granularity** — queries can filter by pulse.type independently
  - "What % reached interactive?" (screen_interactive)
  - "Median page load time?" (screen_load)
  - "Gap between interactive and fully loaded?"
- **No cost** — same export batch

**Decision:** ✅ Keep both spans. Android parity + analytics flexibility.

---

## Q2: Do we emit `screen_interactive` on SPA nav?

**Question:** When user navigates in SPA (React Router), do we emit `screen_interactive` for the new screen?

### Initial thinking
- v1 design only mentions it for initial page load
- No PerformanceNavigationTiming on SPA nav
- Would be misleading (fake 0ms TTI)

### Industry research
Searched: PostHog, Sentry, GA4 approaches to SPA nav metrics.

**Key findings:**
- **PostHog:** single `$pageview` event per nav (no TTI separate)
- **Sentry:** transaction per nav, but **no separate TTI milestone** (uses idling timeout for transaction end)
- **GA4:** `page_view` + separate `web_vitals` (LCP, INP, CLS), but **no TTI on SPA**
- **W3C Soft Navigation spec:** experimental, not standard yet

**Why no TTI on SPA:**
- React rendering is synchronous (DOM updates instantly)
- No browser API milestone (PerformanceNavigationTiming doesn't exist for SPA)
- "Interactive" happens instantly, not a meaningful milestone

**Decision:** ✅ **Skip `screen_interactive` on SPA nav.** Only emit on initial page load.

---

## Q3: User request — separate web vitals signal

**Question:** "I want web vitals (LCP, INP, CLS) emitted separately for both initial load and SPA nav, like GA4."

**Our response:** ✅ Agreed. Will emit as separate signal (deferred to Phase 2, architecture foundation in Phase 1).

**Rationale:**
- GA4 model (industry standard)
- Decouples nav tracking from performance metrics
- Allows per-page web vitals independently
- Future feature gating flexibility

---

## Q4: User feedback — PLAN-A unnecessary

**User:** "Why do we need PLAN-A (rejected alternative)? The lifecycle skill is not optimized for this."

**Response:** Fair point. PLAN-A (metrics approach) was clearly inferior once research showed span semantics. We included it per lifecycle guidelines, but you're right it wasn't strictly necessary once research was done.

**Lesson:** For future plans, if research clearly favors one approach, skip alternatives doc if no real tradeoff.

**Decision:** ✅ Noted for future. PLAN-A included for completeness; won't require changes.

---

## Q5: User request — document the grill discussion

**Question:** "The above discussion should be documented properly later."

**Response:** ✅ Creating this doc (GRILL-SESSION-NOTES.md) + FINAL-PLAN.md consolidates all decisions.

Will also update ADR with explicit section: "Grill findings: screen_interactive on SPA nav rejected (no industry standard, sync rendering)."

---

## Q6: Lifecycle & flush strategy

**Not explicitly asked, but critical:**

Initial page load → `screen_load` + `screen_interactive` → OTLP batch → flush on `visibilitychange` hidden or `pagehide`

SPA nav → `screen_session` (old screen) + `screen_load` (new, SPA variant) → same batch → flush on next visibility/pagehide

**Key:** Use `sdk.loggerProvider?.forceFlush()` (OTel SDK owns pipeline), not `Logger.emit()` alone.

---

## Q7: `screen_session` timing

**Implementation detail:**

`screen_session` emitted **when user navigates away**, not when they land on screen.

```
User on /home for 5 seconds
  ├─ screen_load + screen_interactive emitted (on entry)
  ├─ [5 seconds pass]
  └─ User navigates to /products
     ├─ screen_session emitted (for /home, duration=5000ms) ← HERE
     └─ screen_load emitted (for /products)
```

**Why?** Pulse custom metric. Time on screen is measured on exit (full visit known).

---

## Q8: Feature gate + consent

**Implementation:**

- `PulseFeature.SCREEN_NAVIGATION` gates installation (backend controls)
- If gate OFF → instrumentation not installed (zero listeners)
- If installed then consent OFF → no exports (Logger queues but SDK won't flush)
- E2E: gate-off test seeds config disabled → zero exports

---

## Q9: SSR safety

**Check:** Will this break server-side rendering (Next.js, Remix)?

**Answer:** ✅ Safe. First line of `install()`: `if (typeof window === "undefined") return;`

---

## Q10: Double-install guard

**Check:** What prevents `installAll()` being called twice?

**Answer:** `InstrumentationRegistry` has private `installAllCompleted` flag.
- First call: sets flag to true, installs all
- Second call: returns early (flag already true)
- `uninstallAll()` resets flag + uninstalls

---

## Q11: Screen name resolution

**Confirmed:** 4-step fallback chain (manual > pattern > heuristic > pathname) is correct and documented.

---

## Q12: Android parity checklist

Reviewed all parity points:
- ✅ Signal types (screen_load, screen_interactive, screen_session)
- ✅ screen.name resolution
- ✅ Global attribute propagation (PulseGlobalAttributesProcessor)
- ✅ Span type (SpanKind.INTERNAL, root)
- ✅ Start type (cold/reload/back_forward/spa)
- ✅ Web bonus fields (timing data from Navigation API)

All locked.

---

## Q13: Deferred items

Explicit list for future implementation:

1. **Web vitals per screen** (Phase 2) — LCP/INP/CLS in screen_load + UI
2. **BFCache optimization** (Phase 3+) — detected but no special handling v1
3. **Hash routes** (Phase 3+) — opt-in config
4. **Remote sampling** (Phase 3+) — per-screen rates
5. **Soft Navigation API** (Phase 3+) — wait for W3C standardization

---

## Final decisions summary

| Topic | Decision | Confidence |
|---|---|---|
| Span type (not metrics) | ✅ Spans | High |
| Split screen_load + screen_interactive | ✅ Yes, initial load only | High |
| screen_interactive on SPA nav | ✅ NO (skip) | High |
| Web vitals separate signal | ✅ Yes, GA4 model | High |
| Android parity | ✅ Maintained | High |
| Lifecycle & flush strategy | ✅ Locked | High |
| Feature gate + consent | ✅ Standard Pulse flow | High |
| Cross-package scope | ✅ SDK + backend + UI | High |

---

## Questions resolved

- ✅ Why split spans? (Attributes + analytics + parity)
- ✅ TTI on SPA? (No industry standard; React sync rendering)
- ✅ Web vitals? (Separate signal, GA4 model)
- ✅ SSR safe? (Yes, typeof window check)
- ✅ Double-install? (Registry flag guard)
- ✅ Flush strategy? (loggerProvider.forceFlush on visibility/pagehide)
- ✅ All Android parity items? (Locked)

---

## No blockers found

All grill questions resolved. Design is coherent:
- Android alignment ✅
- Industry patterns ✅
- OTel compliance ✅
- Pulse requirements ✅
- Cross-package scope ✅
- Deferred items explicit ✅

---

**Ready for:** PRD → issues → implementation

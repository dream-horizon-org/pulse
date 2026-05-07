# 02.3 — Web Vitals instrumentation

**Goal:** Emit Core Web Vitals (LCP, INP, CLS, FCP, TTFB, etc.) as logs/events per Pulse `pulse.type` / attribute conventions.

**File:** `src/instrumentations/web-vitals.ts`

**Android equivalent:** Not a 1:1 surface (mobile uses startup/rendering signals); alignment notes sit in the web vitals program docs below.

---

## Plans and parity

Design, PLAN-A/B/C, touchpoints, and contract parity: **[`../../v2-web-vitals/README.md`](../../v2-web-vitals/README.md)** — authoritative deep dive; this file is the v1 milestone stub linked from [`../MILESTONES.md`](../MILESTONES.md).

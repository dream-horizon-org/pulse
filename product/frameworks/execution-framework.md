---
title: Pulse — Execution Framework
status: live
layer: framework
last-edited: 2026-05-08
owner: Pulse Product Team
hero: gradient
---

# Pulse — Execution Framework

This is the canonical product framework for Pulse. Team leads should reference it when scoping new work, writing PRDs, or aligning on direction. The document is content-only and copy-pasteable into any external surface (decks, Notion, Google Docs).

## Table of Contents

1. North Star
2. Personas
3. Execution Framework
4. Progress Matrix — Layers × Personas
5. Metrics & Glossary
6. Future Stages

---

## 1. North Star

> **Resolved Revenue Leakage (RRL) per Customer per Month.**

A green infrastructure dashboard means nothing if checkout silently drops 5%. Pulse exists to surface and close that gap. We measure ourselves not on alerts fired or dashboards viewed — only on revenue we helped a customer save.

**Leading indicators that predict RRL:**

| Metric | What it predicts |
|---|---|
| TTFA — Time to First Anomaly Surfaced | Speed from SDK install to first "aha" moment |
| MTTD-R — Mean Time to Detect Revenue Regression | How early we catch leakage before it compounds |
| Signal Quality — genuine signal vs noise | Customer trust in our alerts |

---

## 2. Personas

Three roles own the revenue stack inside every digital product company. A gap in any one = silent revenue leakage.

| Persona | Owns Revenue Via | Top Failure Mode |
|---|---|---|
| **Tech** | Reliability — a crashed app converts 0% | Crashes, ANRs, latency, error rates |
| **Product** | Conversion + Retention | Funnel drop-off, completion-rate regression |
| **UX** | Experience quality | Rage taps, dead zones, layout shift, friction |

Every PRD must explicitly name the persona(s) it serves.

---

## 3. Execution Framework

Five sequential stages. Each stage shrinks the leakage that escapes the prior one.

**Detect → Diagnose → Quantify → Resolve → Predict**

| Stage | Question Answered | Unlock |
|---|---|---|
| **Detect** | Are we leaking? | Investigation becomes possible |
| **Diagnose** | What broke, where, for whom, why? | Investigation becomes actionable |
| **Quantify** | What is it costing in ₹? | Leakage can be prioritised |
| **Resolve** | Can we close it? | Leakage is resolved |
| **Predict** | Can we prevent the next one? | Pre-emptive savings |

Every PRD must explicitly name the layer(s) it operates in. Work that spans multiple layers should call out the dependencies between them.

---

## 4. Progress Matrix — Layers × Personas

Legend: ✅ Built · ⚡ WIP · ❌ Not built

Cells list the tools or solutions delivered for that layer × persona.

| Stage | Tech | Product | UX |
|---|---|---|---|
| **Detect** | ✅ App Vitals · Network metrics · Critical Interactions · Crash & ANR detection | ✅ Funnels · Journeys · Completion-rate alerts | ✅ Heatmaps · Session Replay · Apdex · Rage-tap detection |
| **Diagnose** | ✅ Interaction RCA agent · Screen RCA agent · Release-regression RCA | ⚡ Funnel & journey diagnose agent (in build) | ⚡ Heatmap + Session Replay diagnose agent (in build) |
| **Quantify** | ⚡ Anomaly → impacted users → ₹ correlation (in build) | ⚡ Funnel ₹-at-risk attribution (in build) | ❌ UX-driven ₹ attribution model |
| **Resolve** | ❌ Automated PRs against client source | ❌ Behavioral nudges | ❌ UX recommendations engine |
| **Predict** | ❌ — | ❌ — | ❌ — |

When proposing new work, locate it in this matrix first. Filling a ❌ cell or upgrading a ⚡ cell to ✅ is the highest-leverage form of work.

---

## 5. Metrics & Glossary

Every metric Pulse cares about is defined here. Acronyms used anywhere in the product folder resolve to this table.

| Metric | Threshold | Layer |
|---|---|---|
| **TTFE** — Time to First Event (SDK install → first event arrives in Pulse) | < 1 day | Integrate |
| **TTFA** — Time to First Anomaly (SDK install → first meaningful anomaly surfaced) | < 14 days | Detect |
| **MTTD-R** — Mean Time to Detect Revenue regression (regression onset → alert fires) | < 1 hour | Detect |
| Anomaly → investigation rate (% of surfaced anomalies users actually investigate) | > 50% | Diagnose |
| **MTTDx** — Mean Time to Diagnose (anomaly surfaced → root cause confirmed) | < 10 minutes | Diagnose |
| % anomalies with ₹ estimate attached | 100% | Quantify |
| PulseAI answer correctness (% of AI answers verified correct by user) | > 80% | Diagnose + Quantify |
| **NRR** — Net Revenue Retention (revenue retained from existing customers, net of churn and expansion) | > 110% | Outcome |
| **RRL** — Resolved Revenue Leakage (₹ of customer revenue Pulse helped protect, per customer per month) | North Star | Outcome of all layers |

PRD success metrics should reuse these where possible rather than inventing new ones.

---

## 6. Future Stages

The framework's last two stages are not yet in active development. They define our long-term direction.

| Phase | Delivers | Why It Matters |
|---|---|---|
| **Resolve** | Automated PRs for known-fix patterns · runtime nudges · UX recommendations engine | Pulse moves from "tells you" to "fixes it" |
| **Predict** | ML over accumulated detect + diagnose + quantify data → pre-emptive recommendations | The moat — only possible once a cross-customer corpus exists |

When work in Detect / Diagnose / Quantify generates data or interfaces that Resolve or Predict will need, call that out in the PRD so the foundations get laid early.

---
title: [Feature Name] — PRD
status: draft
layer: detect | diagnose | quantify | resolve | predict
persona: tech | product | ux
last-edited: [YYYY-MM-DD]
owner: [Name]
tracker: [Linear/Jira URL]
hero: flat
---

# [Feature Name] — PRD

> Replace bracketed placeholders. Delete sections that don't apply, but keep their headings as-is so the structure stays predictable. Keep the front-matter block above accurate — it drives the web panel's rendering and metadata.

## Status

| Field | Value |
|---|---|
| Author | [Name] |
| Reviewers | [Names] |
| Last Edited | [YYYY-MM-DD] |
| Status | Draft / In Review / Approved / In Execution / Live |
| Tracker | [Linear/Jira link] |

> The Status table mirrors front-matter for human readers. Update both together.

---

## 1. Problem

What problem are we solving? Who feels it? What's the evidence (data, customer quotes, support tickets)?

A good problem statement answers: *if we do nothing, what does it cost?*

---

## 2. Hypothesis

> We believe **[behavior]** for **[persona]** at **[execution layer]** will move **[metric]** because **[reason]**.

Reference the personas and metrics from the [execution framework](../frameworks/execution-framework.md). Don't invent new metrics if a framework metric fits.

---

## 3. Persona(s) Affected

| Persona | How they are affected |
|---|---|
| Tech | |
| Product | |
| UX | |

Delete rows that don't apply.

---

## 4. Execution Layer(s)

Which layer(s) does this work operate in? Detect / Diagnose / Quantify / Resolve / Predict.

If multiple, explain the dependency: e.g. "Diagnose work that requires a new Detect signal first."

---

## 5. Goals

- Outcome 1
- Outcome 2

## 6. Non-Goals

What we explicitly will not do — and why.

---

## 7. Solution Overview

High-level description. One paragraph + a diagram if useful. Keep this readable by non-engineers.

---

## 8. User Stories

- As a **[persona]**, I want **[action]** so that **[outcome]**.
- ...

---

## 9. Functional Requirements

1. ...
2. ...

## 10. Non-Functional Requirements

| Concern | Requirement |
|---|---|
| Performance | |
| Reliability | |
| Privacy / Security | |
| Accessibility | |

---

## 11. Success Metrics

| Metric | Source | Baseline | Target |
|---|---|---|---|
| | | | |

Prefer framework metrics from [Metrics & Glossary](../frameworks/execution-framework.md#5-metrics--glossary). Add custom metrics only when no framework metric fits.

---

## 12. Dependencies

| Area | Dependency | Owner |
|---|---|---|
| Backend | | |
| SDK | | |
| Pulse UI | | |
| Ingestion | | |
| AI Agent | | |

---

## 13. Open Questions

- ...

---

## 14. Rollout Plan

| Stage | Audience | Exit Criteria |
|---|---|---|
| Behind flag | Internal | |
| Dogfood | Internal customer | |
| Design partners | Selected partners | |
| General availability | All customers | |

---

## 15. Out of Scope / Future Work

What we are intentionally deferring, and the rough trigger for picking it up later.

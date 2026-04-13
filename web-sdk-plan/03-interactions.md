# Phase 2.5 — Interactions (Index)

**Goal:** Port the server-driven multi-step journey tracking system from Android/iOS to web. No new public API — hooks into `PulseWeb.trackEvent()`. Existing Interactions dashboard works without changes.

**Estimated duration:** Week 4–5 (parallel with Phase 2)
**Prerequisites:** Phase 1 complete.

---

## Sub-Documents

| # | Doc | What It Does |
|---|---|---|
| 03.1 | [Config Fetcher](./03.1-interaction-config.md) | Fetches interaction definitions from CDN, parses JSON |
| 03.2 | [Matching Algorithm](./03.2-interaction-matching.md) | State machine, sequence matching, timeout, blacklists |
| 03.3 | [Span Output](./03.3-interaction-span.md) | APDEX calculation, user category, OTel span creation |

---

## How It Fits Together

```
PulseWeb.trackEvent('event_name', props)
    │
    ├─→ [1] Emits custom event log record  (existing behaviour)
    │
    └─→ [2] InteractionManager.addEvent()
              │
              ├─→ InteractionEventsTracker[0].addEvent()  ← 03.2
              ├─→ InteractionEventsTracker[1].addEvent()
              └─→ InteractionEventsTracker[N].addEvent()
                        │
                        └─→ On sequence complete:
                              InteractionSpanBuilder.create()  ← 03.3
                                    │
                                    └─→ OTel span (pulse.type: interaction)
                                          → OTLP exporter → ClickHouse
```

Config is fetched once at SDK init (03.1) and drives how many trackers are created (03.2). Span output (03.3) is identical in attributes to Android/iOS — no backend or UI changes needed.

---

## Signal Output

| `pulse.type` | Kind | Doc |
|---|---|---|
| `interaction` | Span | 03.3 |

---

## Phase 2.5 Done Criteria

All sub-doc criteria must pass, plus:
- [ ] Interaction span visible in Pulse Interactions dashboard
- [ ] APDEX and user category correct for all threshold bands
- [ ] Config fetch failure is silent — no crash, tracking simply disabled
- [ ] Two concurrent interaction configs tracked independently

# Module 3 — Interactions (Index)

**Goal:** Port the server-driven multi-step journey tracking system from Android/iOS to web. No new public API — hooks into `PulseWeb.trackEvent()`. Existing Interactions dashboard works without changes.

**Prerequisites:** Module 1 complete.

---

## Sub-Documents

| # | Doc | What It Does |
|---|---|---|
| — | [M2 implementation plan (Android parity)](./IMPLEMENTATION-PLAN-M2-ANDROID-PARITY.md) | End-to-end plan, parity matrix, flow diagram, gaps vs `matching.md` / `span.md` |
| 03.1 | [Config Fetcher](./config.md) | Fetches interaction definitions from CDN, parses JSON |
| 03.2 | [Matching Algorithm](./matching.md) | State machine, sequence matching, timeout, blacklists |
| 03.3 | [Span Output](./span.md) | APDEX calculation, user category, OTel span creation |

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

## Module 3 Done Criteria

All sub-doc criteria must pass, plus:
- [ ] Interaction span visible in Pulse Interactions dashboard
- [ ] APDEX and user category correct for all threshold bands
- [ ] Config fetch failure is silent — no crash, tracking simply disabled
- [ ] Two concurrent interaction configs tracked independently

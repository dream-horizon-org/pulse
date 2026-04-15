# Phase 3 — Session Replay (Index)

**Goal:** Record browser sessions as compressed DOM snapshots, apply privacy masking by default, and ship recordings to Pulse backend for playback in the UI.

**Estimated duration:** Week 6–7
**Prerequisites:** Phase 1 complete. Phase 7 (Backend & UI) needed for playback in dashboard.

---

## Sub-Documents

| # | Doc | What It Does |
|---|---|---|
| 04.1 | [Recorder](./recorder.md) | rrweb setup, batching, flush strategy |
| 04.2 | [Privacy](./privacy.md) | Masking config, CSS class API, sensitive field defaults |
| 04.3 | [Transport](./transport.md) | Compression, OTLP log format, sendBeacon on unload |

---

## How It Fits Together

```
rrweb.record()                            ← 04.1 + 04.2
    │  emits DOM mutation events
    │  (inputs masked, sensitive blocks redacted)
    ↓
EventBuffer (in-memory, max 200 events)   ← 04.1
    │  flush every 5s OR on pagehide
    ↓
pako.gzip(JSON.stringify(events))         ← 04.3
    │  compress + base64 encode
    ↓
OTLP Log Record                           ← 04.3
  pulse.type: session_replay
  session_replay.chunk: "<base64>"
  session_replay.encoding: "gzip+base64"
    │
    └─→ OtlpHttpLogExporter → /v1/logs → ClickHouse
```

---

## Signal Output

| `pulse.type` | Kind | Doc |
|---|---|---|
| `session_replay` | Log | 04.1 + 04.3 |

---

## Key Constraint

Session replay data is stored in ClickHouse from Phase 3 onwards, but it is **not yet playable** until the rrweb player component is built in Phase 7 (Backend & UI). These two phases can be built in parallel.

---

## Phase 3 Done Criteria

All sub-doc criteria must pass, plus:
- [ ] Session replay chunks land in ClickHouse with correct `session.id`
- [ ] Masked input values absent from all recorded events
- [ ] `sampleRate: 0` produces zero recordings
- [ ] Final chunk delivered via `sendBeacon` on page close

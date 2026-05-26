# Funnel Drop-off Correlation with OTel Signals

**Goal:** Bridge the gap between Product and Engineering by correlating every funnel drop-off with its underlying reliability, performance, or UX cause — backed by direct evidence (session replay, traces, heatmaps, stack traces).

## 1. The Problem

Today, `otel.funnel_results_local` stores only per-step aggregates:

| Column | Purpose |
| --- | --- |
| `StepIndex` | 0-based step |
| `StepName` | Event name |
| `UserCount` | Unique users/sessions reaching the step |
| `ConversionPct` | % from step 0 |
| `MedianStepSeconds` | Median time from previous step |

This tells us **how many** dropped, but not **who**, **when**, or **why**. The `SessionId` is discarded at aggregation time, so there is no way to join back to crashes, traces, or replay.

**Core gap:** The funnel pipeline loses the one piece of information needed to correlate drop-offs with OTel signals — the per-session funnel state.

## 2. Walkthrough: Meet Alice

Let's follow one real user through a 5-step checkout funnel to make the design concrete.

### 2.1 The funnel definition

| Step | Event name |
| --- | --- |
| 0 | `view_product` |
| 1 | `add_to_cart` |
| 2 | `view_cart` |
| 3 | `start_checkout` |
| 4 | `payment_success` |

### 2.2 Aggregate result from `funnel_results_local`

| StepIndex | StepName | UserCount | ConversionPct |
| --- | --- | --- | --- |
| 0 | view_product | 100,000 | 100% |
| 1 | add_to_cart | 45,000 | 45% |
| 2 | view_cart | 40,000 | 40% |
| 3 | start_checkout | 30,000 | 30% |
| 4 | payment_success | 18,000 | 18% |

**12,000 users started checkout but never paid.** The PM clicks this drop-off bar. We need to answer: *why?*

### 2.3 Alice's session in `otel_logs`

| Timestamp | EventName | SessionId | UserId | ScreenName |
| --- | --- | --- | --- | --- |
| 10:00:05 | view_product | sess_abc | user_alice | ProductPage |
| 10:00:40 | add_to_cart | sess_abc | user_alice | ProductPage |
| 10:01:10 | view_cart | sess_abc | user_alice | CartPage |
| 10:01:25 | start_checkout | sess_abc | user_alice | CheckoutPage |
| (never emits `payment_success`) | | | | |

### 2.4 What else happened in Alice's session

**`otel_traces`** — there's an HTTP call right after she tapped "Checkout":

| Timestamp | SpanName | SessionId | HttpStatusCode | Duration |
| --- | --- | --- | --- | --- |
| 10:01:26 | POST /v2/cart/checkout | sess_abc | 503 | 8,200ms |

**`stack_trace_events`** — no crash or ANR.

**`session_summary`** for `sess_abc`:

- networkErrors: 1
- crashCount: 0
- frozenFrameCount: 0

**`session_replay_events`** — replay blob covering `10:01:20 → 10:01:45` showing 4 rapid taps on the Checkout button (rage tap), then app close.

### 2.5 The real story

Checkout API returned 503 → Alice rage-tapped → left the app.

All the evidence exists across our OTel tables. We just can't connect it to her funnel position because her `SessionId` was discarded.

### 2.6 Now meet Bob — why "per-session" isn't always the right grain

Alice has exactly one session in the funnel window, so there's no ambiguity about who she is or when she dropped off. But Bob uses the app differently:

| Timestamp | Session | EventName | Outcome |
| --- | --- | --- | --- |
| Mon 09:12 | `sess_bob_1` | view_product → add_to_cart → view_cart → start_checkout | 503 on checkout API → left |
| Mon 09:40 | `sess_bob_2` | view_product → add_to_cart | Got distracted, closed app |
| Tue 18:05 | `sess_bob_3` | view_product → add_to_cart → view_cart → start_checkout → payment_success | Purchased |

If our funnel is configured to count **sessions**, Bob contributes *three* data points — one drop-off at step 3, one at step 1, one converter — and `sess_bob_1`'s crash is a legitimate signal in the step-3 drop-off cohort.

But if Product asked *"how many customers couldn't get through checkout?"*, the answer is **zero** — Bob is a converter at the customer grain; his checkout-session crash is a reliability event against him, but not a funnel loss. Same raw data, different question.

Pulse funnels support both framings via a `mode` setting: `SESSIONS` or `UNIQUE_USERS`. The drop-off correlation system has to respect that setting all the way down. Section 5 covers how.

## 3. Solution Overview

Three new tables bridge funnel state to OTel signals:

1. **`funnel_session_state_local`** — per-session funnel position. Always populated for ordered funnels, both modes. Cohort source for SESSIONS funnels; powers x-ray drill-in for UNIQUE_USERS.
2. **`funnel_user_state_local`** — per-user state, computed independently via cross-session `windowFunnel`-equivalent chain on `otel.otel_logs`. Populated only when `mode = UNIQUE_USERS`. Cohort source for UNIQUE_USERS funnels — captures cross-session converters.
3. **`funnel_dropoff_attribution_local`** — precomputed cause-per-step with lift vs converter baseline.

All three are populated by either the ClickHouse async funnel compute (`ClickHouseFunnelComputeDao`, runs SQL inside ClickHouse against `otel_logs` / `otel_traces` / `session_summary`) or the Spark batch job (`FunnelComputeJob`, runs DataFrame ops on parquet shipped to S3 by the OTel s3-archiver). The dispatcher picks one engine per funnel run; both populate the same downstream tables with the same row shapes and shared `RunTime` stamp so the drop-off DAO is engine-agnostic.

**Why both tables (independent, not derived):** OTel signals (crashes, traces, replay) are inherently per-session in time — a crash happens in one specific session at one specific moment. The per-session table gives every OTel row a concrete session to anchor on. The user-level table mirrors what `windowFunnel` does for the displayed funnel chart: groups events by `AppInstallationId`, tracks chains across sessions, and resolves the canonical session = the one that contained the last matched step. Cohort numbers from `funnel_user_state` therefore match `funnel_results.UserCount` exactly, including cross-session conversions.

## 4. Schema Additions

### 4.1 `funnel_session_state_local` — the per-session bridge table

One row per session that entered the funnel. Keeps `SessionId` so every other OTel table becomes joinable. **Always populated for ordered funnels, regardless of mode.** What it powers depends on mode:

- **SESSIONS funnels:** primary cohort source for the drop-off panel. Cohort = `countIf(LastReachedStep >= k)`, lift baseline = converter sessions in the same table.
- **UNIQUE_USERS funnels:** powers the **x-ray drill-in** (per-session view of one user's funnel attempts) and single-session debug view. The drop-off panel's cohort numbers come from `funnel_user_state` instead, so a user with cross-session conversions stays correctly classified at the panel level — but the x-ray drill-in can still enumerate that user's per-session journey row by row from this table.

The chain logic per row is identical in both modes: a session-local single-anchor walk matching `windowFunnel`. `attempts` CTE selects `min(t0)` of step-0 events per session — exactly what `windowFunnel` would anchor on.

```sql
CREATE TABLE otel.funnel_session_state_local
ON CLUSTER `pulse-clickhouse`
(
    `FunnelId`             UInt64                 CODEC(T64, ZSTD(1)),
    `ProjectId`            LowCardinality(String) CODEC(ZSTD(1)),
    `RunTime`              DateTime64(3, 'UTC')   CODEC(DoubleDelta, ZSTD(1)),
    `SessionId`            String                 CODEC(ZSTD(1)),
    `UserId`               String                 CODEC(ZSTD(1)),
    `LastReachedStep`      UInt8                  CODEC(T64, ZSTD(1)),
    `LastReachedStepName`  LowCardinality(String) CODEC(ZSTD(1)),
    `LastReachedAt`        DateTime64(3, 'UTC')   CODEC(DoubleDelta, ZSTD(1)),
    `DropoffStep`          Int8                   CODEC(T64, ZSTD(1)),
    `TimeToDropoffSec`     Int64                  CODEC(T64, ZSTD(1)),
    `ScreenAtDropoff`      LowCardinality(String) CODEC(ZSTD(1)),
    `TraceIdAtDropoff`     String                 CODEC(ZSTD(1)),
    `AppVersion`           LowCardinality(String) CODEC(ZSTD(1)),
    `OsName`               LowCardinality(String) CODEC(ZSTD(1)),
    `OsVersion`            LowCardinality(String) CODEC(ZSTD(1)),
    `Platform`             LowCardinality(String) CODEC(ZSTD(1)),
    `DeviceModel`          LowCardinality(String) CODEC(ZSTD(1)),
    `NetworkProvider`      LowCardinality(String) CODEC(ZSTD(1)),
    `GeoCountry`           LowCardinality(String) CODEC(ZSTD(1)),

    INDEX idx_session_id SessionId TYPE bloom_filter(0.01) GRANULARITY 1,
    INDEX idx_user_id    UserId    TYPE bloom_filter(0.01) GRANULARITY 1,
    INDEX idx_run_time   RunTime   TYPE minmax GRANULARITY 1
)
ENGINE = ReplicatedMergeTree('/clickhouse/tables/{shard}/otel/funnel_session_state_local', '{replica}')
PARTITION BY toYYYYMM(RunTime)
ORDER BY (ProjectId, FunnelId, RunTime, DropoffStep, SessionId)
TTL toDateTime(RunTime) + toIntervalDay(7)  TO VOLUME 'cold',
    toDateTime(RunTime) + toIntervalDay(90) DELETE
SETTINGS index_granularity = 8192, storage_policy = 'tiered';
```

`DropoffStep` conventions:

- `-1` → converted (reached final step)
- Otherwise → the step the user *failed to reach*. (E.g. Alice reached step 3, so `DropoffStep = 4`.)

Alice's row:

| FunnelId | SessionId | UserId | LastReachedStep | LastReachedStepName | LastReachedAt | DropoffStep | ScreenAtDropoff | TraceIdAtDropoff |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 42 | sess_abc | user_alice | 3 | start_checkout | 10:01:25 | 4 | CheckoutPage | trace_xyz |

Bob's three rows (all land in this same table, one per session):

| SessionId | UserId | LastReachedStep | DropoffStep | LastReachedAt |
| --- | --- | --- | --- | --- |
| sess_bob_1 | user_bob | 3 (start_checkout) | 4 | Mon 09:12:48 |
| sess_bob_2 | user_bob | 1 (add_to_cart) | 2 | Mon 09:40:15 |
| sess_bob_3 | user_bob | 4 (payment_success) | -1 | Tue 18:05:33 |

### 4.2 `funnel_user_state_local` — the per-user rollup

One row per user that entered the funnel, derived from the session bridge. **Only populated when the funnel's `mode = UNIQUE_USERS`.** Picks each user's canonical session — the one that reached the furthest step, latest on ties — so OTel attribution has exactly one `LastReachedAt` anchor per user.

```sql
CREATE TABLE otel.funnel_user_state_local
ON CLUSTER `pulse-clickhouse`
(
    `FunnelId`                   UInt64                 CODEC(T64, ZSTD(1)),
    `ProjectId`                  LowCardinality(String) CODEC(ZSTD(1)),
    `RunTime`                    DateTime64(3, 'UTC')   CODEC(DoubleDelta, ZSTD(1)),
    `UserId`                     String                 CODEC(ZSTD(1)),
    `MaxReachedStep`             UInt8                  CODEC(T64, ZSTD(1)),
    `DropoffStep`                Int8                   CODEC(T64, ZSTD(1)),
    `CanonicalSessionId`         String                 CODEC(ZSTD(1)),
    `CanonicalLastReachedAt`     DateTime64(3, 'UTC')   CODEC(DoubleDelta, ZSTD(1)),
    `CanonicalTraceIdAtDropoff`  String                 CODEC(ZSTD(1)),
    `CanonicalScreenAtDropoff`   LowCardinality(String) CODEC(ZSTD(1)),
    `AppVersion`                 LowCardinality(String) CODEC(ZSTD(1)),
    `OsName`                     LowCardinality(String) CODEC(ZSTD(1)),
    `OsVersion`                  LowCardinality(String) CODEC(ZSTD(1)),
    `Platform`                   LowCardinality(String) CODEC(ZSTD(1)),
    `DeviceModel`                LowCardinality(String) CODEC(ZSTD(1)),
    `NetworkProvider`            LowCardinality(String) CODEC(ZSTD(1)),
    `GeoCountry`                 LowCardinality(String) CODEC(ZSTD(1)),
    `SessionAttempts`            UInt32                 CODEC(T64, ZSTD(1)),

    INDEX idx_user_id        UserId             TYPE bloom_filter(0.01) GRANULARITY 1,
    INDEX idx_canonical_sess CanonicalSessionId TYPE bloom_filter(0.01) GRANULARITY 1,
    INDEX idx_run_time       RunTime            TYPE minmax             GRANULARITY 1
)
ENGINE = ReplicatedMergeTree('/clickhouse/tables/{shard}/otel/funnel_user_state_local', '{replica}')
PARTITION BY toYYYYMM(RunTime)
ORDER BY (ProjectId, FunnelId, RunTime, DropoffStep, UserId)
TTL toDateTime(RunTime) + toIntervalDay(7)  TO VOLUME 'cold',
    toDateTime(RunTime) + toIntervalDay(90) DELETE
SETTINGS index_granularity = 8192, storage_policy = 'tiered';
```

Derivation rules (computed directly from `otel.otel_logs` via cross-session windowFunnel chain — independent of `funnel_session_state`):

- Group events by `AppInstallationId` (matches what `windowFunnel` does for UNIQUE_USERS funnel_results).
- `attempts` CTE: `min(t0)` of step-0 events per user (single-anchor — same rule windowFunnel uses).
- `s1..s(N-1)` CTEs: chain walk forward in time within `[t_{k-1}, t0 + window]`, tracking `SessionId` at each match via `argMinIf(sid, ts, condition)`.
- `MaxReachedStep` = highest k where `t_k IS NOT NULL`.
- `DropoffStep` = `-1` if `t_{stepCount-1} IS NOT NULL` (final step reached, possibly cross-session); else `MaxReachedStep + 1`.
- `CanonicalSessionId` = `sid` of the deepest matched step (chosen via `multiIf` cascade). For cross-session converters, this is the session that contained the final step's match.
- `CanonicalLastReachedAt` = timestamp of the deepest matched step.
- Canonical dimensions (TraceId, ScreenAtDropoff, AppVersion, OS, device, network, geo) hydrated by joining `otel.otel_logs` on `(CanonicalSessionId, CanonicalLastReachedAt)`.
- `SessionAttempts` = `uniqExact(SessionId)` per user across all funnel events — how many distinct sessions touched the funnel.

**Cohort alignment:** `countIf(MaxReachedStep >= k)` from this table equals `funnel_results.UserCount[k]` exactly for the same UNIQUE_USERS funnel run. Cross-session converters that windowFunnel sees are correctly captured here too.

Bob's single user-level row (UNIQUE_USERS mode, payment_success in `sess_bob_3`):

| UserId | MaxReachedStep | DropoffStep | CanonicalSessionId | CanonicalLastReachedAt | SessionAttempts |
| --- | --- | --- | --- | --- | --- |
| user_bob | 4 (payment_success) | -1 (final step reached) | `sess_bob_3` | Tue 18:05:33 | 3 |

If Bob's converting chain were *cross-session* (e.g. step 0 in `sess_bob_1`, steps 1-4 in `sess_bob_3`, all within the configured window), the canonical session would still be `sess_bob_3` because that's where step 4 happened. OTel correlation anchors on the failure / completion moment, not on where the chain started.

### 4.3 `funnel_dropoff_attribution_local` — precomputed causes

One row per (step × cause) with baseline lift. The side-panel is a single lookup, not a fan-out.

```sql
CREATE TABLE otel.funnel_dropoff_attribution_local
ON CLUSTER `pulse-clickhouse`
(
    `FunnelId`           UInt64                 CODEC(T64, ZSTD(1)),
    `ProjectId`          LowCardinality(String) CODEC(ZSTD(1)),
    `RunTime`            DateTime64(3, 'UTC')   CODEC(DoubleDelta, ZSTD(1)),
    `StepIndex`          UInt8                  CODEC(T64, ZSTD(1)),
    `CauseKind`          LowCardinality(String) CODEC(ZSTD(1)),
    `CauseKey`           String                 CODEC(ZSTD(1)),
    `DropoffCohort`      UInt64                 CODEC(T64, ZSTD(1)),
    `DropoffAffected`    UInt64                 CODEC(T64, ZSTD(1)),
    `ConverterAffected`  UInt64                 CODEC(T64, ZSTD(1)),
    `Lift`               Float64                CODEC(ZSTD(1)),
    `PValue`             Float64                CODEC(ZSTD(1)),
    `ExampleSessions`    Array(String)          CODEC(ZSTD(3))
)
ENGINE = ReplicatedMergeTree('/clickhouse/tables/{shard}/otel/funnel_dropoff_attribution_local', '{replica}')
PARTITION BY toYYYYMM(RunTime)
ORDER BY (ProjectId, FunnelId, RunTime, StepIndex, Lift)
TTL toDateTime(RunTime) + toIntervalDay(7)  TO VOLUME 'cold',
    toDateTime(RunTime) + toIntervalDay(90) DELETE
SETTINGS index_granularity = 8192, storage_policy = 'tiered';
```

**Cohort unit follows funnel mode:** `DropoffCohort`, `DropoffAffected`, `ConverterAffected` count **sessions** for SESSIONS funnels and **users** for UNIQUE_USERS funnels. `ExampleSessions` is always `SessionId`s — for UNIQUE_USERS it holds each dropped user's canonical session, for SESSIONS it holds the dropped sessions directly.

#### `CauseKind` taxonomy

| CauseKind | Source table | CauseKey example |
| --- | --- | --- |
| `crash` | `stack_trace_events` | `OutOfMemoryError@CheckoutActivity` (GroupId) |
| `anr` | `stack_trace_events` | ANR GroupId |
| `non_fatal` | `stack_trace_events` | Exception GroupId |
| `http_5xx` | `otel_traces` | `POST /v2/cart/checkout` |
| `http_4xx` | `otel_traces` | `GET /v2/user/profile 401` |
| `frozen_frame` | `session_summary` + span | ScreenName |
| `slow_interaction` | `session_summary` + span | ScreenName |
| `rage_tap` | `session_replay_events` | ScreenName |
| `dead_click` | `session_replay_events` | ScreenName + element |
| `network_offline` | connectivity spans | — |

**Why store Lift, not raw %:** A 30% "had a 5xx" number is meaningless if 28% of *converters* also had a 5xx. Ranking the panel by lift (droppers% ÷ converters%) surfaces the real culprits and hides ambient noise.

## 5. Mode Semantics: SESSIONS vs UNIQUE_USERS

Every funnel is configured with one of two cohort modes. The drop-off system honours that choice end-to-end — the bridge table queried, the cohort denominator, the lift calculation, and the UI copy all branch on it.

### 5.1 Side-by-side

| Aspect | SESSIONS mode | UNIQUE_USERS mode |
| --- | --- | --- |
| Unit counted | One session = one data point | One user = one data point (dedup across their sessions) |
| Example question | "Of 10,000 sessions that hit *Home*, how many reached *Checkout*?" | "Of 4,200 unique users that hit *Home*, how many *ever* reached *Checkout*?" |
| Good for | Session-quality, per-visit conversion, funnels where each visit is an independent intent | Customer-journey, acquisition funnels, funnels where retry across sessions is expected |
| Cohort denominator at step k | `COUNT(DISTINCT SessionId WHERE LastReachedStep >= k)` | `COUNT(DISTINCT UserId WHERE MaxReachedStep >= k)` |
| OTel anchor per cohort member | The session itself (one `LastReachedAt` per session) | The user's canonical session (furthest-reaching, latest on ties) |
| Bridge table queried by DAO | `funnel_session_state` | `funnel_user_state` |
| User rollup table written? | No | Yes |

### 5.2 Bob's three sessions under both modes

Using Bob from §2.6 — three sessions, one converted.

**SESSIONS mode — Bob contributes three cohort members:**

| Step k | Cohort includes | Cohort count | Notes |
| --- | --- | --- | --- |
| 0 → 1 | — | Bob's three sessions all reached step 1 | |
| 1 → 2 | `sess_bob_2` | 1 dropper | `sess_bob_2` stopped at step 1 |
| 3 → 4 | `sess_bob_1` | 1 dropper | `sess_bob_1` failed at checkout (503) |
| converted | `sess_bob_3` | 1 converter | |

→ Bob is simultaneously a dropper (at step 1 and step 3) AND a converter in the same report. The 503 from `sess_bob_1` is a legitimate signal in the step-3 dropper cohort AND shows up in the converter baseline (via `sess_bob_3`, which had no 503).

**UNIQUE_USERS mode — Bob contributes one cohort member:**

| Step k | Cohort includes | Cohort count | Notes |
| --- | --- | --- | --- |
| any step | `user_bob` | converter only | `sess_bob_3` converted → `DropoffStep = -1` → Bob is a converter at every step's dropper/converter split |

→ Bob is purely a converter. His crashed `sess_bob_1` is **silently excluded** from attribution because his canonical session is `sess_bob_3`, which had no crash. Any signal the user rollup surfaces for Bob is anchored on `sess_bob_3`.

### 5.3 The subtle consequence

For the exact same raw data, SESSIONS mode and UNIQUE_USERS mode will often produce **different lift numbers** for the same cause:

- SESSIONS mode tends to produce **smaller lifts** — dropper sessions and converter sessions can belong to the same user, so bad moments dilute the converter baseline (Bob's 503 counts as an event that happened to a user who also converted).
- UNIQUE_USERS mode tends to produce **larger lifts** for signals concentrated in the dropper cohort — because converter users' "bad sessions" never contribute to the baseline at all (only their canonical/winning session does).

Both are correct — they answer different questions. The panel's `mode` field echoes the funnel's configured mode so the UI (and readers of the API) are never guessing.

## 6. Compute Pipeline

Extend the existing ClickHouse async funnel job (the one that writes `funnel_results_local` via `windowFunnel`). Both bridge inserts share the same `RunTime` literal as `funnel_results` so the drop-off DAO's `MAX(RunTime)` join finds them as one consistent run.

### Step 1 — Per-session sequencing (always written for ordered funnels)

For each session in the run window, scan `otel_logs` ordered by `Timestamp`, run a single-anchor chain walk (matching `windowFunnel` semantics — anchor on the first step-0 event, walk forward in time), and emit one `funnel_session_state` row.

- Identity is always `SessionId` regardless of funnel mode.
- `attempts` CTE: `min(t0)` per session — single anchor, not multi-attempt.
- For SESSIONS funnels this table IS the cohort source.
- For UNIQUE_USERS funnels this table powers the x-ray drill-in only — cohort numbers come from Step 2.

### Step 2 — Cross-session user state (UNIQUE_USERS only)

Independently from Step 1, scan `otel_logs` again grouped by `AppInstallationId` and emit one `funnel_user_state` row per user. The chain walks across sessions: step k can be in a different session than step k-1, as long as both fall within `[t0, t0 + window]`.

- `attempts` CTE: `min(t0)` of step-0 events per user (matches `windowFunnel`'s anchoring rule).
- `s1..s(N-1)` CTEs: forward chain walk via `LEFT JOIN step_events`, with `argMinIf(sid, ts, condition)` tracking which session contributed each match.
- `MaxReachedStep` = highest `k` where `t_k IS NOT NULL`.
- `DropoffStep` = `-1` if `t_{stepCount-1} IS NOT NULL` (final step matched, possibly cross-session); else `MaxReachedStep + 1`.
- `CanonicalSessionId` = `sid` of the deepest matched step (multiIf cascade picks `sid_{depth-1}`).
- `SessionAttempts` = `uniqExact(SessionId)` per user across funnel events.
- Dimensions hydrated from `otel_logs` joined on `(CanonicalSessionId, CanonicalLastReachedAt)`.

Skipped for SESSIONS funnels and unordered funnels.

`funnel_user_state` is **NOT derived from `funnel_session_state`** — it scans `otel_logs` directly. This is what guarantees cohort alignment with `funnel_results`: a user whose conversion chain spans sessions is invisible to a per-session view but correctly captured by the cross-session walk.

### Step 3 — Cohort split

Partition the bridge rows by `DropoffStep`. `DropoffStep = -1` is the converter cohort; any other value is a dropper cohort for that step. The DAO reads from `funnel_session_state` for SESSIONS funnels and `funnel_user_state` for UNIQUE_USERS — the cohort query is otherwise identical.

### Step 4 — Cause join

For each dropped session (or, in UNIQUE_USERS mode, each dropped user's canonical session), in a window `[LastReachedAt - 30s, LastReachedAt + 60s]`, join against:

- `stack_trace_events` → crash / ANR / non-fatal (group by `GroupId`, `ExceptionType`)
- `otel_traces` where `StatusCode = 'Error'` or HTTP status ≥ 400 → network errors
- `session_summary` where `frozenFrameCount > 0` or `slowInteractionCount > 0`
- `session_replay_events` → rage-tap / dead-click heuristics from interaction events

### Step 5 — Baseline

Run the same join on the converter cohort, anchored at equivalent step-completion time, with the same window length. Same bridge table as step 4 — SESSIONS mode anchors on each converting session's final-step timestamp; UNIQUE_USERS mode anchors on each converter's canonical session.

### Step 6 — Lift + significance

For each (step × cause):

```
Lift   = (DropoffAffected / DropoffCohort) / (ConverterAffected / ConverterCohort)
PValue = chi-square or Fisher's exact
```

Keep only causes with `Lift > 1.5` and `PValue < 0.05`.

### Step 7 — Evidence sampling

Reservoir-sample top-N (cap ~50) `SessionId`s per cause, biased toward sessions that have a `session_replay_events` blob available. In UNIQUE_USERS mode these are canonical session IDs; in SESSIONS mode they're the dropped sessions directly.

### Step 8 — Write (`funnel_dropoff_attribution`)

Steps 3–7 above are executed in a single `INSERT INTO otel.funnel_dropoff_attribution … WITH …` SQL block built by `ClickHouseFunnelComputeDao.buildAttributionInsertSql(def, runTime)`. One row per `(FunnelId, RunTime, StepIndex, CauseKind, CauseKey)` with cohort sizes, affected counts, lift, and a capped 50-element `ExampleSessions` array.

- Bridge table read mode-switches: SESSIONS funnels read `funnel_session_state`, UNIQUE_USERS funnels read `funnel_user_state` (with `CanonicalSessionId` aliased to `SessionId` and `CanonicalLastReachedAt` aliased to `LastReachedAt` so the cause-join SQL is identical for both modes).
- Per-step dropper cohort sizes via a `dropper_cohorts` CTE keyed on `step`; scalar converter cohort via `converter_cohort`.
- Three cause branches UNION ALL'd together: stack_trace_events (`crash` / `anr` / `non_fatal`), otel_traces (`http_5xx` / `http_4xx`), session_summary (`frozen_frame`). `groupArraySample(50)` per (step × cause) caps example session IDs.
- `PValue` is emitted as `0.0` in v1 — chi-square / Fisher's exact deferred. Lift filtering does most of the ranking work.
- Skipped for unordered funnels and funnels with fewer than 2 steps (nothing to attribute).

**Read path:** `FunnelDropoffQueries.buildCausesSqlFromAttribution` does a single indexed lookup against `funnel_dropoff_attribution` filtered on `(ProjectId, FunnelId, RunTime, StepIndex)`. The DAO tries this precomputed path first; if it returns zero rows (e.g. for an old run that predates the attribution writer), it falls back to the live `buildCausesSql` join against the OTel signal tables. UI behavior is identical either way.

**Shared RunTime contract:** All four inserts — `funnel_results`, `funnel_session_state`, `funnel_user_state`, `funnel_dropoff_attribution` — MUST share one `RunTime` literal so the drop-off DAO's `MAX(RunTime)` lookup returns them as one consistent run. The ClickHouse compute path threads this via `ClickHouseFunnelComputeDao.newRunTimeLiteral()` → `buildInsertSqlForDefinition(def, runTime)` → `buildInsertSqlWindowFunnel(def, runTime)`, plus the three bridge/attribution builders. The Spark compute path uses the same contract — `runTime` is a method parameter passed through `FunnelComputeJob.runFunnels` → `computeFunnel` → `emitBridgeAndRollup` → `emitAttribution`, then stamped into every row of all four inserts via `ClickHouseClient`.

**Cause coverage across engines:**

| Cause kind | CH compute path | Spark compute path |
|---|---|---|
| `crash` / `anr` / `non_fatal` | ✅ via `stack_trace_events` JOIN | ✅ via `otel_logs` parquet filtered by `pulse_type` |
| `http_5xx` / `http_4xx` | ✅ via `otel_traces` JOIN | ✅ via `otel_traces` parquet HTTP attributes |
| `frozen_frame` | ✅ via `session_summary` JOIN | ⚠️ deferred — session_summary is a CH materialized view, not S3-archived. DAO falls back to live join for this one cause when Spark ran the funnel. |

## 7. Lift Example — Alice's Step

Aggregating over all 12,000 sessions that dropped at step 3 (SESSIONS mode):

| CauseKind | CauseKey | Droppers % | Converters % | Lift | Signal |
| --- | --- | --- | --- | --- | --- |
| `http_5xx` | POST /v2/cart/checkout | 30.0% | 1.5% | **20×** | strong |
| `crash` | OOM@CheckoutActivity | 17.5% | 0.2% | **85×** | strong |
| `frozen_frame` | CheckoutPage | 6.7% | 4.0% | 1.7× | weak |
| `http_4xx` | GET /v2/user/profile 401 | 4.2% | 3.9% | 1.07× | noise |

Lift separates real culprits from ambient errors.

If the same funnel were configured as UNIQUE_USERS, the same query against `funnel_user_state` would typically produce higher lift numbers for the same causes, because any converter user's own "bad sessions" don't count against the converter baseline — only their canonical (winning) session does. Exact numbers depend on how many converters had prior failed sessions in the window.

## 8. Side-Panel API Contract

### Request

```
GET /v1/funnels/{funnelId}/dropoffs/{stepIndex}?runTime=2026-04-19T00:00:00Z
```

### Response (SESSIONS mode — Alice's funnel)

```json
{
  "funnelId": 42,
  "stepIndex": 3,
  "stepName": "start_checkout",
  "mode": "SESSIONS",
  "dropoffCohort": 12000,
  "converterCohort": 18000,
  "causes": [
    {
      "causeKind": "crash",
      "causeKey": "OutOfMemoryError@CheckoutActivity",
      "causeLabel": "OOM @ CheckoutActivity",
      "dropoffAffected": 2100,
      "converterAffected": 36,
      "lift": 85.0,
      "dropoffRate": 17.5,
      "exampleSessionIds": ["sess_abc", "sess_def", "sess_ghi"]
    },
    {
      "causeKind": "http_5xx",
      "causeKey": "POST /v2/cart/checkout",
      "causeLabel": "HTTP 503 · POST /v2/cart/checkout",
      "dropoffAffected": 3600,
      "converterAffected": 270,
      "lift": 20.0,
      "dropoffRate": 30.0,
      "exampleSessionIds": ["sess_abc", "sess_jkl", "sess_mno"]
    }
  ]
}
```

### Response (UNIQUE_USERS mode — same funnel, same moment)

```json
{
  "funnelId": 42,
  "stepIndex": 3,
  "stepName": "start_checkout",
  "mode": "UNIQUE_USERS",
  "dropoffCohort": 9500,
  "converterCohort": 15200,
  "causes": [
    {
      "causeKind": "crash",
      "causeKey": "OutOfMemoryError@CheckoutActivity",
      "causeLabel": "OOM @ CheckoutActivity",
      "dropoffAffected": 1980,
      "converterAffected": 12,
      "lift": 267.0,
      "dropoffRate": 20.8,
      "exampleSessionIds": ["sess_abc", "sess_def", "sess_ghi"]
    },
    {
      "causeKind": "http_5xx",
      "causeKey": "POST /v2/cart/checkout",
      "causeLabel": "HTTP 503 · POST /v2/cart/checkout",
      "dropoffAffected": 3120,
      "converterAffected": 140,
      "lift": 35.6,
      "dropoffRate": 32.8,
      "exampleSessionIds": ["sess_abc", "sess_jkl", "sess_mno"]
    }
  ]
}
```

Same underlying incident — different cohort unit, different lift numbers. The `mode` field tells the UI (and downstream API consumers) which unit the numbers are in. Cohort sizes shrink (users < sessions) and lifts grow because converter users' bad sessions no longer dilute the baseline.

### Rendered panel (SESSIONS mode)

```
12,000 sessions dropped at: start_checkout → pay
-------------------------------------------------
Crash: OOM @ CheckoutActivity     85x lift
  2,100 affected  · [view group] [3 examples]
-------------------------------------------------
HTTP 503: POST /v2/cart/checkout  20x lift
  3,600 affected  · [view traces] [3 examples]
-------------------------------------------------
Frozen frames on CheckoutPage     1.7x lift
  800 affected  · [heatmap] [3 examples]
```

### Rendered panel (UNIQUE_USERS mode)

```
9,500 users dropped at: start_checkout → pay
-------------------------------------------------
Crash: OOM @ CheckoutActivity    267x lift
  1,980 affected  · [view group] [3 examples]
-------------------------------------------------
HTTP 503: POST /v2/cart/checkout  36x lift
  3,120 affected  · [view traces] [3 examples]
-------------------------------------------------
Frozen frames on CheckoutPage     2.1x lift
  640 affected  · [heatmap] [3 examples]
```

The only UI copy that branches on mode is the header unit ("sessions" ↔ "users") and the KPI subtitles. Cause rows, evidence drill-in, and the significant-lift colour threshold are identical.

## 9. Evidence Linking

For each `evidenceSession`, the backend builds drill-in links at fetch time. The session used is the session directly in SESSIONS mode, and the canonical session in UNIQUE_USERS mode — either way it's one concrete SessionId tied to one `LastReachedAt`.

| Evidence | Source | Join key | UX |
| --- | --- | --- | --- |
| Session replay | `session_replay_events.BlockUrls` + `BlockFirstTimestamps` nearest `LastReachedAt` | `SessionId` | Replay player deep-linked to ±10s of dropoff |
| Trace waterfall | `otel_traces` | `TraceIdAtDropoff` | Full trace view, FE + BE spans |
| Stack trace group | `stack_trace_events` | `GroupId` / `Fingerprint` | Existing crash view |
| Heatmap | replay interaction events aggregated by `ScreenName` | `ScreenName = ScreenAtDropoff` | Tap-density over screen screenshot |

For UNIQUE_USERS funnels, the evidence panel shows the user's canonical session. A future "show me all of this user's attempts" drill-in would iterate `funnel_session_state` filtered on the user's `UserId` — cheap because the `idx_user_id` bloom filter is already in place.

## 10. Query Examples

### 10.1 Pull the drop-off cohort — SESSIONS mode

```sql
SELECT SessionId, UserId, LastReachedAt, TraceIdAtDropoff
FROM otel.funnel_session_state
WHERE ProjectId   = 'proj-123'
  AND FunnelId    = 42
  AND RunTime     = (SELECT max(RunTime) FROM otel.funnel_session_state
                     WHERE ProjectId = 'proj-123' AND FunnelId = 42)
  AND DropoffStep = 4;
```

### 10.2 Pull the drop-off cohort — UNIQUE_USERS mode

```sql
SELECT UserId, CanonicalSessionId AS SessionId,
       CanonicalLastReachedAt AS LastReachedAt,
       CanonicalTraceIdAtDropoff AS TraceIdAtDropoff,
       SessionAttempts
FROM otel.funnel_user_state
WHERE ProjectId   = 'proj-123'
  AND FunnelId    = 42
  AND RunTime     = (SELECT max(RunTime) FROM otel.funnel_user_state
                     WHERE ProjectId = 'proj-123' AND FunnelId = 42)
  AND DropoffStep = 4;
```

Note the DAO pattern: alias the canonical columns back to their SESSIONS-mode names (`LastReachedAt`, `TraceIdAtDropoff`) so the downstream cause join SQL is literally the same string for both modes — only the `FROM` table switches.

### 10.3 Correlate network errors

```sql
SELECT
    ifNull(t.SpanAttributes['http.url'], '') AS url,
    count() AS droppers_affected
FROM otel.funnel_session_state AS s
INNER JOIN otel.otel_traces AS t
    ON s.SessionId = t.SessionId
   AND t.Timestamp BETWEEN s.LastReachedAt - INTERVAL 30 SECOND
                       AND s.LastReachedAt + INTERVAL 60 SECOND
WHERE s.FunnelId    = 42
  AND s.DropoffStep = 4
  AND toUInt16OrZero(t.SpanAttributes['http.status_code']) >= 500
GROUP BY url
ORDER BY droppers_affected DESC;
```

For UNIQUE_USERS mode, replace `otel.funnel_session_state AS s` with:

```sql
(SELECT UserId, CanonicalSessionId AS SessionId, CanonicalLastReachedAt AS LastReachedAt
 FROM otel.funnel_user_state
 WHERE FunnelId = 42 AND DropoffStep = 4 AND RunTime = …) AS s
```

…and the cohort denominator becomes `count(DISTINCT UserId)` instead of `count()`. Everything else is identical.

Run the same query with `DropoffStep = -1` (converters), divide the two rates → **lift**.

### 10.4 Correlate crashes / ANRs

```sql
SELECT
    e.ExceptionType,
    e.GroupId,
    count() AS droppers_affected
FROM otel.funnel_session_state AS s
INNER JOIN otel.stack_trace_events AS e
    ON s.SessionId = e.SessionId
   AND e.Timestamp BETWEEN s.LastReachedAt - INTERVAL 30 SECOND
                       AND s.LastReachedAt + INTERVAL 60 SECOND
WHERE s.FunnelId    = 42
  AND s.DropoffStep = 4
GROUP BY e.ExceptionType, e.GroupId
ORDER BY droppers_affected DESC;
```

### 10.5 Segment a UNIQUE_USERS drop-off by retry behaviour

`SessionAttempts` falls out of the user rollup for free — cheap segmentation:

```sql
SELECT
    multiIf(SessionAttempts = 1, 'one-and-done',
            SessionAttempts <= 3, '2-3 attempts',
            '4+ attempts') AS retry_bucket,
    count() AS users
FROM otel.funnel_user_state
WHERE FunnelId    = 42
  AND DropoffStep = 4
GROUP BY retry_bucket;
```

"One-and-done" droppers usually hit a hard failure (crash, 503). "4+ attempts" droppers usually hit a soft-loop (UX friction, validation error) — very different triage paths.

## 11. What Else This Unlocks

The bridge table is the leverage point — once sessions are tagged with funnel position, many adjacent features come almost for free.

| # | Capability | Description |
| --- | --- | --- |
| 1 | Release regression view | Diff cause-mix between two `AppVersion`s on the same step. "v4.2.1 introduced a 503 on `/v2/cart` costing 3% checkout conversion, Android-only." |
| 2 | Segment lift | Attribution pivoted by Platform / OsVersion / DeviceModel / NetworkProvider / GeoCountry. "This is a low-end Android on 3G problem, not a product problem." |
| 3 | Cross-step blame | A crash at step 2 often surfaces as dropout at step 3. Attribute the crash to the next observed dropout. |
| 4 | Frustration signals as soft causes | Rage-taps, dead-clicks, back-navigations. Not all drop-offs are technical — some are UX friction. |
| 5 | Slow / frozen-frame bridge | `session_summary.slowInteractionCount` + `frozenFrameCount` at the dropoff screen → "users waited >5s, then left." |
| 6 | Screen-scoped heatmap | Aggregate interaction coordinates across *just the drop-off cohort* on the drop-off screen → where confused users tap. |
| 7 | Anomaly detection on cause mix | Detect spikes in `(StepIndex, CauseKind, CauseKey)` vs a 7d rolling baseline; auto-open an incident with linked release + evidence. |
| 8 | Funnel SLO / error budget | Define target conversion per step; when reliability-attributed loss exceeds budget, page the owning team via a `cause_kind → team` map. |
| 9 | Predictive dropoff | Forecast next-hour step loss from rate-of-change in crash/ANR rate on the step's screen. Useful during staged rollouts. |
| 10 | LLM narrative summary | Feed top causes + lift + segments to Claude → one-paragraph "what changed this week" for the funnel owner. |
| 11 | Trace-level Product↔Eng bridge | Trace waterfall from `TraceIdAtDropoff` shows BE latency / 5xx vs FE render. PM hands engineer a trace, not a Jira description. |
| 12 | "Moment" deep-link in replay | `BlockFirstTimestamps` jumps the player to the exact moment of `LastReachedAt`. No more scrubbing 12-minute replays. |
| 13 | Cohort export | Dropped-at-step-N users/sessions → email campaign, in-app nudge, support outreach. UNIQUE_USERS mode makes the "users, not sessions" export trivial. |
| 14 | Single-session funnel x-ray | Inverse view: pick one session, see its step-by-step path with errors / spans / replay inline. Useful for support tickets. |
| 15 | Retry-behaviour segmentation | `funnel_user_state.SessionAttempts` splits "one-and-done" vs "kept retrying" droppers — usually different root causes, different triage paths. (See §10.5.) |

## 12. Minimum Viable Slice

**Fastest path to ship.** If the roadmap needs a quick win, build in this order:

1. ✅ Add `funnel_session_state_local`. Supports SESSIONS-mode funnels end-to-end.
2. ✅ Side-panel runs the cause query on-demand against `stack_trace_events` + `session_summary` + `otel_traces` for the dropped cohort. Sub-second for most projects. (Retained as the fallback path when precomputed rows are absent.)
3. ✅ Add `funnel_user_state_local` so UNIQUE_USERS funnels get the user-level cohort unit. Independently computed cross-session windowFunnel chain (not derived from session_state) so cross-session converters are captured.
4. ✅ Add `funnel_dropoff_attribution_local` + baseline/lift. Side-panel reads precomputed rows; falls back to live join when no rows exist for the requested RunTime.
5. Uncomment the HTTP materialized columns in `clickhouse_cluster_migration.sql` (lines 134–140): `HttpUrl`, `HttpHost`, `HttpMethod`, `HttpStatusCode` + indexes. Makes the network-cause query an indexed lookup instead of a Map scan. *(still pending)*

## 13. Mental Model

The funnel chart knows **how many** dropped. The two bridge tables remember **who** dropped, **when**, and (for cross-session chains) **which session held the final step**. Every other OTel table we already have — traces, crashes, session replay, heatmaps — becomes joinable on those rows.

Two tables, two questions, both answered with `windowFunnel` semantics:

- **`funnel_session_state`** — "for each session that touched this funnel, how far did it get and when did it stop?" Answers SESSIONS-mode cohort questions, plus per-session drill-in for any user.
- **`funnel_user_state`** — "for each user that touched this funnel, anywhere across their sessions, how far did the cross-session chain reach and which session contained the final matched step?" Answers UNIQUE_USERS-mode cohort questions; cohort numbers match the funnel chart exactly because both use `windowFunnel`.

Everything in section 11 — release diffs, segment lift, anomaly detection, LLM summaries, predictive dropoff — is a different query on top of these two tables. The two are **independent**: `funnel_user_state` scans `otel_logs` directly, not `funnel_session_state`. That's the design choice that lets cross-session converters stay correctly classified.

## Open Questions

- Retention: keep `funnel_session_state` and `funnel_user_state` for the standard 7d hot / 90d cold, or shorter since they're denormalized? Current: same TTL as `funnel_results`.
- Compute cadence: re-run attribution hourly, daily, or on-demand? Affects `funnel_dropoff_attribution` volume.
- Unordered funnels: per-user rollup needs a different "furthest step" definition when steps can occur in any order — current v1 skips bridge + rollup for unordered funnels.
- Step-definition flexibility: today steps are a linear event sequence. Do we need branching steps (any-of) for this v1?

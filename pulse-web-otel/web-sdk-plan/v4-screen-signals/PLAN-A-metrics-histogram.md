# Plan A (rejected) — screen navigation as metrics (histogram)

## Approach

Emit screen navigation timing as **histograms** instead of spans:
- `screen.load.duration.ms` — histogram of page load times
- `screen.tti.ms` — histogram of time-to-interactive values
- `screen.session.duration.ms` — histogram of time spent per screen

**Rationale considered:**
- Low cardinality aggregation (histograms bucket by default)
- Familiar to infrastructure teams (Prometheus paradigm)
- Single observation per navigation = efficient storage

## Why rejected

| Reason | Impact | Why it matters |
|--------|--------|---|
| **No per-observation context** | Can't answer: "which screen had the slowest load?" | Histograms are aggregates; individual screen context lost |
| **Flush complexity** | Metrics need `PeriodicMetricReaderFactory` + separate export schedule | Spans flush with logs; simpler pipeline |
| **Session correlation** | Can't link individual slow load to user session + errors on that screen | Breaks analytics: "user had slow page, then error, then churn" |
| **Android parity loss** | Android emits spans; web would diverge | Team maintains two semantics (breaks cross-platform consistency) |
| **ClickHouse inefficiency** | Histograms require `GroupArray(value)` for distribution queries; span table is indexed on `ScreenName` already | Queries slower; re-aggregation penalty |
| **Feature gate mismatch** | Metrics gating separate from trace gating in OTel SDK | Consent/feature-flag logic duplicates |

## Decision

**Spans (PLAN-B)** keeps:
- Full per-event context (which screen, which user, which session)
- Android parity (same semconv)
- Simpler flush (coexist with session lifecycle)
- Queryable context ("slow loads on checkout" = direct ClickHouse query)
- One feature gate + one consent flow

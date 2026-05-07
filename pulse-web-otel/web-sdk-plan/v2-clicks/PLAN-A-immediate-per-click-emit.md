# Plan A — immediate per-click OTLP emit (rejected)

## Idea

Emit one OTLP log on every `click` event with no buffering.

## Why rejected

- **Diverges from Android** `ClickEventBuffer`, which delays singleton taps until window eviction or flush and collapses rage sequences into **one** log with `click.is_rage` + `click.rage_count`.
- **Operator signal-to-noise:** rage grouping reduces duplicate rows for the same frustration burst.
- Product contract already reserves semconv keys `click.is_rage` / `click.rage_count` for parity.

## When Plan A is still available

- Set `instrumentations.clicks.rage.enabled: false` for emergency compatibility; implementation uses immediate emit path (no buffer timers).

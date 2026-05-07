# Research 01 — errors ecosystem and industry patterns

## Objective

Validate expected browser error instrumentation shape for product-facing observability dashboards.

## Findings

1. Browser fatal and async rejection signals are most commonly represented as log/error events (not spans) for crash/non-fatal dashboards.
2. Deduplication windows are standard to avoid noisy loops from hot render/timer failures.
3. `window.onerror` + `unhandledrejection` remain baseline browser hooks with broad support.
4. Cross-origin `"Script error."` events are typically skipped due to low diagnostic value.
5. Optional runtime context (battery/storage/memory) should be best-effort and omitted when unavailable.

## Implication for Pulse Web SDK

- Current architecture (log records + dedupe + optional runtime attrs) aligns with common RUM error patterns.
- Hardening priority is testability and contract stability, not signal-family redesign.


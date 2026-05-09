# Contract parity — Web vs Android (errors)

## Mapping

| Concern | Android | Web |
|---------|---------|-----|
| Fatal crash signal | `device.crash` log | `device.crash` log |
| Non-fatal signal | `non_fatal` log | `non_fatal` log |
| Manual non-fatal marker | `non_fatal.is_manual=true` | `non_fatal.is_manual=true` |
| Timestamp source | observed timestamp at emit | `Date.now()` at emit |
| Trace context link | context-aware log emit | `context.active()` in logger emit |
| Crash listener | uncaught exception handler | `window.error` listener |
| Promise failure | platform-specific non-fatal reporting | `unhandledrejection` listener |

## Expected divergence

1. Browser has no thread id/name parity fields; web uses filename/line/column where available. We intentionally do **not** emit synthetic `thread.name` / `thread.id` (see backlog in `ralph/sdk-error-platform-parity.md`).
2. Device-state attrs are browser API-dependent (`getBattery`, `storage.estimate`) and best-effort.
3. Blocking forceFlush semantics differ on page lifecycle boundaries.

## Parity check result (this rerun)

- Product-level signal semantics: **match**
- Contract keys used by UI filtering (`pulse.type`, exception attrs): **match**
- Known platform-limited differences are documented above.


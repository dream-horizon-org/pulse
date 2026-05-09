# Design — Error Instrumentation (Web SDK)

## Decision snapshot

Web error signals remain OTLP **logs**:
- `pulse.type=device.crash` for unhandled `window.error`
- `pulse.type=non_fatal` for `unhandledrejection` and manual `reportException`

No new signal family was introduced in this rerun. Focus was lifecycle verification and E2E hardening from design intent.

## Why this rerun

- Existing docs lived in one long markdown file and were not lifecycle-structured.
- Error E2E coverage lacked mandatory assertion floor in several cases.
- Gate-off reset pattern was missing for `js_crash`.
- Demo lacked easy UI triggers for string/undefined rejection and dedupe burst.

## Implemented hardening

- Added dedicated plan pack under `web-sdk-plan/v1-errors/`.
- Hardened `e2e/m3-errors.spec.ts` with:
  - exact `pulse.type`
  - finite numeric checks for crash line/column attributes
  - required `session.id` and `screen.name` checks on positive-path logs
  - gate-off test with seeded config + `otlp.reset()` pattern
  - consent-denied no-export path
- Added demo actions in `ErrorDemo.tsx` for:
  - string rejection
  - undefined rejection
  - dedupe burst
- Included error E2E in `e2e:web-sdk-gates`.

## Linked artifacts

- Decision: `ADR-errors.md`
- Canonical implementation spec: `PLAN-B-errors-log-signals.md`
- Platform parity: `04-contract-parity.md`
- Touchpoints: `03-touchpoints-matrix.md`


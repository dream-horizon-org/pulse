# ADR — Web SDK Error Instrumentation Lifecycle Rerun

## Status

Accepted

## Context

Error instrumentation already existed in:
- `src/instrumentations/errors.ts`
- `sdk.ts` manual paths (`reportException`, `reportDeviceCrash`)

But lifecycle documentation was fragmented and E2E contract assertions were inconsistent across scenarios.

## Decision

Keep the existing log-based signal model and harden surrounding lifecycle artifacts:

1. Preserve signal contract:
   - `device.crash` for unhandled JS errors
   - `non_fatal` for unhandled promise rejections and manual report
2. Keep instrumentation ownership in `InstrumentationRegistry` under `InstrumentationKeys.ERRORS` with `PulseFeature.JS_CRASH`.
3. Raise E2E floor for positive-path logs:
   - exact `pulse.type`
   - finite numeric attrs where relevant
   - truthy `session.id`
   - truthy `screen.name`
4. Add explicit gate-off test using seeded SDK config + reset-after-proof-of-life pattern.
5. Expand demo UI minimally to exercise common rejection and dedupe edge cases without raw `page.evaluate` dependence.

## Alternative analysis

- **No separate Plan A file required in this rerun.**
  - Reason: there was no credible architectural fork; implementation style and signal family were already decided and validated. Work focused on lifecycle re-validation and E2E hardening.

## Consequences

- Better regression protection for error telemetry contract.
- Demo is now a more realistic contract surface for common JS error edge cases.
- Gate script now enforces error scenarios in the default web-sdk gate run.


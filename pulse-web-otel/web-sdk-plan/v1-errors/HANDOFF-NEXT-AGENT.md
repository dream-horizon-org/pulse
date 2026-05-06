# Handoff — v1-errors

## Status

Completed rerun for lifecycle docs + E2E hardening.

## Done

- Created structured lifecycle docs (`01/02/03`, ADR, PLAN-B, DESIGN, parity, README).
- Hardened `m3-errors.spec.ts` to contract floor.
- Added demo edge-case triggers in `ErrorDemo.tsx`.
- Added error spec to `e2e:web-sdk-gates`.
- Aligned `.env.test` OTLP format with fixture JSON decode.

## Remaining follow-ups (optional)

1. Consider splitting `m3-errors.spec.ts` into smaller files if runtime grows too much in CI.
2. Consider adding a dedicated unit test for `InstrumentationRegistry` idempotency if install semantics change for errors.
3. If graph artifacts are introduced for `pulse-web-otel`, start syncing `graph-cache.md` against them.

## Resume prompt

Continue from `web-sdk-plan/v1-errors/README.md`. Re-run:
- `yarn test:run src/__tests__/m3.test.ts`
- `yarn workspace ecommerce-demo e2e:m3-errors`
- `yarn workspace ecommerce-demo e2e:web-sdk-gates`

Then append outcomes to `web-sdk-plan/agent-runtime/test-run-log.md`.


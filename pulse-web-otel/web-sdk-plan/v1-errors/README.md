# Web SDK Error Instrumentation (v1-errors)

This folder is the canonical lifecycle pack for Web SDK error instrumentation rerun.

## Reading order

1. `DESIGN.md`
2. `ADR-errors.md`
3. `PLAN-B-errors-log-signals.md`
4. `04-contract-parity.md`
5. `03-touchpoints-matrix.md`
6. Research docs (`01-*`, `02-*`)
7. `HANDOFF-NEXT-AGENT.md`

## Active plan

- **Chosen path:** `PLAN-B-errors-log-signals.md`
- **Rejected alternatives:** documented inside `ADR-errors.md` (no separate Plan A file required for this rerun).

## Gap matrix (rerun status)

| Area | Item | Status | Notes |
|------|------|--------|-------|
| Docs | Research industry (`01-*`) | DONE | Added in this rerun. |
| Docs | Research OTel + SDK (`02-*`) | DONE | Added in this rerun. |
| Docs | Touchpoints matrix | DONE | Added with SDK/demo/e2e/doc files. |
| Docs | ADR | DONE | Added; rationale + no-Plan-A note. |
| Docs | PLAN-B | DONE | Added; contract + lifecycle + test matrix. |
| Docs | DESIGN | DONE | Added as entrypoint summary. |
| Docs | Contract parity | DONE | Added Android parity map + divergence notes. |
| Docs | README | DONE | This file. |
| Docs | HANDOFF | DONE | Added with explicit next actions. |
| Code | `src/instrumentations/errors.ts` lifecycle/contract | DONE | Existing implementation verified in rerun. |
| Demo | Error scenario UI surfaces | PARTIAL -> DONE | Added string/undefined rejection and dedupe burst actions in `ErrorDemo.tsx`. |
| E2E | Assertion floor (`pulse.type`, finite numbers, `session.id`, `screen.name`) | PARTIAL -> DONE | Hardened in `e2e/m3-errors.spec.ts`. |
| E2E | Gate-off zero-export reset pattern | MISSING -> DONE | Added `js_crash` gate-off test with reset and seeded config. |
| E2E | Consent edge coverage for errors | PARTIAL -> DONE | Added DENIED consent test under error suite. |
| E2E | Gate script inclusion | MISSING -> DONE | Added `e2e/m3-errors.spec.ts` to `e2e:web-sdk-gates`. |
| E2E wiring | OTLP fixture-compatible format | PARTIAL -> DONE | Set `.env.test`: JSON + no compression. |


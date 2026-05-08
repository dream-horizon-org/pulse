**Triage:** complete

## Parent

[PRD](../PRD.md) · [Technical plan](../TECHNICAL-PLAN.md)

## What to build

Extend the **RCA agent payload contract** for **`mode: hybrid`** and refresh **prompt** copy so **`serverRank` / `rank` 1** semantics match **merged hybrid ordering** (actionable **2D+** first, **1D** flat tier after). Update **`serverRank` field description** away from “problematic_count order” if no longer accurate.

## Acceptance criteria

- [x] `RootCausePayloadSchema.mode` accepts **`hybrid`** (and remains backward compatible with stored payloads).
- [x] `pulse_ai/agents/rca/prompts.py` documents **hybrid** tier behavior and **rank** alignment with **`serverRank`** under the new policy.
- [x] Tests or schema validation updated so invalid literals fail fast (e.g. serializer / unit test on schema).

## Blocked by

- ~~`issues/02-backend-rca-unified-pipeline.md`~~ (**complete** — pick up Issue 04)

## User stories covered

PRD **8, 11, 34, 36** (aligned with PRD “Out of Scope”: only minimal prompt edits beyond ranking/mode).

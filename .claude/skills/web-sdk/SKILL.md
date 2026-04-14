---
name: web-sdk
description: >
  Load full context for Pulse Web SDK work. Use when building, reviewing, or verifying
  anything in pulse-web-otel/. Auto-triggers on: "web sdk", "pulse-web", "pulse web",
  or any mention of pulse-web-otel files.
argument-hint: "[task-description | phase-name | verify <file>]"
allowed-tools: Read Grep Glob
---

## Step 1 — Load stable context

!`cat web-sdk-plan/WEB-SDK-AGENT-CONTEXT.md`

## Step 2 — Load live milestone state

!`cat web-sdk-plan/v1/MILESTONES.md`

## Step 3 — Instructions

You now have the full context for most web SDK tasks. Follow these rules:

### If $ARGUMENTS describes a feature or task (e.g. "session provider", "errors instrumentation")
1. Identify which milestone it belongs to from MILESTONES.md above
2. Find the matching phase doc from the navigation table in WEB-SDK-AGENT-CONTEXT.md
3. Read the phase doc for the detailed spec and done criteria
4. Implement against the spec; put code in the file named in the phase doc

### If $ARGUMENTS is "verify <file>" or "verify <phase>"
1. Open the file Cursor produced (or glob the relevant files)
2. Open the matching phase doc
3. Check every item in the phase doc's "Done Criteria" section against the actual code
4. Report: PASS / FAIL per criterion, with exact line references for failures
5. If all pass, tick the corresponding exit criteria checkbox in `web-sdk-plan/v1/MILESTONES.md`

### If $ARGUMENTS is a phase name (e.g. "foundation", "errors", "interactions")
Open the corresponding phase doc and summarise what needs to be built and what the done criteria are.

### If no $ARGUMENTS
Summarise current milestone status from MILESTONES.md: which exit criteria are checked vs unchecked per milestone.

### Always
- Respect the data contract table in WEB-SDK-AGENT-CONTEXT.md — `pulse.type` values and required attributes are non-negotiable
- `platform = 'web'` on every signal
- Never load `pulse-web-sdk-plan.md` or `PLAN-OVERVIEW.md` — they are human planning docs
- Update MILESTONES.md checkboxes as exit criteria are verified — that is the live state

# INPUTS

A PRD lives under `/Users/sarthakagarwal/Desktop/Dream11/pulse/.scratch/rca-segment-signal-gate/` and work items under `/Users/sarthakagarwal/Desktop/Dream11/pulse/.scratch/rca-segment-signal-gate/issues/`. Read those (and `/Users/sarthakagarwal/Desktop/Dream11/pulse/.scratch/rca-segment-signal-gate/issues/progress.txt` for handoff) to understand scope and what is left.

Review recent git history to see what has already landed.

If there is nothing meaningful left to do, output `<promise>NO MORE TASKS</promise>`.

# EXPLORATION

Explore the repo as needed before editing. Do not assume file locations or conventions without checking.

# IMPLEMENTATION

Pick the **single** highest-priority issue or slice to work on—**you** judge priority, not file order.

Complete only that one item:

1. Implement the change in the codebase.
2. Run `pnpm ai-hero-cli internal lint` for lint errors.
3. Run `npx tsc --noEmit` for type errors. Expect some intentional type errors (markers for learners); fix only what your change requires.
4. Update the relevant files under `prd/` and `issues/` (checkboxes, decisions, links to PRD sections where useful).
5. Append a short dated note to `issues/progress.txt` for the next person.

If, while doing this, the PRD **and** the active issues for that effort are fully complete, output `<promise>COMPLETE</promise>`.

# FEEDBACK LOOPS

Re-run lint or typecheck after substantive edits if fixes might have introduced new issues.

# COMMIT

Make **one** git commit for this slice. The message must:

1. Capture key decisions.
2. Mention important files touched.
3. Note blockers or follow-ups for the next iteration.

# FINAL RULES

**Only one task / one feature / one issue slice per run.** Do not batch unrelated work.

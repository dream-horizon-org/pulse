---
name: pulse-prd-review
description: Use this skill when the user wants to review or validate an existing Pulse PRD against the execution framework, glossary, and folder conventions. Produces a structured findings report covering persona/layer correctness, metric reuse, hypothesis quality, dependency coverage, missing sections, front-matter compliance, and renderer-contract conformance. Triggers on phrases like "review this PRD", "validate this PRD", "audit PRD against framework", "does this PRD comply", "check PRD".
---

# Pulse — PRD Reviewer

Audit an existing Pulse PRD against the framework and folder rules. Output is a structured review with explicit pass / soft / blocking findings — no prose drafting, no rewriting.

## Before you start

Read these files to load context.

1. `product/CLAUDE.md` — folder rules, front-matter schema, renderer contract.
2. `product/frameworks/execution-framework.md` — personas, layers, progress matrix, glossary.
3. `product/prds/_template.md` — structural template to compare against.
4. The target PRD file the user named.

If the target PRD is unspecified, ask which file. Do not guess.

## Review checklist

Walk every item. For each, mark `✅ Pass`, `⚠️ Soft`, or `❌ Blocking`. Capture the specific line, section, or quoted phrase that triggered the finding.

### Front-matter

- [ ] Front-matter block is present at the top.
- [ ] `title`, `status`, `layer`, `persona`, `last-edited`, `owner` are all present.
- [ ] `layer` value is one of: detect / diagnose / quantify / resolve / predict / framework / meta.
- [ ] `persona` value is one of: tech / product / ux / all (with body justification if `all`).
- [ ] `status` is one of: draft / in-review / approved / in-execution / live.
- [ ] `last-edited` is `YYYY-MM-DD`.
- [ ] `tracker` is present if `status` is `approved`, `in-execution`, or `live`.

### Anchoring

- [ ] Personas explicitly named in § Persona(s) Affected. If front-matter says `all`, body has a reason.
- [ ] Execution layer(s) explicitly named. Body matches front-matter.
- [ ] PRD locates the work in the progress matrix (mentions which cell it fills or upgrades).

### Hypothesis

- [ ] Hypothesis section uses the standard shape: *"We believe **[behavior]** for **[persona]** at **[layer]** will move **[metric]** because **[reason]**."*
- [ ] The metric is from the glossary in `frameworks/execution-framework.md`. A custom metric is a Soft finding unless flagged in Open Questions for glossary review — otherwise Blocking.
- [ ] Behavior is observable, not philosophical.

### Metrics & success

- [ ] Success Metrics section reuses glossary metrics where applicable.
- [ ] Each metric has Source, Baseline, Target columns filled (or marked as Open Question).
- [ ] No metric invented inline that should obviously map to a glossary entry.

### Coverage

- [ ] Problem section has evidence (data point, customer quote, ticket reference, telemetry). "We think users are unhappy" is a Soft finding.
- [ ] Goals are concrete outcomes, not activities.
- [ ] Non-goals section exists and is non-empty.
- [ ] Dependencies cover all relevant areas: Backend / SDK / Pulse UI / Ingestion / AI Agent. If a clearly-touched area is missing, that is a Blocking finding for `in-review` PRDs.
- [ ] Out of Scope / Future Work present.

### Renderer contract

- [ ] Status emojis (when used) are exactly ✅ ⚡ ❌. Substitutes (🟢 🟡 🔴, 🚧, ✓, `[x]`, "Done") are a Blocking finding.
- [ ] Layer / Persona names use the exact capitalisation defined in the framework: Detect, Diagnose, Quantify, Resolve, Predict, Tech, Product, UX.
- [ ] Cross-doc links are relative (`../frameworks/...`, `../prds/...`), not absolute paths or repo URLs.

### Hygiene

- [ ] No duplicate definitions of personas, layers, or metrics — references point to the framework.
- [ ] No `TBD` / `???` outside the Open Questions section.
- [ ] No images, GIFs, or videos embedded — those break copy-paste.

## Output format

Print the review using this exact structure (replace bracketed placeholders, omit empty sections):

```
# PRD Review — <PRD title>

**File:** `<path>`
**Reviewed against:** product/CLAUDE.md, product/frameworks/execution-framework.md
**Reviewed on:** <YYYY-MM-DD>

## Verdict
<APPROVED | APPROVED WITH NITS | NEEDS CHANGES | BLOCKED>

## Blocking findings (❌)
- <finding> — <line/section reference>
  Recommendation: <action>

## Soft findings (⚠️)
- <finding> — <line/section reference>
  Recommendation: <action>

## Passing checks (✅)
- Front-matter
- Anchoring
- Hypothesis
- Metrics
- Coverage
- Renderer contract
- Hygiene

## Suggested next step
<one sentence>
```

## Anti-patterns to refuse

- Re-drafting sections of the PRD inside the review. The review reports findings; it does not rewrite. If the user wants edits, they should pass the findings back through `/pulse-prd-author` or edit manually.
- Soft-pedalling ❌ findings into ⚠️. The renderer contract and front-matter schema are non-negotiable.
- Approving a PRD with `status: approved` / `in-execution` / `live` that lacks a `tracker` — that's a Blocking finding.
- Auto-approving because "the doc looks fine." Walk the entire checklist before issuing a verdict.

## When done

Tell the user:

- The verdict.
- The single highest-leverage fix to make next.
- Whether the PRD is ready to advance status (e.g. `draft → in-review`).

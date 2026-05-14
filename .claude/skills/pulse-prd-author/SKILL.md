---
name: pulse-prd-author
description: Use this skill when the user wants to draft a new Pulse PRD from a feature brief, idea, or scoping conversation. Walks the user through locating the work in the execution-framework progress matrix, anchoring persona and execution layer, building a hypothesis using glossary metrics, and producing a draft PRD saved to product/prds/. Triggers on phrases like "write a PRD", "draft a PRD for X", "scope feature Y for Pulse", "create PRD for", "let's PRD this".
---

# Pulse — PRD Author

Guided authoring of a new Pulse PRD. This is a **brainstorm with checkpoints**, not autonomous drafting. Stop and confirm with the user at every step.

## Before you start

Read these files to load context. Do not skip — the rest of the skill assumes the rules they encode.

1. `product/CLAUDE.md` — folder rules, front-matter schema, renderer contract.
2. `product/frameworks/execution-framework.md` — personas, layers, progress matrix, glossary.
3. `product/prds/_template.md` — the structural template the output must follow.

If any of these files are missing, stop and tell the user before continuing.

## Workflow — five checkpoints

Walk these in order. Do not skip ahead. Do not write the PRD file until checkpoint 5.

### Step 1 — Locate

Ask: *what cell of the progress matrix does this work fill?*

- Read `frameworks/execution-framework.md` § Progress Matrix.
- Identify which `(Stage, Persona)` cells the work touches.
- Cross-check whether existing tools listed in those cells already cover the proposed work. If they do, surface this — the work might be an extension of an existing tool, not a new PRD.
- Confirm with the user before proceeding.

### Step 2 — Anchor

Lock down two things explicitly:

- **Persona(s) affected.** Tech / Product / UX. Reject "all" unless the user provides a specific reason. Default expectation: one or two personas with a primary.
- **Execution layer(s).** Detect / Diagnose / Quantify / Resolve / Predict. If the work spans layers, identify the dependency direction (e.g. "Diagnose tool needs a new Detect signal first").

### Step 3 — Hypothesise

Build the hypothesis using this exact shape:

> We believe **[behavior]** for **[persona]** at **[layer]** will move **[metric]** because **[reason]**.

Constraints:

- The metric must come from the glossary in `frameworks/execution-framework.md` § Metrics & Glossary. If the user proposes a new metric, push back: ask which existing metric is closest and why it doesn't fit. Only proceed with a new metric if the user has a clear answer — and flag it in Open Questions as requiring a glossary update.
- The behavior must be observable, not philosophical. "Reduce diagnose effort" is bad. "Drop MTTDx from 45 minutes to under 10 minutes for Product persona on funnel anomalies" is good.

### Step 4 — Scope

Pull from the user, in this order:

- **Problem** — with evidence (data point, customer quote, support ticket, usage telemetry). No hand-waving.
- **Goals** — concrete outcomes, not activities.
- **Non-goals** — what we explicitly will not do, and why.
- **Solution overview** — one paragraph, no implementation detail.
- **Dependencies** — by area: Backend / SDK / Ingestion / Pulse UI / AI Agent. Cross-check the codebase by reading the repo's root `CLAUDE.md` for layout, and surface anything the user missed.
- **Out of scope / future work** — what's deferred and the rough trigger to pick it up later.
- **Open questions** — the parts the user can't answer yet.

Push back if any of these are vague. "TBD" lands in Open Questions, not in Goals.

### Step 5 — Draft

Now produce the file:

1. Slugify the feature name into kebab-case: `<feature-slug>`.
2. Read `product/prds/_template.md`.
3. Fill the template using the information gathered. Preserve the template's section order and headings.
4. Add front-matter at the top per `product/CLAUDE.md` schema:
   - `title` — explicit.
   - `status: draft`
   - `layer:` and `persona:` from Step 2.
   - `last-edited:` today's date.
   - `owner:` — ask the user if not already known.
   - `tracker:` if available; otherwise leave blank and add to Open Questions.
   - `hero: flat`
5. Save to `product/prds/<feature-slug>.md`.
6. Show the user the saved path. List which sections still have `TBD` or open questions, so they know what to follow up on.

## Anti-patterns to refuse

- Writing the full PRD before reaching Step 5. Steps 1–4 are intake; only Step 5 produces the file.
- Inventing a new metric. Always reuse the glossary unless the user explicitly justifies adding one.
- Skipping the matrix locate step. "Where in the framework does this live?" is non-negotiable.
- Auto-completing fields the user hasn't given evidence for. Better to leave a `TBD` and list it in Open Questions.
- Substituting `[x]` / 🟢 / "Done" for the status emojis ✅ ⚡ ❌ defined in `product/CLAUDE.md`.

## When done

Tell the user:

- The path the PRD was saved to.
- The open questions that need follow-up.
- That `/pulse-prd-review` is the next step once Open Questions are resolved.

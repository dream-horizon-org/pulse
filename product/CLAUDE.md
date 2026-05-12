---
title: Pulse Product Folder — Authoring Rules
status: live
layer: meta
last-edited: 2026-05-08
owner: Pulse Product Team
hero: none
---

# Pulse Product Folder — Authoring Rules

Scope: every `.md` file under `product/` (frameworks, PRDs, overview docs).

These rules constrain what you write so the web panel renders cleanly, the framework stays a single source of truth, and the docs survive copy-paste into Google Docs / Notion / decks.

These rules are forward-compatible. The current web panel renders standard Markdown; richer visual treatments will activate as the renderer evolves. Following the rules now means we don't retrofit later.

## What lives where

| Folder | Use for | Filename pattern |
|---|---|---|
| `frameworks/` | Long-lived strategic documents — execution framework, north star, persona definitions, glossary. Touched rarely. | `<noun-phrase>.md` (e.g. `execution-framework.md`) |
| `prds/` | One file per feature or capability. New work starts here. | `<feature-slug>.md` (kebab-case) |
| `prds/_template.md` | Authoring template. Copy this for new PRDs. Files starting with `_` sort to the bottom of the panel. | — |
| (root) | Folder-level overview docs only (`README.md`). Not for product content. | — |

Filenames are kebab-case (`funnel-diagnose-agent.md`) — not snake_case, not spaces, not Title Case. The filename becomes the URL slug in the web panel.

## Front-matter schema

Every doc starts with a YAML front-matter block. Most fields are optional, but the block must be present.

```yaml
---
title: Required. Explicit title.
status: draft | in-review | approved | in-execution | live
layer: detect | diagnose | quantify | resolve | predict | framework | meta
persona: tech | product | ux | all
last-edited: YYYY-MM-DD
owner: Author name
tracker: Linear/Jira URL
hero: gradient | flat | none   # default: flat
---
```

Validation:

| Field | Required when | Notes |
|---|---|---|
| `title` | Always | Renderer falls back to first H1 if absent, but always set this explicitly. |
| `layer` | PRDs | Use `framework` for framework docs, `meta` for overview docs. |
| `persona` | PRDs | `all` only with explicit body justification. |
| `status` | All | Frameworks are typically `live`. PRDs progress draft → in-review → approved → in-execution → live. |
| `last-edited` | All | `YYYY-MM-DD`. Update on every meaningful edit. |
| `owner` | All | Single name; the person on point. |
| `tracker` | `approved`, `in-execution`, `live` PRDs | Linear or Jira URL. |
| `hero` | Optional | Defaults to `flat`. Use `gradient` for hero docs. |

Status progression:

| Status | Meaning |
|---|---|
| `draft` | PRD is being written. Not yet ready for review. |
| `in-review` | Under review by EM, PM, tech leads. |
| `approved` | Reviewed and accepted. Engineering can plan the work. |
| `in-execution` | Engineering is actively building. Tracker required. |
| `live` | Shipped to customers, or — for framework / meta docs — currently in force. |

If a viewer doesn't strip front-matter, it appears as a horizontal rule plus a few `key: value` lines at the top — visually quiet, easy to delete on paste.

## Renderer contract

These patterns get rich visual treatment in the web panel. Stay on these patterns or the visuals break silently.

### Status symbols

Use **only** these three symbols when conveying build status:

| Symbol | Meaning |
|---|---|
| ✅ | Built / shipped |
| ⚡ | Work in progress |
| ❌ | Not built |

Do not substitute 🟢 🟡 🔴, 🚧, ✓, `[x]`, "Done", "WIP". The renderer keys off these three exact code points.

### Blockquotes

A `>` blockquote renders as a premium callout card. Use blockquotes for the doc's lede, target dates, key claims — not for casual asides.

### Tables

Standard pipe Markdown. The renderer auto-detects:

- A `Stage × Tech | Product | UX` table → renders as the colored progress matrix.
- Any column whose cells start with ✅ / ⚡ / ❌ → cells are styled as status chips.

### Layer and persona names

Always capitalised, exactly: `Detect`, `Diagnose`, `Quantify`, `Resolve`, `Predict`, `Tech`, `Product`, `UX`. The renderer scans for these exact tokens to apply pills.

### Glossary terms

Metric abbreviations (`TTFA`, `MTTD-R`, `MTTDx`, `TTFE`, `RRL`, `NRR`, etc.) are defined once in [`frameworks/execution-framework.md`](frameworks/execution-framework.md) § Metrics & Glossary. The renderer pulls long-form + meaning into a tooltip wherever the abbreviation appears.

**Do not redefine glossary metrics in PRDs.** If you need a metric the glossary doesn't have, add it to the glossary in the same change — don't fork it inline.

## Cross-doc linking

Relative paths from the doc you're writing:

- From a PRD to the framework: `[Layer & Persona reference](../frameworks/execution-framework.md)`
- From the framework to a PRD: `[Funnel diagnose agent](../prds/funnel-diagnose-agent.md)`

Do not link with absolute paths or repo URLs — docs must read correctly when copied out of the repo.

## When you are tempted to invent

| Temptation | Do this instead |
|---|---|
| Define a new metric inline in a PRD | Add it to `frameworks/execution-framework.md` § Metrics & Glossary first; PRD references the glossary entry. |
| Add a new persona ("Ops", "QA", "Customer Support") | Discuss with the author of the framework. New personas change strategy, not just one PRD. |
| Add a new execution layer | Same — framework change, not PRD change. |
| Use a different status emoji set | No. Use ✅ ⚡ ❌. |
| Skip the Persona section because "this affects everyone" | Either name the personas explicitly, or set `persona: all` and justify in the body. |
| Embed an image, GIF, or video | Avoid in PRDs — they don't survive copy-paste into Google Docs / decks. Diagrams: use a Markdown table or an ASCII flow. |

## Workflow shortcuts

The pulse repo ships two project-scoped skills for working in this folder:

- **`/pulse-prd-author`** — guided draft of a new PRD from a feature brief or scoping conversation.
- **`/pulse-prd-review`** — validate an existing PRD against this folder's rules and the execution framework.

Both skills load these rules and the framework doc on startup. They are the recommended path for AI-assisted authoring and review.

## Don't pollute this folder

Things that do **not** belong in `product/`:

- Engineering specs without a product surface — they live closer to the code.
- Meeting notes — use the team's notes tool.
- Customer-facing copy — that lives with the website / docs site.
- Screenshots of dashboards — link to the dashboard, don't paste images.

The product folder is for the strategic and product-spec layer. Keep it dense.

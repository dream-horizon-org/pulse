---
title: Pulse — Product Folder
status: live
layer: meta
last-edited: 2026-05-08
owner: Pulse Product Team
hero: flat
---

# Pulse — Product Folder

Source of truth for Pulse product strategy, frameworks, and PRDs.

## What lives here

| Folder | Contents |
|---|---|
| `frameworks/` | Strategic, long-lived guides — the execution framework, north star, persona definitions. Read these first. |
| `prds/` | Per-feature Product Requirement Documents. New work starts here. |
| `web-panel/` | A standalone React reader for browsing and searching this folder. Run it locally — see `web-panel/README.md`. |

## Conventions

- **Markdown only.** Every doc is plain `.md` so it can be copy-pasted into Google Docs, Notion, Slack, decks, or any other surface without conversion.
- **Self-contained.** A doc must read cleanly on its own — don't rely on the web panel to provide context.
- **Filename = slug.** Use kebab-case: `funnel-diagnose-agent.md`, not `Funnel Diagnose Agent.md`.
- **Reference, don't duplicate.** Personas, layers, and metrics are defined once in `frameworks/execution-framework.md`. Link to that — don't redefine them in every PRD.

## How to write a PRD

1. Copy `prds/_template.md` to a new file: `prds/<your-feature-slug>.md`.
2. Fill in the sections. Delete rows or tables that don't apply, but keep the section headings so the structure is predictable.
3. Anchor every PRD against the framework: name the persona, name the layer, reuse framework metrics.
4. Open a PR. Reviewers should be the EM, the PM, and at least one tech lead.

## How to read this folder

- **Web panel** (`web-panel/`) — best for browsing, searching, and presenting. `cd web-panel && npm install && npm run dev`.
- **Directly in your editor** — every file is plain Markdown. Tables and headings render cleanly in any viewer.
- **Copy-paste** — grab any file's contents and drop it into Google Docs, Notion, or a deck. The structure survives the trip.

## Related

- Engineering rules and code conventions: `../CLAUDE.md`, `../.claude/rules/`
- Customer-facing site, SDK reference docs: `../docs/`
- This folder is product-focused. Engineering specs that don't have a product surface should live closer to the code.

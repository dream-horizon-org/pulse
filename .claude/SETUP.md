# Claude Code Setup — Pulse

This document tracks what was set up, why each decision was made, and how new team members onboard.

---

## What Was Built

### Branch
`chore/claude-code-setup` — migrated from `.cursor/` to `.claude/` as part of moving the Pulse team to Claude Enterprise.

### File Tree

```
.claude/
├── CLAUDE.md                          # Root instructions (always loaded)
├── settings.json                      # Team-shared settings (committed)
├── settings.local.json.example        # Template for personal overrides (gitignored)
├── SETUP.md                           # This file
├── rules/                             # Path-scoped rules (loaded per file type)
│   ├── java-backend.md                # backend/**/*.java
│   ├── react-frontend.md              # pulse-ui/**/*.{ts,tsx}
│   ├── react-testing.md               # pulse-ui/**/*.{test,spec}.{ts,tsx}
│   ├── clickhouse-sql.md              # **/*.sql, backend/ingestion/**
│   ├── docker-deploy.md               # deploy/**
│   ├── python-ai-agent.md             # pulse_ai/**/*.py
│   ├── android-sdk.md                 # pulse-android-otel/**/*.kt
│   ├── react-native-sdk.md            # pulse-react-native-otel/**/*.{ts,tsx}
│   └── alerts-cron.md                 # backend/pulse-alerts-cron/**/*.java
├── agents/                            # Specialized AI personas
│   ├── backend-engineer.md
│   ├── frontend-engineer.md
│   ├── devops-engineer.md
│   ├── data-analyst.md
│   ├── debugger.md
│   ├── mobile-sdk-engineer.md
│   ├── ai-agent-engineer.md
│   └── pr-reviewer.md
├── skills/                            # Invocable workflows (/skill-name)
│   ├── build-backend/SKILL.md         # /build-backend
│   ├── build-ui/SKILL.md              # /build-ui
│   ├── run-backend-tests/SKILL.md     # /run-backend-tests
│   ├── check-services/SKILL.md        # /check-services
│   ├── view-logs/SKILL.md             # /view-logs [service]
│   ├── query-clickhouse/SKILL.md      # /query-clickhouse <SQL>
│   ├── review-my-changes/SKILL.md     # /review-my-changes
│   ├── add-api-endpoint/SKILL.md      # /add-api-endpoint <domain>
│   └── deploy-service/SKILL.md        # /deploy-service [target]
├── hooks/                             # Safety hooks (auto-run)
│   ├── block-destructive.sh           # Blocks rm -rf, force push, reset-databases, etc.
│   └── block-secrets.sh               # Blocks committing .env files
└── memory/                            # Auto-saved learnings (gitignored, local)
```

---

## Key Design Decisions

### 1. Path-Scoped Rules (not always-loaded)
All rules in `.claude/rules/` use `paths:` frontmatter. This means they only load when Claude opens a matching file, saving significant context per session.

**Before:** All 9 rule files loaded every session (~50KB context)
**After:** Only the rules relevant to the files being touched load

### 2. CLAUDE.md Under 150 Lines
Root `CLAUDE.md` is kept concise and references the rules files. Full detail lives in the per-language rules, which load on demand.

### 3. `mcp.toolSearch: true`
With 10+ MCP servers configured, deferred tool loading saves ~100KB+ of context per session. Tool definitions load only when Claude actually needs a specific tool.

### 4. `defaultMode: acceptEdits`
Claude auto-approves file edits without prompting. Keeps flow fast for the whole team. Destructive operations (rm -rf, force push, etc.) are still blocked by hooks.

### 5. Safety Hooks
Two hooks run before every Bash command:
- `block-destructive.sh` — blocks `rm -rf`, force push, `reset-databases`, DROP/TRUNCATE
- `block-secrets.sh` — blocks committing `.env` files, force-pushing to main

### 6. Agents Auto-Select
Agents have descriptive `description:` fields so Claude picks the right specialist automatically. Working on `backend/`? The backend-engineer agent activates. Working on `pulse-ui/`? Frontend-engineer activates.

### 7. skills/ replaces .cursor/commands/ + .cursor/skills/
All Cursor slash commands and multi-step skills are ported to `.claude/skills/`. Format is consistent: each skill is a `SKILL.md` file in its own directory.

---

## What's Gitignored

```
.claude/settings.local.json    # Personal machine overrides
.claude/memory/                # Auto-saved learnings (local to each developer)
```

---

## New Team Member Onboarding

1. Install Claude Code: `npm install -g @anthropic-ai/claude-code`
2. Clone the repo — `.claude/` is already committed
3. Copy personal settings template:
   ```bash
   cp .claude/settings.local.json.example .claude/settings.local.json
   ```
4. Edit `settings.local.json` to exclude rules from areas you don't work in (saves context)
5. Start Claude in the repo root: `claude`
6. Run `/check-services` to verify your local stack is healthy

## Common Slash Commands

| Command | What it does |
|---------|--------------|
| `/build-backend` | `mvn clean install` + reports results |
| `/build-ui` | `yarn build && yarn lint` |
| `/run-backend-tests` | `mvn verify` + coverage report |
| `/check-services` | Health check all Docker services |
| `/view-logs server` | Tail pulse-server logs |
| `/query-clickhouse <SQL>` | Run a SELECT against local ClickHouse |
| `/review-my-changes` | Code review current uncommitted diff |
| `/add-api-endpoint <domain>` | Scaffold a complete new endpoint |
| `/deploy-service [target]` | Build + restart a service |

---

## Source: Migrated From

All content was ported from `.cursor/` (Cursor AI config):

| Source | Destination |
|--------|-------------|
| `.cursor/rules/pulse-architecture.mdc` + `monorepo-awareness.mdc` + `commit-conventions.mdc` + `pr-workflow.mdc` | `.claude/CLAUDE.md` |
| `.cursor/rules/*.mdc` (9 files) | `.claude/rules/*.md` (paths: frontmatter added) |
| `.cursor/agents/*.md` (10 files) | `.claude/agents/*.md` (YAML frontmatter added) |
| `.cursor/commands/*.md` + `.cursor/skills/*/SKILL.md` | `.claude/skills/*/SKILL.md` |
| `.cursor/hooks/block-destructive.sh` + `block-secrets-in-commands.sh` | `.claude/hooks/` (ported to Claude Code hook format) |
| `.cursor/mcp.json` | Referenced via claude.ai MCP connectors (Atlassian, GitHub, etc.) |

---

## Context Optimization Summary

| Technique | Where applied | Token saving |
|-----------|--------------|--------------|
| Path-scoped rules | All 9 rule files | High — only load when touching matching files |
| Concise CLAUDE.md | Root CLAUDE.md | High — 120 lines vs 300+ |
| `mcp.toolSearch: true` | settings.json | High — deferred tool loading |
| `defaultMode: acceptEdits` | settings.json | Medium — fewer permission prompts |
| `settings.local.json.example` | claudeMdExcludes pattern | Medium — skip irrelevant service rules |

---

*Last updated: 2026-04-09 — branch `chore/claude-code-setup`*

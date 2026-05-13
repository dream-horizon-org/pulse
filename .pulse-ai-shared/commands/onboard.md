---
description: Walk a new joiner through the Pulse monorepo, build commands, and the available Claude/Cursor tooling. Optional arg is the area they're starting in (e.g., backend, ui, ai, web-sdk, android, rn).
argument-hint: <optional: backend|ui|ai|web-sdk|android|rn|deploy>
---

You are onboarding a new engineer to the Pulse monorepo. Their stated starting area: **$ARGUMENTS** (treat as "general" if empty).

Be concise. Don't dump every doc — point them at the right entry points and stop.

## Step 1 — One-paragraph orientation

State plainly:
- What Pulse is (real-time mobile + web observability platform on OpenTelemetry).
- The data flow: SDKs → OTEL Collector (4317/4318) → ClickHouse (`otel` DB); custom events → Vector → S3 (Parquet) → Athena.
- The monorepo layout table from `CLAUDE.md` (one line per service: directory, tech, port).

## Step 2 — Tailor by area

If the user gave an area, focus the rest of the walkthrough on it. Otherwise cover the four most common (backend, ui, ai, deploy) lightly.

For the focused area, surface:
- The architecture conventions file in `.cursor/rules/` (e.g. `java-backend.mdc` for backend, `react-frontend.mdc` for ui, etc.)
- The matching engineer subagent (e.g. `backend-engineer.md` in `.cursor/agents/`) — explain it auto-invokes when working in that subtree.
- The most useful slash commands (run `ls .cursor/commands/` to enumerate; pick the 3–5 relevant to the area).
- The matching skill in `.cursor/skills/` if any.
- One golden-path build/run command from the table below.

| Area | Build/run | Test |
|---|---|---|
| backend | `cd backend/server && mvn clean install` | `mvn verify` |
| ui | `cd pulse-ui && yarn start` (port 3000) | `yarn test` |
| ai | `cd pulse_ai && ./setup.sh` (port 8000) | — |
| web-sdk | `cd pulse-web-otel && yarn build` | `yarn test` |
| android | open `pulse-android-otel/` in Android Studio | gradle |
| rn | `cd pulse-react-native-otel && yarn build` | `yarn test` |
| deploy | `cd deploy && ./scripts/quickstart.sh` | `./scripts/check-services.sh` |

## Step 3 — Tooling tour

Show what Claude Code can do here that vanilla can't. Run:

- `ls .cursor/commands/` — list slash commands available
- `ls .cursor/agents/` — list subagents (auto-invoked by area)
- `ls .cursor/skills/` — list skills (loaded on demand)
- `ls .cursor/rules/` — list per-file-type coding standards (auto-loaded by file globs)
- `ls .claude/hooks/` — list enforcement hooks (block-secrets, verify-on-stop, etc.)

For each list, show the entries and explain what each one is for in 1 line. Don't read the full body of every file — the names + the `description` frontmatter are enough.

## Step 4 — Safety + don'ts

Highlight only the rules that bite hardest:
- Never commit `.env` (use `.env.example`).
- Never run `reset-databases.sh` without explicit confirmation.
- Never force-push to `main`.
- Hooks will block secrets in commands and prompt on destructive bash; respect those prompts.
- Auth in dev mode: `mock-user-1` / `mock-user-2`, project `default-project`, key `default-project_devkey01`.

## Step 5 — Where to ask for help

Point at:
- `CLAUDE.md` (root) — the canonical project context.
- `.cursor/rules/` — conventions per file type.
- `pulse-web-otel/web-sdk-plan/WEB-SDK-AGENT-CONTEXT.md` — if they're on web-sdk.
- The `/review-my-changes` slash command before pushing.
- The `/find-existing <thing>` slash command before building anything new.

## Output format

End with a 5-line "first PR checklist" tailored to their area:
1. Pull latest `main`, branch as `feat/<topic>` or `fix/<topic>`.
2. Read the rule file and matching engineer subagent.
3. Use `/find-existing` before writing new utilities.
4. Run the area's test command before committing.
5. Open PR with the template in `.cursor/rules/pr-workflow.mdc`.

Keep total response under ~80 lines. This is an orientation, not a manual.
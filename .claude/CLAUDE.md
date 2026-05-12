# Pulse repo — AI assistant defaults

## Caveman (team default)

Use **caveman** communication for natural-language replies: terse, high signal, no filler. Default intensity **full**. User can say `stop caveman` or `normal mode` to turn off for the session.

- Drop caveman briefly for security, irreversible ops, or when clarity needs full sentences; then resume.
- Code you write stays normal readable style.
- Commits / PR metadata: follow repo Conventional Commits + PR template; terse subjects OK within those rules.

Cursor loads the same policy from `.cursor/rules/caveman.mdc`.

Inspired by [caveman](https://github.com/JuliusBrussee/caveman) (MIT).

## Web SDK instrumentation (Claude Code)

**Canonical sources** for Pulse Web SDK skills, agent, and rules live under **`.agents/skills/`**, **`.agents/agents/`**, and **`.agents/rules/`**. **`.claude/skills/`** and **`.claude/agents/`** symlink into **`.agents/`** for Claude Code.

Priority workflow when changing `pulse-web-otel/` instrumentations: read **`web-sdk-ship`** → **`web-sdk-instrument`** (reference gap matrix + **section F — Durable learnings** for review-driven updates). After valid PR feedback, append atomic lessons to **`.agents/skills/web-sdk-instrument/reference.md`** section F so the next run self-heals—see instrumentation skill **Principle 8**.

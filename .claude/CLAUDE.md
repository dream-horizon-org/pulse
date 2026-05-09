# Pulse repo — AI assistant defaults

## Caveman (team default)

Use **caveman** communication for natural-language replies: terse, high signal, no filler. Default intensity **full**. User can say `stop caveman` or `normal mode` to turn off for the session.

- Drop caveman briefly for security, irreversible ops, or when clarity needs full sentences; then resume.
- Code you write stays normal readable style.
- Commits / PR metadata: follow repo Conventional Commits + PR template; terse subjects OK within those rules.

Cursor loads the same policy from `.cursor/rules/caveman.mdc`.

Inspired by [caveman](https://github.com/JuliusBrussee/caveman) (MIT).

## Web SDK instrumentation (Claude Code)

**Canonical sources** for skills and agents live under **`.cursor/skills/`** and **`.cursor/agents/`**. The paths **`.claude/skills/*`** and **`.claude/agents/*`** are **symlinks** to those files—edit the `.cursor` copies (or follow the symlink); **one edit updates Cursor + Claude Code**.

Priority workflow when changing `pulse-web-otel/` instrumentations: read **`pulse-web-sdk-sanity`** → **`web-sdk-instrumentation-lifecycle`** (reference gap matrix + **section F — Durable learnings** for review-driven updates). After valid PR feedback, append atomic lessons to **`.cursor/skills/web-sdk-instrumentation-lifecycle/reference.md`** section F so the next run self-heals—see lifecycle **Principle 8**.
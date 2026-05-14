# Shared agent artifacts (Pulse Web SDK)

Canonical copies for **`pulse-web-otel/`** live here:

| Path | Contents |
|------|----------|
| **`agents/`** | `pulse-web-sdk.md`, `web-otel-spec-audit-orchestrator.md` |
| **`skills/`** | `web-sdk-ship`, `web-sdk-instrument`, `web-sdk-e2e-matrix` (SPEC audit skills: `web-otel-spec-*` under **`.cursor/skills/`** only) |
| **`rules/`** | `pulse-web-otel-contract.mdc`, `pulse-web-otel-conventions.mdc` (+ legacy-name symlinks `web-sdk.mdc`, `pulse-web-otel.mdc`) |

**Cursor** loads rules/skills via symlinks under **`.cursor/rules/`**, **`.cursor/skills/`**, **`.cursor/agents/`** → **`.agents/`**.

**Claude Code** uses **`.claude/agents/`** and **`.claude/skills/`** → **`.agents/`**.

Other repo skills (e.g. `grill-me`, `pr-review`) remain under **`.cursor/skills/`**; cross-links from `.agents` use relative paths into `.cursor/skills/`.

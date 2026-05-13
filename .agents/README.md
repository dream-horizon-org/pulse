# Shared AI harness (`.agents/`)

Canonical copies for **Cursor** and **Claude Code** live here. **`.cursor/{agents,commands,rules,skills}/`** and **`.claude/{agents,commands,rules,skills}/`** are symlinks into this tree (except local-only files under `.claude/` that stay gitignored).

## Web SDK (`pulse-web-otel/`)

| Path | Contents |
|------|----------|
| **`agents/`** | `pulse-web-sdk.md` (plus all other sub-agent profiles in the same folder) |
| **`skills/`** | `web-sdk-ship`, `web-sdk-instrument`, `web-sdk-e2e-matrix`, `pulse-prd-author`, `pulse-prd-review`, and other repo-wide skills |
| **`rules/`** | `pulse-web-otel-contract.mdc`, `pulse-web-otel-conventions.mdc` (+ legacy-name symlinks `web-sdk.mdc`, `pulse-web-otel.mdc`) |

Legacy agent names `web-sdk-guardian` / `web-sdk-instrumentation-stage` are superseded by **`pulse-web-sdk`** (see frontmatter in `pulse-web-sdk.md`).

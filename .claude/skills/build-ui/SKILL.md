---
name: build-ui
description: Build and lint the React frontend. Use when asked to build or lint the UI.
allowed-tools: Bash(yarn *)
---

Run from the repo root:

```bash
cd pulse-ui && yarn build && yarn lint
```

Report:
- Build SUCCESS or any TypeScript errors
- ESLint violations (errors vs warnings)
- Bundle size summary if shown

If it fails, show the relevant error lines.

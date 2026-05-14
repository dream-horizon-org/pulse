# Pulse Web SDK — PRDs (`pulse-web-otel/prd/`)

All **product requirement documents** for `@dreamhorizonorg/pulse-web` live in this folder — not at `pulse-web-otel/PRD.md` as a single long-lived file name.

## Naming

- Use **kebab-case** + short scope: `web-sdk-documentation-consolidation.md`, `feature-xyz.md`.
- One PRD per major initiative; split work via `issues/` (Ralph) or GitHub issues.

## Ralph loop (`ralph/loop.sh`)

The harness expects a PRD path at **`$RALPH_WORK_DIR/PRD.md`** by default. Choose one:

1. **Symlink (recommended)** from the package root to the active PRD:

   ```bash
   cd pulse-web-otel
   ln -sf prd/your-feature.md PRD.md
   ```

2. **Explicit path** for a single run:

   ```bash
   PRD_PATH="$PWD/prd/your-feature.md" RALPH_WORK_DIR="$PWD" ./ralph/loop.sh
   ```

`PRD_PATH` overrides the default when set (see `ralph/loop.sh`).

## Index

| PRD file | Summary |
|----------|---------|
| [`web-sdk-documentation-consolidation.md`](web-sdk-documentation-consolidation.md) | Consolidate instrumentation knowledge into `docs/instrumentations/*/SPEC.md`; retire `web-sdk-plan/`. |

Add new rows here when you add PRDs.

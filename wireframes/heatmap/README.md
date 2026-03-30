# Heatmap wireframes (`frames.pen`)

**UI-aligned API contract (current `pulse-ui`):** [`HEATMAP_API_CONTRACT.md`](./HEATMAP_API_CONTRACT.md)

**UI-only spec:** [`HEATMAP_UI.md`](./HEATMAP_UI.md) — API contract, data schema, plotting logic, compare flow, routes, test scenarios.

**Full-stack (combined):** [`HEATMAP_INTEGRATED_SPEC.md`](./HEATMAP_INTEGRATED_SPEC.md) — Registry, Redis, ClickHouse, SDK, layered JSON, Pulse UI mapping.

Pencil file: **`frames.pen`** (same folder as this README).

Shared PNGs live in **`../common/assets/`** (relative to this folder), i.e. `pulse/wireframes/common/assets/`:

| File | Purpose |
|------|---------|
| `pulse-screen.png` | Source UI capture — edit this, then re-bake. |
| `pulse-screen-heatmap-composite.png` | Baked screenshot + heat (used by image fills). |

Image URLs inside the pen are **`../common/assets/...`** (relative to `frames.pen`).

Re-bake (from **`pulse` repo root**):

```bash
python3 wireframes/scripts/bake_heatmap_composite.py
```

Needs Python + Pillow + NumPy — see [`../scripts/README.md`](../scripts/README.md).

Commit **`frames.pen`** + **`../common/assets/*.png`** + wireframe scripts if changed.

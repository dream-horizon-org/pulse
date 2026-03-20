# Heatmap wireframes (`frames.pen`)

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

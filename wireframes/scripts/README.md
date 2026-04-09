# Wireframe tooling

## `bake_heatmap_composite.py`

Regenerates `../common/assets/pulse-screen-heatmap-composite.png` after you edit `../common/assets/pulse-screen.png`.

**Who has access:** Anyone with a clone of the `pulse` repo — this file is tracked in git like any other source.

**Setup (once per machine):**

```bash
cd pulse/wireframes/scripts
python3 -m venv .venv
source .venv/bin/activate   # Windows: .venv\Scripts\activate
pip install -r requirements-bake.txt
```

**Run** (from `pulse` repo root):

```bash
python3 wireframes/scripts/bake_heatmap_composite.py
```

Or from this folder:

```bash
python3 bake_heatmap_composite.py
```

No server or special permissions — only local Python + Pillow + NumPy.

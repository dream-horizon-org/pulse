#!/usr/bin/env python3
"""Bake screenshot + density-style heat overlay into a single PNG for Pencil.

Heat pockets use a radial color ramp (center = hot / red → mid = yellow → edge = blue),
with mixed pocket types: full ramp, warm-only, cool-only, and softer single-hue spots.

  python3 pulse/wireframes/scripts/bake_heatmap_composite.py
"""

from __future__ import annotations

import os
import sys

import numpy as np
from PIL import Image

# pulse/wireframes/scripts/this_file.py -> wireframes root
_SCRIPTS_DIR = os.path.dirname(os.path.abspath(__file__))
_WIREFRAMES_ROOT = os.path.dirname(_SCRIPTS_DIR)
ASSETS = os.path.join(_WIREFRAMES_ROOT, "common", "assets")
SRC = os.path.join(ASSETS, "pulse-screen.png")
OUT = os.path.join(ASSETS, "pulse-screen-heatmap-composite.png")

# Match hmScreenCapture rect in heatmap/frames.pen
W, H = 212, 228


def over_premul(top: np.ndarray, bot: np.ndarray) -> np.ndarray:
    """Premultiplied RGBA over. HxWx4."""
    ta = top[..., 3:4]
    return top + bot * (1.0 - ta)


def straight_to_premul(s: np.ndarray) -> np.ndarray:
    a = s[..., 3:4]
    rgb = s[..., :3] * a
    return np.concatenate([rgb, a], axis=-1)


def premul_to_straight(p: np.ndarray) -> np.ndarray:
    a = np.clip(p[..., 3:4], 1e-6, 1.0)
    rgb = p[..., :3] / a
    return np.concatenate([rgb, p[..., 3:4]], axis=-1)


def _interp_channel(t: np.ndarray, stops: list[float], vals: list[float]) -> np.ndarray:
    """Vectorized np.interp for 2D t."""
    flat = t.ravel()
    out = np.interp(flat, stops, vals)
    return out.reshape(t.shape)


def heat_rgb_ramp(t: np.ndarray, mode: str) -> np.ndarray:
    """
    t: normalized radius 0 = center (hottest), 1 = edge of pocket.
    Returns HxWx3 in [0,1].
    """
    t = np.clip(t, 0.0, 1.0)
    if mode == "full":
        # Red core → yellow ring → cyan/sky → deep blue edge (classic heatmap feel)
        r = _interp_channel(t, [0, 0.22, 0.42, 0.62, 0.82, 1.0], [0.96, 1.0, 0.98, 0.35, 0.12, 0.05])
        g = _interp_channel(t, [0, 0.22, 0.42, 0.62, 0.82, 1.0], [0.05, 0.35, 0.82, 0.78, 0.48, 0.22])
        b = _interp_channel(t, [0, 0.22, 0.42, 0.62, 0.82, 1.0], [0.08, 0.12, 0.18, 0.62, 0.88, 0.95])
    elif mode == "warm":
        # Everyone clustered: mostly red–orange–yellow, shallow cool edge
        r = _interp_channel(t, [0, 0.35, 0.65, 1.0], [0.95, 1.0, 0.92, 0.75])
        g = _interp_channel(t, [0, 0.35, 0.65, 1.0], [0.06, 0.55, 0.72, 0.55])
        b = _interp_channel(t, [0, 0.35, 0.65, 1.0], [0.08, 0.12, 0.22, 0.42])
    elif mode == "cool":
        # Low traffic: teal / cyan shades only (still radial shade variation)
        r = _interp_channel(t, [0, 0.45, 1.0], [0.12, 0.22, 0.35])
        g = _interp_channel(t, [0, 0.45, 1.0], [0.72, 0.88, 0.70])
        b = _interp_channel(t, [0, 0.45, 1.0], [0.68, 0.82, 0.58])
    elif mode == "amber":
        # Single warm family with shade steps (gold → rust)
        r = _interp_channel(t, [0, 0.5, 1.0], [0.92, 1.0, 0.82])
        g = _interp_channel(t, [0, 0.5, 1.0], [0.45, 0.72, 0.38])
        b = _interp_channel(t, [0, 0.5, 1.0], [0.08, 0.15, 0.12])
    else:
        raise ValueError(mode)
    return np.stack([r, g, b], axis=-1)


def radial_heat_pocket(
    h: int,
    w: int,
    cx: float,
    cy: float,
    rx: float,
    ry: float,
    peak_alpha: float,
    mode: str,
    *,
    d_power: float = 1.85,
    alpha_floor: float = 0.14,
    edge_soft: float = 1.08,
) -> np.ndarray:
    """
    Elliptical distance d: 0 at center, ~1 at nominal edge.
    Color follows t = clip(d,0,1). Alpha uses envelope so outer (blue) rings stay visible.
    """
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    d = np.sqrt(((xx - cx) / rx) ** 2 + ((yy - cy) / ry) ** 2)
    t = np.clip(d**d_power, 0.0, 1.0)
    rgb = heat_rgb_ramp(t, mode)

    # Envelope: strong in center, but don't crush alpha at edge (so yellow/blue rings read)
    env = np.clip((edge_soft - d) / edge_soft, 0.0, 1.0) ** 0.75
    bump = np.exp(-(d**2) / (2 * 0.42**2))
    a = peak_alpha * np.maximum(alpha_floor, 0.22 + 0.78 * bump) * (0.35 + 0.65 * env)
    a = np.clip(a, 0.0, 1.0)

    layer = np.zeros((h, w, 4), dtype=np.float32)
    layer[..., :3] = rgb * a[..., None]
    layer[..., 3] = a
    return layer


def gaussian_mono(
    h: int,
    w: int,
    cx: float,
    cy: float,
    rx: float,
    ry: float,
    rgb: tuple[float, float, float],
    peak_alpha: float,
    sharpness: float = 2.4,
) -> np.ndarray:
    """Simple single-hue pocket with shade via alpha falloff (sparse taps)."""
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    d2 = ((xx - cx) / rx) ** 2 + ((yy - cy) / ry) ** 2
    g = np.clip(np.exp(-d2), 0.0, 1.0) ** sharpness
    a = g * peak_alpha
    r, gb, b = rgb
    layer = np.zeros((h, w, 4), dtype=np.float32)
    layer[..., 0] = r * a
    layer[..., 1] = gb * a
    layer[..., 2] = b * a
    layer[..., 3] = a
    return layer


def main() -> int:
    if not os.path.isfile(SRC):
        print(f"Missing {SRC}", file=sys.stderr)
        return 1

    base = Image.open(SRC).convert("RGBA").resize((W, H), Image.Resampling.LANCZOS)
    base_p = straight_to_premul(np.asarray(base).astype(np.float32) / 255.0)

    acc = base_p

    # Mix: full red→yellow→blue ramps, warm-only clusters, cool pockets, one mono spot
    pockets = [
        # Primary — heavy overlap (full ramp)
        (106, 176, 34, 30, 0.58, "full", {}),
        # Tight cluster — warm-heavy (many taps same area)
        (60, 124, 22, 20, 0.42, "warm", {"d_power": 2.0}),
        # Secondary — full ramp
        (124, 102, 26, 22, 0.4, "full", {"edge_soft": 1.02}),
        # Low traffic — cool shades only
        (170, 46, 24, 20, 0.32, "cool", {}),
        # Amber family — warm shades, not full rainbow
        (184, 132, 18, 16, 0.28, "amber", {}),
        # Sparse — single-hue pocket
        (108, 38, 14, 12, 0.22, "mono", {"rgb": (0.2, 0.65, 0.58)}),
        # Lower left — full ramp
        (48, 188, 20, 18, 0.34, "full", {"alpha_floor": 0.12}),
    ]

    for cx, cy, rx, ry, peak, mode, kw in pockets:
        if mode == "mono":
            rgb = kw.get("rgb", (0.5, 0.5, 0.5))
            acc = over_premul(
                gaussian_mono(H, W, cx, cy, rx, ry, rgb, peak, sharpness=kw.get("sharpness", 2.5)),
                acc,
            )
        else:
            acc = over_premul(
                radial_heat_pocket(
                    H,
                    W,
                    cx,
                    cy,
                    rx,
                    ry,
                    peak,
                    mode,
                    d_power=kw.get("d_power", 1.85),
                    alpha_floor=kw.get("alpha_floor", 0.14),
                    edge_soft=kw.get("edge_soft", 1.08),
                ),
                acc,
            )

    out = premul_to_straight(acc)
    out_u8 = (np.clip(out, 0, 1) * 255).astype(np.uint8)
    Image.fromarray(out_u8, "RGBA").save(OUT, optimize=True)
    print(f"Wrote {OUT} ({W}x{H})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

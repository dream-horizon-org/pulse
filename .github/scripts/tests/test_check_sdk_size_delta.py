"""Unit tests for check_sdk_size_delta.py (U1–U9)."""

from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "check_sdk_size_delta.py"
THRESHOLD = 25600


def run_check(
    baseline: dict,
    measured: dict,
    *,
    baseline_update: bool = False,
    tmp_path: Path,
) -> subprocess.CompletedProcess[str]:
    baseline_path = tmp_path / "baseline.json"
    measured_path = tmp_path / "measured.json"
    baseline_path.write_text(json.dumps(baseline), encoding="utf-8")
    measured_path.write_text(json.dumps(measured), encoding="utf-8")
    cmd = [
        sys.executable,
        str(SCRIPT),
        "--baseline",
        str(baseline_path),
        "--measured",
        str(measured_path),
        "--platform",
        "test",
    ]
    if baseline_update:
        cmd.append("--baseline-update")
    return subprocess.run(cmd, capture_output=True, text=True, check=False)


def base_doc(bytes_val: int = 1_000_000) -> dict:
    return {
        "version": 1,
        "thresholdBytes": THRESHOLD,
        "artifacts": {
            "android.demo_app.debug_apk": {
                "bytes": bytes_val,
                "path": "x",
                "recordedAt": "2026-01-01",
                "gitSha": "abc",
            }
        },
    }


def test_u1_equal_measured(tmp_path: Path) -> None:
    """U1: measured = baseline → pass."""
    b = 1_000_000
    r = run_check(base_doc(b), {"android.demo_app.debug_apk": b}, tmp_path=tmp_path)
    assert r.returncode == 0


def test_u2_exactly_25kb_delta(tmp_path: Path) -> None:
    """U2: delta exactly 25 KB → pass."""
    b = 1_000_000
    r = run_check(base_doc(b), {"android.demo_app.debug_apk": b + THRESHOLD}, tmp_path=tmp_path)
    assert r.returncode == 0


def test_u3_over_25kb(tmp_path: Path) -> None:
    """U3: delta 25 KB + 1 → fail."""
    b = 1_000_000
    r = run_check(
        base_doc(b), {"android.demo_app.debug_apk": b + THRESHOLD + 1}, tmp_path=tmp_path
    )
    assert r.returncode == 1


def test_u4_shrink_ok(tmp_path: Path) -> None:
    """U4: shrink → pass."""
    b = 1_000_000
    r = run_check(base_doc(b), {"android.demo_app.debug_apk": b - 100}, tmp_path=tmp_path)
    assert r.returncode == 0


def test_u5_baseline_update_match(tmp_path: Path) -> None:
    """U5: over limit but baseline-update and bytes match → pass."""
    b = 1_000_000
    m = b + THRESHOLD + 1
    doc = base_doc(m)
    r = run_check(
        doc,
        {"android.demo_app.debug_apk": m},
        baseline_update=True,
        tmp_path=tmp_path,
    )
    assert r.returncode == 0


def test_u6_baseline_update_stale(tmp_path: Path) -> None:
    """U6: baseline-update but file still has old bytes → fail."""
    b = 1_000_000
    m = b + THRESHOLD + 1
    r = run_check(
        base_doc(b),
        {"android.demo_app.debug_apk": m},
        baseline_update=True,
        tmp_path=tmp_path,
    )
    assert r.returncode == 1


def test_u7_missing_measurement(tmp_path: Path) -> None:
    """U7: missing measured key → fail."""
    r = run_check(base_doc(), {}, tmp_path=tmp_path)
    assert r.returncode == 1


def test_u8_unseeded_baseline(tmp_path: Path) -> None:
    """Unseeded baseline (bytes 0) fails without baseline-update."""
    r = run_check(base_doc(0), {"android.demo_app.debug_apk": 100}, tmp_path=tmp_path)
    assert r.returncode == 1


def test_u9_custom_threshold(tmp_path: Path) -> None:
    """U9: custom thresholdBytes respected."""
    doc = base_doc(1000)
    doc["thresholdBytes"] = 50
    r = run_check(doc, {"android.demo_app.debug_apk": 1050}, tmp_path=tmp_path)
    assert r.returncode == 0
    r2 = run_check(doc, {"android.demo_app.debug_apk": 1051}, tmp_path=tmp_path)
    assert r2.returncode == 1

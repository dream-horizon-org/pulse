#!/usr/bin/env python3
"""Compare measured SDK artifact sizes against committed baselines."""

from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path


def load_json(path: Path) -> dict:
    with path.open(encoding="utf-8") as f:
        return json.load(f)


def main() -> int:
    parser = argparse.ArgumentParser(description="Check SDK size delta vs baseline")
    parser.add_argument("--baseline", type=Path, required=True)
    parser.add_argument("--measured", type=Path, required=True, help="JSON map artifact_key -> bytes")
    parser.add_argument("--head-sha", default="")
    parser.add_argument("--baseline-update", action="store_true")
    parser.add_argument("--platform", default="")
    args = parser.parse_args()

    baseline_doc = load_json(args.baseline)
    measured = load_json(args.measured)
    threshold = int(baseline_doc.get("thresholdBytes", 25600))
    artifacts = baseline_doc.get("artifacts", {})

    failures: list[str] = []
    rows: list[str] = [
        f"## SDK size delta ({args.platform or 'all'})",
    ]
    if args.head_sha:
        rows.append(f"**HEAD:** `{args.head_sha}`")
    rows.append(f"**Threshold:** {threshold} bytes (25 KB)")
    rows.append("")
    rows.append("| Artifact | Baseline | Measured | Delta | Status |")
    rows.append("|----------|----------|----------|-------|--------|")

    for key, meta in artifacts.items():
        if key not in measured:
            failures.append(f"Missing measurement for {key}")
            continue
        base_bytes = int(meta.get("bytes", 0))
        meas_bytes = int(measured[key])
        delta = meas_bytes - base_bytes
        if base_bytes == 0 and not args.baseline_update:
            failures.append(
                f"{key}: baseline bytes is 0 (unseeded). Run workflow_dispatch seed on main."
            )
            status = "UNSEEDED"
        elif delta <= threshold:
            status = "PASS"
        elif args.baseline_update and base_bytes == meas_bytes:
            status = "PASS (baseline-update)"
        else:
            status = "FAIL"
            failures.append(f"{key}: delta {delta} > {threshold} bytes")
        rows.append(
            f"| `{key}` | {base_bytes} | {meas_bytes} | {delta:+d} | {status} |"
        )

    summary = "\n".join(rows)
    print(summary)
    summary_file = os.environ.get("GITHUB_STEP_SUMMARY", "")
    if summary_file:
        with Path(summary_file).open("a", encoding="utf-8") as f:
            f.write(summary + "\n")

    if failures:
        for msg in failures:
            print(f"::error::{msg}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

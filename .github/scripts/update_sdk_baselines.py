#!/usr/bin/env python3
"""Update baseline JSON bytes from measured values."""

from __future__ import annotations

import argparse
import json
from datetime import date
from pathlib import Path


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--baseline", type=Path, required=True)
    parser.add_argument("--measured", type=Path, required=True)
    parser.add_argument("--git-sha", required=True)
    args = parser.parse_args()

    doc = json.loads(args.baseline.read_text(encoding="utf-8"))
    measured = json.loads(args.measured.read_text(encoding="utf-8"))
    today = date.today().isoformat()

    for key, meta in doc.get("artifacts", {}).items():
        if key in measured:
            meta["bytes"] = int(measured[key])
            meta["recordedAt"] = today
            meta["gitSha"] = args.git_sha

    args.baseline.write_text(json.dumps(doc, indent=2) + "\n", encoding="utf-8")
    print(f"Updated {args.baseline}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
"""Write auto_pass step summary when PR has no dependency manifest changes."""

from __future__ import annotations

import argparse
import os
from pathlib import Path


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--head-sha", required=True)
    parser.add_argument("--reason", default="no_dependency_changes")
    args = parser.parse_args()

    body = f"""## Mobile SDK size delta — passed (skipped)

| Field | Value |
|-------|--------|
| Status | passed (skipped — no dependency manifest changes) |
| HEAD sha | `{args.head_sha}` |
| Label `sdk-size-delta` | not required |
| Manifest scan | no changes in watched files |
| Build | skipped |
| Delta threshold | 25 KB (25,600 bytes) — not evaluated |
| Reason | {args.reason} |
"""
    print(body)
    summary_file = os.environ.get("GITHUB_STEP_SUMMARY", "")
    if summary_file:
        Path(summary_file).write_text(body, encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
"""
check_backend_coverage_overall.py
---------------------------------
JaCoCo XML overall coverage checker (no double counting). Writes a markdown
table to $GITHUB_STEP_SUMMARY and returns non-zero exit when thresholds fail.

Exit codes:
  0 = OK (all thresholds met)
  2 = Coverage below threshold
  3 = Report not found or unreadable
  4 = Invalid report format (no usable counters)
"""
import argparse
import os
import sys
from defusedxml import ElementTree as ET

VALID_TYPES = ("INSTRUCTION", "BRANCH", "LINE", "METHOD", "CLASS")

def pct(covered: int, missed: int) -> float:
    denom = covered + missed
    return (covered * 100.0 / denom) if denom else 100.0

def load_totals(report_path: str):
    """Return non-double-counted totals per metric.
    Strategy:
      1) If report-level counters exist (<report>/<counter>), use ONLY those.
      2) Else, sum ONLY package-level counters (<report>/<package>/<counter>).
    """
    try:
        tree = ET.parse(report_path)
        root = tree.getroot()
    except Exception as e:
        print(f"[ERROR] Failed to parse '{report_path}': {e}", file=sys.stderr)
        sys.exit(3)

    totals = {t: {"missed": 0, "covered": 0} for t in VALID_TYPES}

    # Prefer report-level counters
    report_level = root.findall("./counter")
    if report_level:
        for c in report_level:
            t = c.get("type")
            if t in totals:
                totals[t]["missed"] += int(c.get("missed", 0))
                totals[t]["covered"] += int(c.get("covered", 0))
        return totals

    # Fallback: sum package-level counters ONLY
    seen_any = False
    for pkg in root.findall("./package"):
        for c in pkg.findall("./counter"):
            t = c.get("type")
            if t in totals:
                seen_any = True
                totals[t]["missed"] += int(c.get("missed", 0))
                totals[t]["covered"] += int(c.get("covered", 0))

    if not report_level and not seen_any:
        print("[ERROR] No valid <counter> entries found in the JaCoCo report.", file=sys.stderr)
        sys.exit(4)

    return totals

def get_threshold(name: str, cli_value):
    # prefer CLI arg; else env var; else default 0
    if cli_value is not None:
        return float(cli_value)
    env_name = f"MIN_{name.upper()}"
    if env_name in os.environ and os.environ[env_name] != "":
        try:
            return float(os.environ[env_name])
        except ValueError:
            print(f"[WARN] Invalid {env_name} value '{os.environ[env_name]}', defaulting to 0")
    return 0.0

def write_summary(totals, percentages, thresholds, failures):
    path = os.environ.get("GITHUB_STEP_SUMMARY")
    if not path:
        return
    lines = []
    lines.append("### 📊 Code Coverage Report")
    lines.append("")
    lines.append("| Counter | Covered | Missed | % | Min % |")
    lines.append("|---|---:|---:|---:|---:|")
    for t in VALID_TYPES:
        cov = percentages[t]
        miss = totals[t]["missed"]
        covd = totals[t]["covered"]
        lines.append(f"| {t} | {covd} | {miss} | {cov:.2f}% | {thresholds[t]:.2f}% |")
    lines.append("")
    if failures:
        lines.append("❌ **Build Failed: Coverage below thresholds**")
        for msg in failures:
            lines.append(f"- {msg}")
    else:
        lines.append("✅ Coverage meets all configured thresholds")
    lines.append("")
    with open(path, "a") as f:
        f.write("\n".join(lines) + "\n")

def main():
    parser = argparse.ArgumentParser(description="Overall JaCoCo coverage thresholds + summary.")
    parser.add_argument("--report", required=True, help="Path to jacoco.xml")
    parser.add_argument("--min-instruction", type=float, default=None)
    parser.add_argument("--min-branch", type=float, default=None)
    parser.add_argument("--min-line", type=float, default=None)
    parser.add_argument("--min-method", type=float, default=None)
    parser.add_argument("--min-class", type=float, default=None)

    args = parser.parse_args()

    if not os.path.exists(args.report):
        print(f("[ERROR] Report file not found: {args.report}"), file=sys.stderr)
        sys.exit(3)

    totals = load_totals(args.report)
    percentages = {t: pct(totals[t]["covered"], totals[t]["missed"]) for t in VALID_TYPES}

    thresholds = {
        "INSTRUCTION": get_threshold("instruction", args.min_instruction),
        "BRANCH": get_threshold("branch", args.min_branch),
        "LINE": get_threshold("line", args.min_line),
        "METHOD": get_threshold("method", args.min_method),
        "CLASS": get_threshold("class", args.min_class),
    }

    print("== JaCoCo Coverage Summary ==")
    for t in VALID_TYPES:
        cov = percentages[t]
        miss = totals[t]["missed"]
        covd = totals[t]["covered"]
        print(f"{t:<11} covered={covd:>6} missed={miss:>6} pct={cov:6.2f}%  (min={thresholds[t]:.2f}%)")

    failures = []
    for t in VALID_TYPES:
        if thresholds[t] and (percentages[t] + 1e-9) < thresholds[t]:
            failures.append(f"{t} {percentages[t]:.2f}% < {thresholds[t]:.2f}%")

    write_summary(totals, percentages, thresholds, failures)

    if failures:
        print("\n[FAIL] Coverage below thresholds:")
        for msg in failures:
            print(" -", msg)
        sys.exit(2)

    print("\n[OK] All coverage thresholds met.")
    sys.exit(0)

if __name__ == "__main__":
    main()

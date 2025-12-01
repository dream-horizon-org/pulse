#!/usr/bin/env python3
import argparse, os, sys, xml.etree.ElementTree as ET

VALID_TYPES = {"INSTRUCTION", "BRANCH", "LINE", "METHOD", "CLASS"}

def pct(covered, missed):
    denom = covered + missed
    return 100.0 * covered / denom if denom else 100.0

def load_totals(report_path):
    try:
        tree = ET.parse(report_path)
        root = tree.getroot()
    except Exception as e:
        print(f"[ERROR] Failed to parse '{report_path}': {e}", file=sys.stderr)
        sys.exit(3)

    totals = {t: {"missed": 0, "covered": 0} for t in VALID_TYPES}
    seen_any = False
    for counter in root.iter("counter"):
        t = counter.attrib.get("type")
        if t in VALID_TYPES:
            seen_any = True
            totals[t]["missed"] += int(counter.attrib.get("missed", 0))
            totals[t]["covered"] += int(counter.attrib.get("covered", 0))
    if not seen_any:
        print("[ERROR] No valid <counter> entries found in the JaCoCo report.", file=sys.stderr)
        sys.exit(4)
    return totals

def main():
    ap = argparse.ArgumentParser(description="Check overall JaCoCo coverage thresholds.")
    ap.add_argument("--report", required=True, help="Path to jacoco.xml")
    ap.add_argument("--min-line", type=float, default=0.0)
    ap.add_argument("--min-branch", type=float, default=0.0)
    ap.add_argument("--min-instruction", type=float, default=0.0)
    ap.add_argument("--min-method", type=float, default=0.0)
    ap.add_argument("--min-class", type=float, default=0.0)
    args = ap.parse_args()

    totals = load_totals(args.report)
    perc = {t: pct(totals[t]["covered"], totals[t]["missed"]) for t in VALID_TYPES}

    print("== JaCoCo Coverage Summary ==")
    for t in sorted(VALID_TYPES):
        print(f"{t:<11} covered={totals[t]['covered']:>6} missed={totals[t]['missed']:>6} pct={perc[t]:6.2f}%  (min={getattr(args, 'min_'+t.lower()):.2f}%)")

    failures = []
    if args.min_line and perc["LINE"] < args.min_line:
        failures.append(f"LINE {perc['LINE']:.2f}% < {args.min_line:.2f}%")
    if args.min_branch and perc["BRANCH"] < args.min_branch:
        failures.append(f"BRANCH {perc['BRANCH']:.2f}% < {args.min_branch:.2f}%")
    if args.min_instruction and perc["INSTRUCTION"] < args.min_instruction:
        failures.append(f"INSTRUCTION {perc['INSTRUCTION']:.2f}% < {args.min_instruction:.2f}%")
    if args.min_method and perc["METHOD"] < args.min_method:
        failures.append(f"METHOD {perc['METHOD']:.2f}% < {args.min_method:.2f}%")
    if args.min_class and perc["CLASS"] < args.min_class:
        failures.append(f"CLASS {perc['CLASS']:.2f}% < {args.min_class:.2f}%")

    if failures:
        print("[FAIL] Coverage below thresholds:")
        for f in failures:
            print(" -", f)
        sys.exit(2)

        print("[OK] All coverage thresholds met.")
        sys.exit(0)

        if __name__ == "__main__":
            main()

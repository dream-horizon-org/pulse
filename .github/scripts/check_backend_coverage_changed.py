#!/usr/bin/env python3
"""
check_backend_coverage_changed.py
---------------------------------
Changed-files-only JaCoCo coverage (LINE/BRANCH/INSTRUCTION/METHOD/CLASS) that:
- Uses sourcefile counters for LINE & BRANCH (or derives from <line> mi/ci/mb/cb),
- Uses class counters for INSTRUCTION/METHOD/CLASS,
- Avoids double-counting by not mixing sourcefile + class LINE/BRANCH,
- Writes a markdown table to $GITHUB_STEP_SUMMARY,
- Enforces thresholds for all metrics (0 disables each).

Exit codes:
  0 = OK
  1 = Threshold failed (changed-files coverage)
  3 = Report not found or unreadable
  4 = No coverage data for changed files
"""
import argparse, os, sys
from defusedxml import ElementTree as ET
from typing import Dict, Tuple, List

METRICS = ("LINE", "BRANCH", "INSTRUCTION", "METHOD", "CLASS")

def parse_args():
    ap = argparse.ArgumentParser(description="Changed-files JaCoCo coverage (correct line counts + no double-count).")
    ap.add_argument("--report", required=True, help="Path to jacoco.xml")
    ap.add_argument("--changed-files", required=True, help="Path to file with newline-separated changed .java files")
    ap.add_argument("--src-root", required=True, help="Prefix to strip (e.g., backend/server/src/main/java)")
    ap.add_argument("--min-line", type=float, default=0.0)
    ap.add_argument("--min-branch", type=float, default=0.0)
    ap.add_argument("--min-instruction", type=float, default=0.0)
    ap.add_argument("--min-method", type=float, default=0.0)
    ap.add_argument("--min-class", type=float, default=0.0)
    ap.add_argument("--fail-if-no-changed", action="store_true")
    return ap.parse_args()

def pct(covered:int, missed:int) -> float:
    total = covered + missed
    return (covered * 100.0 / total) if total else 0.0

def load_report(path: str):
    try:
        tree = ET.parse(path)
        return tree.getroot()
    except Exception as e:
        print(f"[ERROR] Failed to parse '{path}': {e}", file=sys.stderr)
        sys.exit(3)

def build_indexes(root):
    pkg_to_sf: Dict[str, Dict[str, object]] = {}
    pkg_to_classes: Dict[str, List[object]] = {}
    for p in root.findall(".//package"):
        pname = p.get("name") or ""
        pkg_to_sf[pname] = {}
        pkg_to_classes[pname] = []
        for sf in p.findall("sourcefile"):
            sname = sf.get("name")
            if sname:
                pkg_to_sf[pname][sname] = sf
        for c in p.findall("class"):
            pkg_to_classes[pname].append(c)
    return pkg_to_sf, pkg_to_classes

def add(a: Tuple[int,int], b: Tuple[int,int]) -> Tuple[int,int]:
    return (a[0]+b[0], a[1]+b[1])

def sf_counter(sf, typ) -> Tuple[int,int]:
    for c in sf.findall("counter"):
        if c.get("type") == typ:
            return int(c.get("missed","0")), int(c.get("covered","0"))
    return 0,0

def sf_line_sums(sf) -> Tuple[int,int,int,int]:
    """Sum per-line instruction and branch counts to use as fallback for BRANCH,
       and as helper for deriving covered/missed LINES when needed."""
    mi=ci=mb=cb=0
    for ln in sf.findall("line"):
        mi += int(ln.get("mi","0"))
        ci += int(ln.get("ci","0"))
        mb += int(ln.get("mb","0"))
        cb += int(ln.get("cb","0"))
    return mi,ci,mb,cb

def sf_line_to_line_counts(sf) -> Tuple[int,int]:
    """Convert per-line mi/ci to line-counts:
       covered_line if ci>0; missed_line if (ci==0 and mi>0)."""
    covered = missed = 0
    for ln in sf.findall("line"):
        mi = int(ln.get("mi","0"))
        ci = int(ln.get("ci","0"))
        if ci > 0:
            covered += 1
        elif mi > 0:
            missed += 1
    return missed, covered

def cov_for_source(pkg: str, srcfile: str, pkg_to_sf, pkg_to_classes) -> Dict[str, Tuple[int,int]]:
    """Compute (missed,covered) per metric for a single source file without double-counts."""
    totals = {m:(0,0) for m in METRICS}

    def from_sourcefile(sf):
        # LINE: prefer counter; else derive from per-line mi/ci
        lm, lc = sf_counter(sf, "LINE")
        if lm == 0 and lc == 0:
            lm, lc = sf_line_to_line_counts(sf)
        totals["LINE"] = add(totals["LINE"], (lm, lc))

        # BRANCH: prefer counter; else sum per-line mb/cb
        bm, bc = sf_counter(sf, "BRANCH")
        if bm == 0 and bc == 0:
            _, _, mb, cb = sf_line_sums(sf)
            bm, bc = mb, cb
        totals["BRANCH"] = add(totals["BRANCH"], (bm, bc))

    def from_classes(package_name):
        # INSTRUCTION / METHOD / CLASS only from classes
        im = ic = mm = mc = cm = cc = 0
        for cls in pkg_to_classes.get(package_name, []):
            if cls.get("sourcefilename") == srcfile:
                for c in cls.findall("counter"):
                    typ = c.get("type")
                    if typ == "INSTRUCTION":
                        im += int(c.get("missed","0")); ic += int(c.get("covered","0"))
                    elif typ == "METHOD":
                        mm += int(c.get("missed","0")); mc += int(c.get("covered","0"))
                    elif typ == "CLASS":
                        cm += int(c.get("missed","0")); cc += int(c.get("covered","0"))
        if im or ic: totals["INSTRUCTION"] = add(totals["INSTRUCTION"], (im, ic))
        if mm or mc: totals["METHOD"]     = add(totals["METHOD"],     (mm, mc))
        if cm or cc: totals["CLASS"]      = add(totals["CLASS"],      (cm, cc))

    # 1) Exact package + sourcefile
    sf = pkg_to_sf.get(pkg, {}).get(srcfile)
    if sf is not None:
        from_sourcefile(sf)
        from_classes(pkg)
        if any(v != (0,0) for v in totals.values()):
            return totals

    # 2) Fallback: first package that has this sourcefile
    for p, sfs in pkg_to_sf.items():
        sf2 = sfs.get(srcfile)
        if sf2 is not None:
            from_sourcefile(sf2)
            from_classes(p)
            if any(v != (0,0) for v in totals.values()):
                return totals

    return totals  # zeros

def main():
    args = parse_args()
    src_root = args.src_root.rstrip("/") + "/"
    root = load_report(args.report)
    pkg_to_sf, pkg_to_classes = build_indexes(root)

    with open(args.changed_files, "r") as f:
        changed = [ln.strip() for ln in f if ln.strip()]

    totals = {m:(0,0) for m in METRICS}
    analyzed = 0

    for path in changed:
        if not path.endswith(".java"): continue
        if not path.startswith(src_root): continue
        rel = path[len(src_root):]
        parts = rel.split("/")
        if not parts: continue
        srcfile = parts[-1]
        pkg = "/".join(parts[:-1])

        per_file = cov_for_source(pkg, srcfile, pkg_to_sf, pkg_to_classes)
        if any(per_file[m] != (0,0) for m in METRICS):
            analyzed += 1
            for m in METRICS:
                totals[m] = add(totals[m], per_file[m])

        # debug line
        lm, lc = per_file["LINE"]; bm, bc = per_file["BRANCH"]
        im, ic = per_file["INSTRUCTION"]; mm, mc = per_file["METHOD"]; cm, cc = per_file["CLASS"]
        print(f"COVERAGE for {rel} -> LINE {lc}/{lm}, BR {bc}/{bm}, INS {ic}/{im}, METH {mc}/{mm}, CLASS {cc}/{cm}", file=sys.stderr)

    # Prepare summary
    path = os.environ.get("GITHUB_STEP_SUMMARY")
    lines = []
    lines.append("### 📊 Changed Files Coverage")
    lines.append("")
    lines.append("| Metric | % | Covered | Missed | Total | Min % |")
    lines.append("|---|---:|---:|---:|---:|---:|")

    thresholds = {
        "LINE": args.min_line,
        "BRANCH": args.min_branch,
        "INSTRUCTION": args.min_instruction,
        "METHOD": args.min_method,
        "CLASS": args.min_class,
    }

    failures = []
    has_data = False
    for m in METRICS:
        miss, cov = totals[m]
        total = miss + cov
        percent = pct(cov, miss) if total else 0.0
        if total > 0: has_data = True
        lines.append(f"| {m} | {percent:.2f}% | {cov} | {miss} | {total} | {thresholds[m]:.2f}% |")
        if thresholds[m] > 0 and total > 0 and (percent + 1e-9) < thresholds[m]:
            failures.append(f"{m} {percent:.2f}% < {thresholds[m]:.2f}%")

    if not has_data:
        note = "ℹ️ No coverage data available for changed files"
        if path:
            with open(path, "a") as f:
                f.write(note + "\n\n")
        print(note)
        sys.exit(0)

    lines.append("")
    if failures:
        lines.append("❌ **Build Failed: Changed-files coverage below thresholds**")
        for msg in failures:
            lines.append(f"- {msg}")
    else:
        lines.append("✅ Changed-files coverage meets configured thresholds")

    if path:
        with open(path, "a") as f:
            f.write("\n".join(lines) + "\n")
    print("\n".join(lines))

    if failures:
        sys.exit(1)
    sys.exit(0)

if __name__ == "__main__":
    main()

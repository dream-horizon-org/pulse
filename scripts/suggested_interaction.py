#!/usr/bin/env python3
"""
Event Interaction Mining
========================
Finds groups of events that fire in sequence after a user action.

Rules:
  - Each pair of consecutive events must be between MIN_EDGE_GAP and MAX_EDGE_GAP apart.
    Too close (< MIN_EDGE_GAP) = same code firing twice, collapse it.
    Too far (> MAX_EDGE_GAP) = different user action, split here.
  - Total chain must complete within MAX_CHAIN_SPAN seconds.
  - CV of total chain span across sessions must be < CHAIN_CV_MAX.
  - CV of each edge across sessions must be < EDGE_CV_MAX.
  - Pattern must appear in at least MIN_SESSIONS sessions.

Usage:
  python script.py --json-dir ~/data/day1 ~/data/day2
  python script.py --min-edge-gap 0.1 --max-edge-gap 1.0 --max-chain-span 3.0
  python script.py --min-sessions 5 --output results.json
"""
from __future__ import annotations

import argparse
import json
import math
import random
from collections import defaultdict
from pathlib import Path
from collections.abc import Sequence
from typing import Any, Iterator

import numpy as np
import pandas as pd

# ─── Data roots ───
JSON_DIRS: list[Path] = [
    Path("./data/vector_json/batch_28_data"),
    Path("./data/vector_json/batch_29_data"),
]

# ─── Interaction rules ───
MIN_EDGE_GAP = 0.1       # 100ms — events closer than this get collapsed
MAX_EDGE_GAP = 1.0       # 1s — events farther than this = different action
MAX_CHAIN_SPAN = 3.0     # 3s — total interaction can't exceed this
MIN_CHAIN_EVENTS = 2     # minimum events in an interaction
CASCADE_MIN_LEN = 2      # shortest pattern to look for
CASCADE_MAX_LEN = 6      # longest pattern to look for

# ─── Quality filters ───
MIN_SESSIONS = 10        # pattern must appear in at least this many sessions
CHAIN_CV_MAX = 0.2     # CV of total chain span must be under this
EDGE_CV_MAX = 0.3     # CV of each individual edge must be under this
TOP_N_PRINT = 30

# ─── Prop-key profiling ───
PROFILE_MAX_EVENTS = 1000
PROFILE_SAMPLE_SPREAD = True
MAX_DYNAMIC_PROP_KEYS = 1
MIN_KEY_PRESENCE_FRAC = 0.03
MAX_CARDINALITY_FRAC = 0.90
MAX_SAMPLE_VALUE_LEN = 200
DISTINCT_CAP = 2000
KEY_VALUE_MAX_LEN = 80
SESSION_CONSTANT_THRESHOLD = 0.85
MIN_VARYING_SESSION_FRAC = 0.10
MAX_DISTINCT_PER_SESSION_RATIO = 0.5
MAX_ABSOLUTE_DISTINCT = 50


# ═══════════════════════════════════════════════════════════════════════
# Utilities
# ═══════════════════════════════════════════════════════════════════════

def parse_props(raw: Any) -> dict[str, Any]:
    if isinstance(raw, dict):
        return raw
    if not raw:
        return {}
    if isinstance(raw, str):
        try:
            o = json.loads(raw)
            return o if isinstance(o, dict) else {}
        except json.JSONDecodeError:
            return {}
    return {}


def _short_key(s: str, lim: int = 100) -> str:
    return s if len(s) <= lim else s[: lim - 3] + "..."


# ═══════════════════════════════════════════════════════════════════════
# Data loading
# ═══════════════════════════════════════════════════════════════════════

def _iter_json_events(
    json_dirs: Sequence[Path],
) -> Iterator[tuple[Any, Any, Any, dict[str, Any]]]:
    for root in json_dirs:
        if not root.is_dir():
            continue
        for f in sorted(root.rglob("*.json")):
            if f.name == "all_events.json":
                continue
            for line in f.read_text(encoding="utf-8").splitlines():
                line = line.strip()
                if not line:
                    continue
                try:
                    o = json.loads(line)
                except json.JSONDecodeError:
                    continue
                sid = o.get("session_id")
                ts = o.get("timestamp")
                en = o.get("event_name")
                if not sid or ts is None:
                    continue
                yield sid, ts, en, parse_props(o.get("props"))


def _collect_all_json_files(json_dirs: Sequence[Path]) -> list[Path]:
    files: list[Path] = []
    for root in json_dirs:
        if root.is_dir():
            files.extend(
                f for f in sorted(root.rglob("*.json"))
                if f.name != "all_events.json"
            )
    return files


# ═══════════════════════════════════════════════════════════════════════
# Prop-key profiling (generic)
# ═══════════════════════════════════════════════════════════════════════

def _sample_events(
    json_dirs: Sequence[Path], max_events: int, spread: bool,
) -> list[tuple[str, dict[str, Any]]]:
    rows: list[tuple[str, dict[str, Any]]] = []
    if spread:
        files = _collect_all_json_files(json_dirs)
        if not files:
            return rows
        per_file = max(1, max_events // len(files))
        rng = random.Random(42)
        for fpath in files:
            if len(rows) >= max_events:
                break
            lines = fpath.read_text(encoding="utf-8").splitlines()
            sample = rng.sample(lines, min(per_file, len(lines)))
            for raw_line in sample:
                raw_line = raw_line.strip()
                if not raw_line:
                    continue
                try:
                    o = json.loads(raw_line)
                except json.JSONDecodeError:
                    continue
                sid = o.get("session_id")
                if not sid:
                    continue
                rows.append((str(sid), parse_props(o.get("props"))))
                if len(rows) >= max_events:
                    break
    else:
        for sid, _, _, props in _iter_json_events(json_dirs):
            rows.append((str(sid), props))
            if len(rows) >= max_events:
                break
    return rows


def profile_and_select_prop_keys(
    json_dirs: Sequence[Path],
    *,
    max_profile_events: int,
    max_keys: int,
    spread: bool = True,
) -> tuple[str, ...]:
    sampled = _sample_events(json_dirs, max_profile_events, spread)
    n_rows = len(sampled)
    if n_rows == 0:
        return ()

    presence: dict[str, int] = defaultdict(int)
    distinct: dict[str, set[str]] = defaultdict(set)
    too_long: set[str] = set()

    for _, props in sampled:
        for k, v in props.items():
            if not isinstance(k, str):
                continue
            if v is None or v == "":
                continue
            sv = str(v)
            if len(sv) > MAX_SAMPLE_VALUE_LEN:
                too_long.add(k)
                continue
            presence[k] += 1
            if len(distinct[k]) < DISTINCT_CAP:
                distinct[k].add(sv[:200])

    key_session_values: dict[str, dict[str, set[str]]] = defaultdict(
        lambda: defaultdict(set)
    )
    for sid, props in sampled:
        for k, v in props.items():
            if k in too_long or k not in presence:
                continue
            if v is None or v == "":
                continue
            sv = str(v)[:200]
            if len(key_session_values[k][sid]) < 50:
                key_session_values[k][sid].add(sv)

    key_varies_ratio: dict[str, float] = {}
    key_constant_ratio: dict[str, float] = {}
    for k, sess_vals in key_session_values.items():
        n_sessions = len(sess_vals)
        if n_sessions == 0:
            key_varies_ratio[k] = 0.0
            key_constant_ratio[k] = 1.0
            continue
        n_varying = sum(1 for vals in sess_vals.values() if len(vals) > 1)
        key_varies_ratio[k] = n_varying / n_sessions
        key_constant_ratio[k] = (n_sessions - n_varying) / n_sessions

    candidates: list[tuple[float, str, str]] = []
    for k, pr in presence.items():
        if k in too_long:
            continue
        frac = pr / n_rows
        if frac < MIN_KEY_PRESENCE_FRAC:
            continue
        d = len(distinct.get(k, ()))
        if pr > 0 and (d / pr) > MAX_CARDINALITY_FRAC:
            continue
        if d > MAX_ABSOLUTE_DISTINCT:
            continue
        n_sess_k = len(key_session_values.get(k, {}))
        if n_sess_k > 0 and (d / n_sess_k) > MAX_DISTINCT_PER_SESSION_RATIO:
            continue
        const_ratio = key_constant_ratio.get(k, 1.0)
        varies_ratio = key_varies_ratio.get(k, 0.0)
        if const_ratio >= SESSION_CONSTANT_THRESHOLD:
            continue
        score = pr * math.log(2.0 + float(min(d, DISTINCT_CAP)))
        classify = "varies"
        if varies_ratio >= MIN_VARYING_SESSION_FRAC:
            score *= (1.0 + 5.0 * varies_ratio)
        else:
            classify = "low-vary"
        candidates.append((score, k, classify))

    candidates.sort(key=lambda x: (-x[0], x[1]))

    all_sessions = set(sid for sid, _ in sampled)
    print(f"\n  Prop-key profiling ({n_rows} events, {len(all_sessions)} sessions)")
    print(f"  {'Key':<35} {'Present':>7} {'Distinct':>8} {'Const%':>7} {'Vary%':>7} {'Score':>9}")
    print(f"  {'-'*35} {'-'*7} {'-'*8} {'-'*7} {'-'*7} {'-'*9}")
    for score, k, _ in candidates[:15]:
        pr = presence[k]
        d = len(distinct.get(k, ()))
        cr = key_constant_ratio.get(k, 0.0)
        vr = key_varies_ratio.get(k, 0.0)
        print(f"  {k:<35} {pr:>7} {d:>8} {cr:>6.0%} {vr:>6.0%} {score:>9.1f}")

    # Dedup redundant keys
    pre_pick = [k for _, k, _ in candidates[:max_keys * 2]]
    co_values: dict[tuple[str, str], list[tuple[str, str]]] = {}
    for _, props in sampled:
        vals = {}
        for k in pre_pick:
            if k in props and props[k] is not None and props[k] != "":
                vals[k] = str(props[k])[:80]
        for k1 in vals:
            for k2 in vals:
                if k1 >= k2:
                    continue
                co_values.setdefault((k1, k2), []).append((vals[k1], vals[k2]))

    redundant: set[str] = set()
    for (k1, k2), pairs in co_values.items():
        if len(pairs) < 10:
            continue
        mapping: dict[str, set[str]] = defaultdict(set)
        for v1, v2 in pairs:
            mapping[v1].add(v2)
        if all(len(v2s) == 1 for v2s in mapping.values()):
            d1 = len(distinct.get(k1, ()))
            d2 = len(distinct.get(k2, ()))
            drop = k2 if d2 >= d1 else k1
            redundant.add(drop)

    final = [k for _, k, _ in candidates if k not in redundant][:max_keys]
    final.sort()
    return tuple(final)


# ═══════════════════════════════════════════════════════════════════════
# Event key building
# ═══════════════════════════════════════════════════════════════════════

def build_event_key(
    event_name: str | None, props: dict[str, Any], prop_keys: tuple[str, ...]
) -> str:
    en = str(event_name or "?")
    parts: list[str] = [en]
    for k in prop_keys:
        if k not in props:
            continue
        v = props[k]
        if v is None or v == "":
            continue
        s = str(v).replace("|", "/")
        if len(s) > KEY_VALUE_MAX_LEN:
            s = s[: KEY_VALUE_MAX_LEN - 3] + "..."
        parts.append(f"{k}={s}")
    return "|".join(parts)


def load_events(json_dirs: Sequence[Path], *, prop_keys: tuple[str, ...]) -> pd.DataFrame:
    rows: list[dict[str, Any]] = []
    for sid, ts, en, props in _iter_json_events(json_dirs):
        key = build_event_key(str(en) if en is not None else None, props, prop_keys)
        rows.append({
            "session_id": sid,
            "timestamp": pd.to_datetime(ts, utc=True),
            "event_name": str(en) if en is not None else "",
            "event_key": key,
        })
    return pd.DataFrame(rows).sort_values(["session_id", "timestamp"])


# ═══════════════════════════════════════════════════════════════════════
# Step 1: Collapse instant events & build clean chains
# ═══════════════════════════════════════════════════════════════════════

def build_session_chains(
    ts: list[Any],
    keys: list[str],
    min_edge_gap: float,
    max_edge_gap: float,
    max_chain_span: float,
    min_chain_events: int,
) -> list[tuple[list[Any], list[str]]]:
    """
    From one session's sorted events, produce clean interaction chains.

    Step 1: Collapse events closer than min_edge_gap (keep last in cluster).
    Step 2: Split on gaps > max_edge_gap (different user action).
    Step 3: Cap total span at max_chain_span.
    """
    m = len(ts)
    if m == 0:
        return []

    # --- Step 1: Collapse instant events ---
    clusters: list[list[int]] = [[0]]
    for i in range(1, m):
        gap = (ts[i] - ts[i - 1]).total_seconds()
        if gap < min_edge_gap:
            clusters[-1].append(i)
        else:
            clusters.append([i])

    # Keep last event from each cluster
    collapsed_ts: list[Any] = []
    collapsed_keys: list[str] = []
    for cluster in clusters:
        last = cluster[-1]
        collapsed_ts.append(ts[last])
        collapsed_keys.append(keys[last])

    # --- Step 2: Split on gaps > max_edge_gap ---
    if len(collapsed_ts) < 2:
        return []

    segments: list[tuple[list[Any], list[str]]] = []
    seg_ts: list[Any] = [collapsed_ts[0]]
    seg_keys: list[str] = [collapsed_keys[0]]

    for i in range(1, len(collapsed_ts)):
        gap = (collapsed_ts[i] - collapsed_ts[i - 1]).total_seconds()
        if gap > max_edge_gap:
            # End current segment, start new one
            if len(seg_ts) >= min_chain_events:
                segments.append((seg_ts, seg_keys))
            seg_ts = [collapsed_ts[i]]
            seg_keys = [collapsed_keys[i]]
        else:
            seg_ts.append(collapsed_ts[i])
            seg_keys.append(collapsed_keys[i])

    if len(seg_ts) >= min_chain_events:
        segments.append((seg_ts, seg_keys))

    # --- Step 3: Cap total span ---
    chains: list[tuple[list[Any], list[str]]] = []
    for seg_ts, seg_keys in segments:
        s = 0
        while s < len(seg_ts):
            e = s
            while e + 1 < len(seg_ts):
                total_span = (seg_ts[e + 1] - seg_ts[s]).total_seconds()
                if total_span > max_chain_span:
                    break
                e += 1
            chain_len = e - s + 1
            if chain_len >= min_chain_events:
                chains.append((seg_ts[s: e + 1], seg_keys[s: e + 1]))
            s = e + 1

    return chains


def extract_all_chains(
    df: pd.DataFrame,
    min_edge_gap: float,
    max_edge_gap: float,
    max_chain_span: float,
    min_chain_events: int,
) -> list[tuple[str, list[Any], list[str]]]:
    all_chains: list[tuple[str, list[Any], list[str]]] = []
    for sid, g in df.groupby("session_id", sort=False):
        g = g.sort_values("timestamp")
        for chain_ts, chain_keys in build_session_chains(
            g["timestamp"].tolist(),
            g["event_key"].tolist(),
            min_edge_gap, max_edge_gap, max_chain_span, min_chain_events,
        ):
            all_chains.append((str(sid), chain_ts, chain_keys))
    return all_chains


# ═══════════════════════════════════════════════════════════════════════
# Step 2: Extract n-grams and score
# ═══════════════════════════════════════════════════════════════════════

def discover_interactions(
    chains: list[tuple[str, list[Any], list[str]]],
    total_sessions: int,
    min_len: int,
    max_len: int,
    min_sessions: int,
    chain_cv_max: float,
    edge_cv_max: float,
) -> list[dict[str, Any]]:
    # Collect n-grams
    pat_spans: dict[tuple[str, ...], list[float]] = defaultdict(list)
    pat_sessions: dict[tuple[str, ...], set[str]] = defaultdict(set)
    pat_edge_gaps: dict[tuple[str, ...], dict[int, list[float]]] = defaultdict(
        lambda: defaultdict(list)
    )

    for sid, tsb, keysb in chains:
        m = len(keysb)
        for n in range(min_len, max_len + 1):
            if n > m:
                continue
            for i in range(m - n + 1):
                pat = tuple(keysb[i: i + n])
                total = (tsb[i + n - 1] - tsb[i]).total_seconds()
                pat_spans[pat].append(total)
                pat_sessions[pat].add(sid)
                for e in range(n - 1):
                    gap = (tsb[i + e + 1] - tsb[i + e]).total_seconds()
                    pat_edge_gaps[pat][e].append(gap)

    results: list[dict[str, Any]] = []
    for pat, spans in pat_spans.items():
        unique_sessions = len(pat_sessions[pat])
        if unique_sessions < min_sessions:
            continue

        arr = np.array(spans, dtype=float)
        mu = float(np.mean(arr))
        if mu < 0.001:
            continue

        sig = float(np.std(arr, ddof=1)) if len(arr) > 1 else 0.0
        cv = sig / mu if mu > 0 else float("inf")

        # Filter: chain CV
        if cv >= chain_cv_max:
            continue

        # Filter: every edge must pass edge CV
        edges: list[dict[str, Any]] = []
        all_edges_pass = True
        for e_idx in range(len(pat) - 1):
            gaps = pat_edge_gaps[pat][e_idx]
            e_arr = np.array(gaps, dtype=float)
            e_mu = float(np.mean(e_arr))
            e_sig = float(np.std(e_arr, ddof=1)) if len(e_arr) > 1 else 0.0
            e_cv = e_sig / e_mu if e_mu > 0 else 0.0
            e_median = float(np.median(e_arr))
            e_p5 = float(np.percentile(e_arr, 5))
            e_p95 = float(np.percentile(e_arr, 95))

            if e_cv >= edge_cv_max:
                all_edges_pass = False
                break

            edges.append({
                "from": pat[e_idx],
                "to": pat[e_idx + 1],
                "mean_gap_s": round(e_mu, 4),
                "median_gap_s": round(e_median, 4),
                "cv": round(e_cv, 4),
                "p5_s": round(e_p5, 4),
                "p95_s": round(e_p95, 4),
            })

        if not all_edges_pass:
            continue

        session_pct = round((unique_sessions / total_sessions) * 100, 2) if total_sessions > 0 else 0.0

        results.append({
            "pattern": list(pat),
            "length": len(pat),
            "total_occurrences": len(spans),
            "unique_sessions": unique_sessions,
            "session_pct": session_pct,
            "mean_span_s": round(mu, 4),
            "std_span_s": round(sig, 4),
            "cv": round(cv, 4),
            "min_span_s": round(float(np.min(arr)), 4),
            "max_span_s": round(float(np.max(arr)), 4),
            "median_span_s": round(float(np.median(arr)), 4),
            "p5_span_s": round(float(np.percentile(arr, 5)), 4),
            "p95_span_s": round(float(np.percentile(arr, 95)), 4),
            "edges": edges,
        })

    # Sort: session % desc, then CV asc
    results.sort(key=lambda r: (-r["session_pct"], r["cv"]))
    return results


# ═══════════════════════════════════════════════════════════════════════
# Deduplication
# ═══════════════════════════════════════════════════════════════════════

def remove_subpatterns(
    patterns: list[dict[str, Any]], *, keep_if_much_more_frequent: float = 3.0,
) -> list[dict[str, Any]]:
    pat_tuples = [tuple(p["pattern"]) for p in patterns]
    to_remove: set[int] = set()
    for i, short in enumerate(pat_tuples):
        if i in to_remove:
            continue
        for j, long in enumerate(pat_tuples):
            if i == j or j in to_remove or len(long) <= len(short):
                continue
            ls, ll = len(short), len(long)
            is_sub = any(long[k: k + ls] == short for k in range(ll - ls + 1))
            if is_sub:
                freq_ratio = patterns[i]["total_occurrences"] / max(patterns[j]["total_occurrences"], 1)
                if freq_ratio <= keep_if_much_more_frequent:
                    to_remove.add(i)
                    break
    return [p for idx, p in enumerate(patterns) if idx not in to_remove]


# ═══════════════════════════════════════════════════════════════════════
# Pipeline
# ═══════════════════════════════════════════════════════════════════════

def run_pipeline(df: pd.DataFrame, args: argparse.Namespace) -> list[dict[str, Any]]:
    total_sessions = int(df["session_id"].nunique())
    print(f"\nTotal sessions: {total_sessions}")

    # Step 1: Build clean chains
    chains = extract_all_chains(
        df, args.min_edge_gap, args.max_edge_gap,
        args.max_chain_span, MIN_CHAIN_EVENTS,
    )
    print(
        f"Chains extracted: {len(chains)}  "
        f"(edge gap: {args.min_edge_gap}s-{args.max_edge_gap}s, "
        f"max span: {args.max_chain_span}s)"
    )
    if not chains:
        print("No chains found. Try adjusting --min-edge-gap or --max-edge-gap.")
        return []

    sizes = [len(k) for _, _, k in chains]
    print(f"Chain sizes: mean={np.mean(sizes):.1f} median={np.median(sizes):.0f} "
          f"min={min(sizes)} max={max(sizes)}")

    # Step 2: Find patterns
    patterns = discover_interactions(
        chains, total_sessions,
        CASCADE_MIN_LEN, CASCADE_MAX_LEN,
        args.min_sessions, args.chain_cv_max, args.edge_cv_max,
    )
    print(
        f"Patterns passing all filters "
        f"(sessions>={args.min_sessions}, chain_cv<{args.chain_cv_max}, "
        f"edge_cv<{args.edge_cv_max}): {len(patterns)}"
    )

    # Step 3: Dedup
    before = len(patterns)
    patterns = remove_subpatterns(patterns)
    if before != len(patterns):
        print(f"After dedup: {len(patterns)} (removed {before - len(patterns)} sub-patterns)")

    # Print
    print(f"\n{'=' * 90}")
    print(f"INTERACTIONS (sorted by session volume %, then CV)")
    print(f"{'=' * 90}")

    for i, p in enumerate(patterns[:TOP_N_PRINT]):
        print(f"\n  #{i + 1}")
        print(f"  ├─ Sessions:     {p['unique_sessions']} / {total_sessions}  "
              f"({p['session_pct']}% of all sessions)")
        print(f"  ├─ Occurrences:  {p['total_occurrences']}")
        print(f"  ├─ Steps:        {p['length']}")
        print(f"  ├─ Chain CV:     {p['cv']:.4f}")
        print(f"  ├─ Timing:       mean={p['mean_span_s']:.4f}s  "
              f"median={p['median_span_s']:.4f}s")
        print(f"  ├─ Range:        min={p['min_span_s']:.4f}s  "
              f"max={p['max_span_s']:.4f}s")
        print(f"  ├─ P5-P95:       {p['p5_span_s']:.4f}s - {p['p95_span_s']:.4f}s")
        print(f"  │")
        print(f"  ├─ Interaction:")
        for si, step in enumerate(p["pattern"]):
            prefix = "│     " if si == 0 else "│  -> "
            print(f"  {prefix}{_short_key(step, 110)}")
        print(f"  │")
        print(f"  └─ Edge details:")
        for edge in p["edges"]:
            print(
                f"       {_short_key(edge['from'], 40)}")
            print(
                f"         -> {_short_key(edge['to'], 40)}")
            print(
                f"         gap: mean={edge['mean_gap_s']:.4f}s  "
                f"median={edge['median_gap_s']:.4f}s  "
                f"cv={edge['cv']:.4f}  "
                f"[p5={edge['p5_s']:.4f}s  p95={edge['p95_s']:.4f}s]")

    return patterns


# ═══════════════════════════════════════════════════════════════════════
# CLI
# ═══════════════════════════════════════════════════════════════════════

def _parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__,
                                formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--json-dir", type=Path, nargs="*", default=None)
    p.add_argument("--min-edge-gap", type=float, default=MIN_EDGE_GAP,
                   help=f"Min gap between events in seconds (default {MIN_EDGE_GAP})")
    p.add_argument("--max-edge-gap", type=float, default=MAX_EDGE_GAP,
                   help=f"Max gap between events in seconds (default {MAX_EDGE_GAP})")
    p.add_argument("--max-chain-span", type=float, default=MAX_CHAIN_SPAN,
                   help=f"Max total chain duration in seconds (default {MAX_CHAIN_SPAN})")
    p.add_argument("--min-sessions", type=int, default=MIN_SESSIONS,
                   help=f"Min sessions for a pattern (default {MIN_SESSIONS})")
    p.add_argument("--chain-cv-max", type=float, default=CHAIN_CV_MAX,
                   help=f"Max CV for total chain span (default {CHAIN_CV_MAX})")
    p.add_argument("--edge-cv-max", type=float, default=EDGE_CV_MAX,
                   help=f"Max CV for each edge (default {EDGE_CV_MAX})")
    p.add_argument("--max-prop-keys", type=int, default=MAX_DYNAMIC_PROP_KEYS,
                   help=f"Max prop keys (default {MAX_DYNAMIC_PROP_KEYS})")
    p.add_argument("--profile-events", type=int, default=PROFILE_MAX_EVENTS)
    p.add_argument("--output", type=Path, default=None)
    return p.parse_args()


def main() -> None:
    args = _parse_args()

    json_dirs = (
        [p.resolve() for p in args.json_dir]
        if args.json_dir
        else [p.resolve() for p in JSON_DIRS]
    )
    missing = [str(p) for p in json_dirs if not p.is_dir()]
    if missing:
        print(f"Warning: skipping: {missing}")
    json_dirs = [p for p in json_dirs if p.is_dir()]
    if not json_dirs:
        raise SystemExit("No valid JSON roots.")

    prop_keys = profile_and_select_prop_keys(
        json_dirs,
        max_profile_events=max(1, args.profile_events),
        max_keys=max(1, args.max_prop_keys),
        spread=PROFILE_SAMPLE_SPREAD,
    )
    print(f"\n>>> Selected prop keys: {list(prop_keys)}")

    df = load_events(json_dirs, prop_keys=prop_keys)
    print(f"Loaded {len(df)} events")

    patterns = run_pipeline(df, args)

    if args.output:
        output = {
            "config": {
                "min_edge_gap": args.min_edge_gap,
                "max_edge_gap": args.max_edge_gap,
                "max_chain_span": args.max_chain_span,
                "min_sessions": args.min_sessions,
                "chain_cv_max": args.chain_cv_max,
                "edge_cv_max": args.edge_cv_max,
                "prop_keys": list(prop_keys),
            },
            "total_events": len(df),
            "total_sessions": int(df["session_id"].nunique()),
            "interactions": patterns,
        }
        args.output.write_text(json.dumps(output, indent=2, default=str))
        print(f"\nResults written to {args.output}")


if __name__ == "__main__":
    main()
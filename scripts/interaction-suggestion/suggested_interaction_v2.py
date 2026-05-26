#!/usr/bin/env python3
"""
Event Interaction Mining
========================
Finds groups of events that fire in sequence after a user action.

Each interaction has exactly one action event at the first step; later steps are
non-action reactions only. Prop hints omit keys at ~100% value coverage.

Rules:
  - Each pair of consecutive events must be between MIN_EDGE_GAP and MAX_EDGE_GAP apart.
    Too close (< MIN_EDGE_GAP) = same code firing twice, collapse it.
    Too far (> MAX_EDGE_GAP) = different user action, split here.
  - Total chain must complete within MAX_CHAIN_SPAN seconds.
  - CV of total chain span across sessions must be < CHAIN_CV_MAX (unless relaxed by
    --auto-pattern-cv using a percentile of observed pattern CVs).
  - CV of each edge across sessions must be < EDGE_CV_MAX (same optional relax).

Usage:
  python script.py --project-id fancode
  python script.py --project-id fancode --lookback-days 7 --s3-output s3://pulse-otel-ingestion/fancode/suggested_interactions/
  python script.py --json-dir ~/data/day1 ~/data/day2
  python script.py --min-edge-gap 0.1 --max-edge-gap 1.0 --max-chain-span 3.0
  python script.py --min-sessions 5 --output results.json

S3 input (default when --project-id is set): reads parquet from
  s3://{bucket}/{project_id}/otel_logs/year=YYYY/month=MM/day=DD/
for the last N days (--lookback-days, default 7). By default processes one
partition at a time to avoid OOM (--no-s3-chunked loads all days at once).
Only events with pulse.type matching --pulse-type-filter are kept (default: custom_event).
Output JSON is written to S3 when --s3-output is set (or derived from --project-id).

Prop keys: a global blacklist is built first by sampling, per event_name, sessions
that contain that event; props with <=1 distinct value across all events in that
session are candidates. Profiling and step_prop_hints then ignore blacklisted keys.
Use --no-prop-session-blacklist to skip. Tune with --prop-blacklist-* flags.

Final list: use --top-suggestions N (default 20) to cap results; ranking is greedy
by new event names vs. already-chosen patterns, then interaction_score. Use 0 for no cap.

API integration (--api-integrate): after mining, fetches active interactions and
GET /v1/interactions/suggestions?status=ALL, removes duplicates, re-ranks to
--top-suggestions (default 20), and POSTs to /v1/interactions/suggestions.

Dynamic CV: --auto-pattern-cv widens chain/edge CV caps using a percentile of CVs
among patterns that already meet --min-sessions (floor stays the CLI --chain-cv-max /
--edge-cv-max). Pair with --min-session-pct for a %-of-sessions support floor.
"""
from __future__ import annotations

import argparse
import json
import math
import random
import sys
from collections import Counter, defaultdict
from datetime import date, datetime, timezone
from pathlib import Path
from collections.abc import Sequence
from typing import Any, Iterator

import numpy as np
import pandas as pd

_SCRIPT_DIR = Path(__file__).resolve().parent
if str(_SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(_SCRIPT_DIR))

from suggested_interaction_api import (  # noqa: E402
    PulseApiConfig,
    PulseInteractionApiClient,
    filter_patterns_against_existing,
    pattern_to_api_suggestion,
)
from suggested_interaction_chunked import (  # noqa: E402
    ActionInferenceAccumulator,
    EventSessionCounter,
    PatternDiscoveryAccumulator,
    RuntimeGapAccumulator,
    release_df,
    run_chunked_s3_workflow,
)
from suggested_interaction_s3 import (  # noqa: E402
    DEFAULT_LOOKBACK_DAYS,
    DEFAULT_PULSE_TYPE_FILTER,
    DEFAULT_S3_BUCKET,
    DEFAULT_S3_TABLE,
    props_match_pulse_type_filter,
    S3InputConfig,
    S3OutputConfig,
    default_output_prefix,
    iter_lookback_dates,
    list_existing_partition_uris,
    load_events_parquet,
    parse_s3_output_uri,
    resolve_aws_profile,
    resolve_end_date,
    write_json_to_s3,
)

# ─── Data roots ───
JSON_DIRS: list[Path] = [
    Path("/Users/shivamsengar/fancode_vector_json/fancode_28_data"),
    Path("/Users/shivamsengar/fancode_vector_json/fancode_29_data"),
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
MIN_CONFIDENCE = 0.0     # drop patterns with confidence <= this (keep if > threshold)
CHAIN_CV_MAX = 0.2     # CV of total chain span must be under this (before auto relax)
EDGE_CV_MAX = 0.3     # CV of each individual edge must be under this (before auto relax)
AUTO_PATTERN_CV_PERCENTILE = 85.0
AUTO_PATTERN_CV_CHAIN_CAP = 0.95
AUTO_PATTERN_CV_EDGE_CAP = 0.99
CV_DENOM_FLOOR_S = 0.05  # avoid exploding CV for very tiny means
PROP_HINT_MIN_SHARE = 0.7
PROP_HINT_MAX_SHARE = 0.999  # at or above = 100% coverage, omit hint
TOP_N_PRINT = 30
TOP_SUGGESTIONS_DEFAULT = 20
ACTION_TOP_K = 80
ACTION_MIN_SESSIONS = 20
ACTION_PRINT_TOP = 20
AUTO_MIN_SESSION_PCT = 0.0
ACTION_EVENT_TOKENS: tuple[str, ...] = (
    "clicked",
    "click",
    "initiated",
    "start",
    "submitted",
    "submit",
    "tapped",
    "tap",
    "selected",
    "select",
    "open",
    "order",
)
ACTION_EVENT_EXACT: set[str] = {
    "OrderInitiated",
    "LogPaymentCompleteActionStart",
    "SectionContentClicked",
}
NON_ACTION_EVENT_TOKENS: tuple[str, ...] = (
    "impression",
    "loaded",
    "response",
    "payload",
    "shown",
    "received",
    "heartbeat",
)

# ─── Prop-key profiling ───
PROFILE_MAX_EVENTS = 1000
CHUNKED_PROFILE_MAX_SESSIONS = 10_000
PROFILE_SAMPLE_SPREAD = True
MAX_DYNAMIC_PROP_KEYS = 0
MIN_KEY_PRESENCE_FRAC = 0.03
MAX_CARDINALITY_FRAC = 0.90
MAX_SAMPLE_VALUE_LEN = 200
DISTINCT_CAP = 2000
KEY_VALUE_MAX_LEN = 80
SESSION_CONSTANT_THRESHOLD = 0.85
MIN_VARYING_SESSION_FRAC = 0.10
PROP_BLACKLIST_SESSIONS_PER_EVENT = 5
PROP_BLACKLIST_MAX_EVENT_TYPES = 400
PROP_BLACKLIST_MIN_KEY_SAMPLES = 8
PROP_BLACKLIST_SEED = 42
MAX_DISTINCT_PER_SESSION_RATIO = 0.5
MAX_ABSOLUTE_DISTINCT = 50
MAX_PROP_CARDINALITY = 10
EXCLUDED_PROP_KEY_TOKENS: tuple[str, ...] = (
    "user",
    "device",
    "session",
    "installation",
    "token",
    "advertiser",
    "trace",
    "span",
    "telemetry",
    "network",
    "os.",
    "build",
    "version",
)


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


def _is_user_device_specific_prop_key(key: str) -> bool:
    k = key.strip().lower()
    return any(tok in k for tok in EXCLUDED_PROP_KEY_TOKENS)


def _is_user_action_event(event_name: str) -> bool:
    if not event_name:
        return False
    if event_name in ACTION_EVENT_EXACT:
        return True
    name = event_name.lower()
    return any(tok in name for tok in ACTION_EVENT_TOKENS)


def _is_likely_non_action_event(event_name: str) -> bool:
    if not event_name:
        return False
    name = event_name.lower()
    return any(tok in name for tok in NON_ACTION_EVENT_TOKENS)


def _is_single_action_at_start_pattern(
    pat: tuple[str, ...] | list[str],
    action_events: set[str],
) -> bool:
    """Interaction must start with one action event; no other action events in the chain."""
    if not pat:
        return False
    if pat[0] not in action_events:
        return False
    return not any(pat[i] in action_events for i in range(1, len(pat)))


def _build_step_prop_hints(
    pat: tuple[str, ...],
    pat_step_props: dict[int, dict[str, Counter[str]]],
    min_sessions: int,
) -> list[dict[str, Any]]:
    """Prop hints for discriminating values; skip keys at ~100% coverage (not informative)."""
    step_prop_hints: list[dict[str, Any]] = []
    for step_idx, step_name in enumerate(pat):
        candidates: list[tuple[float, int, str, str]] = []
        for k, counts in pat_step_props.get(step_idx, {}).items():
            total_k = sum(counts.values())
            if total_k < max(3, min_sessions):
                continue
            top_v, top_n = counts.most_common(1)[0]
            share = top_n / total_k
            distinct = len(counts)
            if distinct <= 8 and share >= PROP_HINT_MIN_SHARE:
                if share >= PROP_HINT_MAX_SHARE:
                    continue
                candidates.append((share, top_n, k, top_v))
        candidates.sort(key=lambda x: (-x[0], -x[1], x[2]))
        hints = [
            f"{k}={_short_key(v, 50)} ({share * 100:.0f}%)"
            for share, _, k, v in candidates[:2]
        ]
        if hints:
            step_prop_hints.append({"event": step_name, "hints": hints})
    return step_prop_hints


def infer_action_events(
    df: pd.DataFrame,
    *,
    top_k: int,
    min_sessions: int,
) -> tuple[set[str], list[dict[str, Any]], list[dict[str, Any]]]:
    event_sessions: dict[str, set[str]] = defaultdict(set)
    next_counts: dict[str, Counter[str]] = defaultdict(Counter)
    prev_counts: dict[str, Counter[str]] = defaultdict(Counter)
    gap_samples: dict[str, list[float]] = defaultdict(list)
    MAX_GAP_SAMPLES = 5000

    for sid, g in df.groupby("session_id", sort=False):
        g = g.sort_values("timestamp")
        names = g["event_name"].tolist()
        ts = g["timestamp"].tolist()
        m = len(names)
        for i in range(m):
            en = str(names[i]) if names[i] is not None else "?"
            event_sessions[en].add(str(sid))
        for i in range(m - 1):
            a = str(names[i]) if names[i] is not None else "?"
            b = str(names[i + 1]) if names[i + 1] is not None else "?"
            gap = (ts[i + 1] - ts[i]).total_seconds()
            if gap < 0:
                continue
            next_counts[a][b] += 1
            prev_counts[b][a] += 1
            if len(gap_samples[a]) < MAX_GAP_SAMPLES:
                gap_samples[a].append(gap)

    total_session_count = max(1, len(df["session_id"].unique()))
    scored: list[dict[str, Any]] = []
    for en, sessions in event_sessions.items():
        support = len(sessions)
        if support < min_sessions:
            continue
        outs = next_counts.get(en, Counter())
        out_degree = len(outs)
        if out_degree == 0:
            continue
        total_out = sum(outs.values())
        entropy = 0.0
        if total_out > 0:
            for c in outs.values():
                p = c / total_out
                if p > 0:
                    entropy -= p * math.log(p)
            if out_degree > 1:
                entropy /= math.log(out_degree)
        median_gap = float(np.median(np.array(gap_samples.get(en, [999.0]), dtype=float)))
        in_degree = len(prev_counts.get(en, Counter()))
        heuristic_action = _is_user_action_event(en)
        likely_non_action = _is_likely_non_action_event(en)
        name_boost = 0.8 if heuristic_action else 0.0
        non_action_penalty = 1.8 if likely_non_action else 0.0
        score = (
            1.4 * math.log1p(out_degree)
            + 1.2 * entropy
            + 0.8 * math.log1p(support)
            - 0.6 * math.log1p(max(0.0001, median_gap))
            + 0.3 * max(0, out_degree - in_degree)
            + name_boost
            - non_action_penalty
        )
        scored.append({
            "event_name": en,
            "score": round(score, 4),
            "session_support": support,
            "session_pct": round((support / total_session_count) * 100, 3),
            "out_degree": out_degree,
            "in_degree": in_degree,
            "entropy_next": round(entropy, 4),
            "median_next_gap_s": round(median_gap, 4),
            "heuristic_name_match": heuristic_action,
            "non_action_name_match": likely_non_action,
        })

    scored.sort(
        key=lambda x: (
            -x["score"], -x["session_support"], -x["out_degree"], x["median_next_gap_s"], x["event_name"]
        )
    )
    noise_candidates = [
        r
        for r in scored
        if (
            not r["heuristic_name_match"]
            and (
                r["non_action_name_match"]
                or (r["session_pct"] >= 10.0 and r["out_degree"] >= 20 and r["entropy_next"] <= 0.45)
            )
        )
    ]
    noise_event_names = {r["event_name"] for r in noise_candidates}

    filtered = [
        r
        for r in scored
        if (
            (r["heuristic_name_match"] or not r["non_action_name_match"])
            and r["out_degree"] >= 2
            and r["event_name"] not in noise_event_names
        )
    ]
    chosen = filtered[: max(1, top_k)]
    forced_exact = [
        r for r in scored
        if r["event_name"] in ACTION_EVENT_EXACT and r["session_support"] >= min_sessions
    ]
    chosen_by_name: dict[str, dict[str, Any]] = {r["event_name"]: r for r in chosen}
    for r in forced_exact:
        chosen_by_name[r["event_name"]] = r
    chosen = list(chosen_by_name.values())
    chosen.sort(
        key=lambda x: (
            -x["score"], -x["session_support"], -x["out_degree"], x["median_next_gap_s"], x["event_name"]
        )
    )
    action_events = {r["event_name"] for r in chosen}
    noise_candidates.sort(key=lambda x: (-x["session_support"], -x["out_degree"], x["event_name"]))
    return action_events, chosen, noise_candidates[:30]


def infer_action_events_from_accumulator(
    acc: ActionInferenceAccumulator,
    *,
    top_k: int,
    min_sessions: int,
) -> tuple[set[str], list[dict[str, Any]], list[dict[str, Any]]]:
    """Same selection rules as infer_action_events, using merged chunk state."""
    total_session_count = max(1, len(acc.all_sessions))
    scored: list[dict[str, Any]] = []
    for en, sessions in acc.event_sessions.items():
        support = len(sessions)
        if support < min_sessions:
            continue
        outs = acc.next_counts.get(en, Counter())
        out_degree = len(outs)
        if out_degree == 0:
            continue
        total_out = sum(outs.values())
        entropy = 0.0
        if total_out > 0:
            for c in outs.values():
                p = c / total_out
                if p > 0:
                    entropy -= p * math.log(p)
            if out_degree > 1:
                entropy /= math.log(out_degree)
        median_gap = float(np.median(np.array(acc.gap_samples.get(en, [999.0]), dtype=float)))
        in_degree = len(acc.prev_counts.get(en, Counter()))
        heuristic_action = _is_user_action_event(en)
        likely_non_action = _is_likely_non_action_event(en)
        name_boost = 0.8 if heuristic_action else 0.0
        non_action_penalty = 1.8 if likely_non_action else 0.0
        score = (
            1.4 * math.log1p(out_degree)
            + 1.2 * entropy
            + 0.8 * math.log1p(support)
            - 0.6 * math.log1p(max(0.0001, median_gap))
            + 0.3 * max(0, out_degree - in_degree)
            + name_boost
            - non_action_penalty
        )
        scored.append({
            "event_name": en,
            "score": round(score, 4),
            "session_support": support,
            "session_pct": round((support / total_session_count) * 100, 3),
            "out_degree": out_degree,
            "in_degree": in_degree,
            "entropy_next": round(entropy, 4),
            "median_next_gap_s": round(median_gap, 4),
            "heuristic_name_match": heuristic_action,
            "non_action_name_match": likely_non_action,
        })

    scored.sort(
        key=lambda x: (
            -x["score"], -x["session_support"], -x["out_degree"], x["median_next_gap_s"], x["event_name"]
        )
    )
    noise_candidates = [
        r
        for r in scored
        if (
            not r["heuristic_name_match"]
            and (
                r["non_action_name_match"]
                or (r["session_pct"] >= 10.0 and r["out_degree"] >= 20 and r["entropy_next"] <= 0.45)
            )
        )
    ]
    noise_event_names = {r["event_name"] for r in noise_candidates}
    filtered = [
        r
        for r in scored
        if (
            (r["heuristic_name_match"] or not r["non_action_name_match"])
            and r["out_degree"] >= 2
            and r["event_name"] not in noise_event_names
        )
    ]
    chosen = filtered[: max(1, top_k)]
    forced_exact = [
        r for r in scored
        if r["event_name"] in ACTION_EVENT_EXACT and r["session_support"] >= min_sessions
    ]
    chosen_by_name: dict[str, dict[str, Any]] = {r["event_name"]: r for r in chosen}
    for r in forced_exact:
        chosen_by_name[r["event_name"]] = r
    chosen = list(chosen_by_name.values())
    chosen.sort(
        key=lambda x: (
            -x["score"], -x["session_support"], -x["out_degree"], x["median_next_gap_s"], x["event_name"]
        )
    )
    action_events = {r["event_name"] for r in chosen}
    noise_candidates.sort(key=lambda x: (-x["session_support"], -x["out_degree"], x["event_name"]))
    return action_events, chosen, noise_candidates[:30]


def derive_runtime_params(
    df: pd.DataFrame,
    action_events: set[str],
    args: argparse.Namespace,
) -> dict[str, Any]:
    total_sessions = int(df["session_id"].nunique())
    min_sessions = max(1, int(args.min_sessions))
    if args.min_session_pct > 0:
        min_sessions = max(min_sessions, int(math.ceil((args.min_session_pct / 100.0) * total_sessions)))

    min_edge_gap = float(args.min_edge_gap)
    max_edge_gap = float(args.max_edge_gap)
    max_chain_span = float(args.max_chain_span)

    if not args.auto_thresholds:
        return {
            "min_sessions": min_sessions,
            "min_edge_gap": min_edge_gap,
            "max_edge_gap": max_edge_gap,
            "max_chain_span": max_chain_span,
            "auto_derived": False,
        }

    gaps: list[float] = []
    action_to_next_gaps: list[float] = []
    action_spans: list[float] = []
    MAX_SAMPLES = 300000
    for _, g in df.groupby("session_id", sort=False):
        g = g.sort_values("timestamp")
        names = g["event_name"].tolist()
        ts = g["timestamp"].tolist()
        m = len(ts)
        if m < 2:
            continue
        action_indices = [i for i, n in enumerate(names) if str(n) in action_events]
        for i in range(m - 1):
            gap = (ts[i + 1] - ts[i]).total_seconds()
            if gap < 0:
                continue
            if len(gaps) < MAX_SAMPLES:
                gaps.append(gap)
            if str(names[i]) in action_events and str(names[i + 1]) not in action_events:
                if len(action_to_next_gaps) < MAX_SAMPLES:
                    action_to_next_gaps.append(gap)
        for idx, ai in enumerate(action_indices):
            nxt = action_indices[idx + 1] if idx + 1 < len(action_indices) else (m - 1)
            if nxt <= ai:
                continue
            span = (ts[nxt] - ts[ai]).total_seconds()
            if span >= 0 and len(action_spans) < MAX_SAMPLES:
                action_spans.append(span)

    if gaps:
        min_edge_gap = float(np.percentile(np.array(gaps, dtype=float), 1))
    if action_to_next_gaps:
        max_edge_gap = float(np.percentile(np.array(action_to_next_gaps, dtype=float), 92))
    elif gaps:
        max_edge_gap = float(np.percentile(np.array(gaps, dtype=float), 90))
    if action_spans:
        max_chain_span = float(np.percentile(np.array(action_spans, dtype=float), 90))

    min_edge_gap = float(max(0.0, min(min_edge_gap, 0.2)))
    max_edge_gap = float(max(0.2, min(max_edge_gap, 5.0)))
    if max_edge_gap <= min_edge_gap + 0.01:
        max_edge_gap = min_edge_gap + 0.05
    max_chain_span = float(max(max_edge_gap * 2.0, min(max_chain_span, 30.0)))

    return {
        "min_sessions": min_sessions,
        "min_edge_gap": round(min_edge_gap, 4),
        "max_edge_gap": round(max_edge_gap, 4),
        "max_chain_span": round(max_chain_span, 4),
        "auto_derived": True,
        "derived_samples": {
            "gaps": len(gaps),
            "action_to_next_gaps": len(action_to_next_gaps),
            "action_spans": len(action_spans),
        },
    }


def derive_runtime_params_from_gap_accumulator(
    gap_acc: RuntimeGapAccumulator,
    action_events: set[str],
    args: argparse.Namespace,
    *,
    total_sessions: int,
) -> dict[str, Any]:
    min_sessions = max(1, int(args.min_sessions))
    if args.min_session_pct > 0:
        min_sessions = max(
            min_sessions,
            int(math.ceil((args.min_session_pct / 100.0) * total_sessions)),
        )
    min_edge_gap = float(args.min_edge_gap)
    max_edge_gap = float(args.max_edge_gap)
    max_chain_span = float(args.max_chain_span)
    if not args.auto_thresholds:
        return {
            "min_sessions": min_sessions,
            "min_edge_gap": min_edge_gap,
            "max_edge_gap": max_edge_gap,
            "max_chain_span": max_chain_span,
            "auto_derived": False,
        }
    gaps = gap_acc.gaps
    action_to_next_gaps = gap_acc.action_to_next_gaps
    action_spans = gap_acc.action_spans
    if gaps:
        min_edge_gap = float(np.percentile(np.array(gaps, dtype=float), 1))
    if action_to_next_gaps:
        max_edge_gap = float(np.percentile(np.array(action_to_next_gaps, dtype=float), 92))
    elif gaps:
        max_edge_gap = float(np.percentile(np.array(gaps, dtype=float), 90))
    if action_spans:
        max_chain_span = float(np.percentile(np.array(action_spans, dtype=float), 90))
    min_edge_gap = float(max(0.0, min(min_edge_gap, 0.2)))
    max_edge_gap = float(max(0.2, min(max_edge_gap, 5.0)))
    if max_edge_gap <= min_edge_gap + 0.01:
        max_edge_gap = min_edge_gap + 0.05
    max_chain_span = float(max(max_edge_gap * 2.0, min(max_chain_span, 30.0)))
    return {
        "min_sessions": min_sessions,
        "min_edge_gap": round(min_edge_gap, 4),
        "max_edge_gap": round(max_edge_gap, 4),
        "max_chain_span": round(max_chain_span, 4),
        "auto_derived": True,
        "derived_samples": {
            "gaps": len(gaps),
            "action_to_next_gaps": len(action_to_next_gaps),
            "action_spans": len(action_spans),
        },
    }


# ═══════════════════════════════════════════════════════════════════════
# Data loading
# ═══════════════════════════════════════════════════════════════════════

def _iter_json_events(
    json_dirs: Sequence[Path],
    *,
    pulse_type_filter: str | None = DEFAULT_PULSE_TYPE_FILTER,
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
                props = parse_props(o.get("props"))
                if not props_match_pulse_type_filter(props, pulse_type_filter):
                    continue
                yield sid, ts, en, props


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
) -> list[tuple[str, str, dict[str, Any]]]:
    rows: list[tuple[str, str, dict[str, Any]]] = []
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
                en = o.get("event_name")
                if not sid:
                    continue
                rows.append(
                    (str(sid), str(en) if en is not None else "", parse_props(o.get("props")))
                )
                if len(rows) >= max_events:
                    break
    else:
        for sid, _, en, props in _iter_json_events(json_dirs):
            rows.append((str(sid), str(en) if en is not None else "", props))
            if len(rows) >= max_events:
                break
    return rows


def build_global_prop_blacklist_from_sessions(
    df: pd.DataFrame,
    *,
    sessions_per_event: int,
    max_event_types: int,
    low_card_frac: float,
    min_key_samples: int,
    seed: int,
) -> tuple[frozenset[str], dict[str, Any]]:
    """
    For each frequent event_name, sample sessions that contain that event.
    For each sampled session, look at *all* events in that session and measure
    per-prop-key distinct value count. Keys that almost always have cardinality
    <= 1 within a user's session (device constants, stable context) go into a
    global blacklist for profiling and hints.
    """
    if df.empty or "session_id" not in df.columns or "props" not in df.columns:
        return frozenset(), {"disabled": True, "reason": "empty_or_missing_columns"}

    rng = random.Random(seed)
    evt_counts = df["event_name"].value_counts()
    names = evt_counts.index.tolist()
    if max_event_types > 0:
        names = names[:max_event_types]

    tasks: list[tuple[str, str]] = []
    for en in names:
        sids = df.loc[df["event_name"] == en, "session_id"].astype(str).unique()
        if len(sids) == 0:
            continue
        take = min(max(1, sessions_per_event), len(sids))
        for sid in rng.sample(list(sids), take):
            tasks.append((str(en), str(sid)))

    needed_sids = {sid for _, sid in tasks}
    if not needed_sids:
        return frozenset(), {"disabled": True, "reason": "no_sample_tasks"}

    sub = df[df["session_id"].astype(str).isin(needed_sids)]
    by_sid: dict[str, pd.DataFrame] = {
        str(sid): grp for sid, grp in sub.groupby("session_id", sort=False)
    }

    def _session_key_cards(grp: pd.DataFrame) -> dict[str, int]:
        key_vals: dict[str, set[str]] = defaultdict(set)
        for props in grp["props"]:
            if not isinstance(props, dict):
                continue
            for k, v in props.items():
                if not isinstance(k, str):
                    continue
                if _is_user_device_specific_prop_key(k):
                    continue
                if v is None or v == "":
                    continue
                sv = str(v)
                if len(sv) > MAX_SAMPLE_VALUE_LEN:
                    continue
                s = sv[:200]
                vs = key_vals[k]
                if len(vs) < 2:
                    vs.add(s)
        return {k: len(vs) for k, vs in key_vals.items()}

    seen_sk: set[tuple[str, str]] = set()
    low: dict[str, int] = defaultdict(int)
    high: dict[str, int] = defaultdict(int)
    for _en, sid in tasks:
        grp = by_sid.get(sid)
        if grp is None or grp.empty:
            continue
        cards = _session_key_cards(grp.sort_values("timestamp"))
        for k, card in cards.items():
            if (sid, k) in seen_sk:
                continue
            seen_sk.add((sid, k))
            if card <= 1:
                low[k] += 1
            else:
                high[k] += 1

    blacklist: set[str] = set()
    for k, lo in low.items():
        hi = high.get(k, 0)
        tot = lo + hi
        if tot < min_key_samples:
            continue
        if lo / tot >= low_card_frac:
            blacklist.add(k)

    obs_keys = set(low) | set(high)
    stats: dict[str, Any] = {
        "sessions_per_event": sessions_per_event,
        "max_event_types": max_event_types,
        "low_card_frac": low_card_frac,
        "min_key_samples": min_key_samples,
        "event_types_considered": len(names),
        "sample_tasks": len(tasks),
        "sessions_resolved": len(by_sid),
        "unique_prop_keys_observed": len(obs_keys),
        "n_blacklisted": len(blacklist),
    }

    return frozenset(blacklist), stats


def _sample_events_from_df(
    df: pd.DataFrame,
    max_events: int,
    spread: bool,
) -> list[tuple[str, str, dict[str, Any]]]:
    rows: list[tuple[str, str, dict[str, Any]]] = []
    if df.empty or max_events <= 0:
        return rows
    if spread and "event_name" in df.columns:
        groups = list(df.groupby("event_name", sort=False))
        if not groups:
            return rows
        per_group = max(1, max_events // len(groups))
        for _, group in groups:
            if len(rows) >= max_events:
                break
            take = group if len(group) <= per_group else group.sample(
                n=per_group, random_state=PROP_BLACKLIST_SEED
            )
            for _, row in take.iterrows():
                rows.append((
                    str(row["session_id"]),
                    str(row.get("event_name") or ""),
                    row["props"] if isinstance(row.get("props"), dict) else {},
                ))
                if len(rows) >= max_events:
                    break
    else:
        subset = df.head(max_events)
        for _, row in subset.iterrows():
            rows.append((
                str(row["session_id"]),
                str(row.get("event_name") or ""),
                row["props"] if isinstance(row.get("props"), dict) else {},
            ))
    return rows


def rows_from_sessions_df(
    df: pd.DataFrame,
    session_ids: frozenset[str] | set[str],
) -> list[tuple[str, str, dict[str, Any]]]:
    """All events for the given sessions (for session-based prop profiling)."""
    if df.empty or not session_ids:
        return []
    sub = df[df["session_id"].astype(str).isin(session_ids)]
    rows: list[tuple[str, str, dict[str, Any]]] = []
    for _, row in sub.iterrows():
        rows.append((
            str(row["session_id"]),
            str(row.get("event_name") or ""),
            row["props"] if isinstance(row.get("props"), dict) else {},
        ))
    return rows


def profile_and_select_prop_keys(
    json_dirs: Sequence[Path] | None = None,
    *,
    sampled_rows: list[tuple[str, str, dict[str, Any]]] | None = None,
    max_profile_events: int,
    max_cardinality: int,
    max_keys: int,
    spread: bool = True,
    blacklisted_prop_keys: frozenset[str] | set[str] | None = None,
) -> dict[str, tuple[str, ...]]:
    bl = blacklisted_prop_keys or frozenset()
    if sampled_rows is not None:
        sampled = sampled_rows
    elif json_dirs:
        sampled = _sample_events(json_dirs, max_profile_events, spread)
    else:
        sampled = []
    n_rows = len(sampled)
    if n_rows == 0:
        return {}

    event_rows: dict[str, int] = defaultdict(int)
    presence: dict[str, dict[str, int]] = defaultdict(lambda: defaultdict(int))
    distinct: dict[str, dict[str, set[str]]] = defaultdict(lambda: defaultdict(set))
    too_long: dict[str, set[str]] = defaultdict(set)

    for _, event_name, props in sampled:
        en = event_name or "?"
        event_rows[en] += 1
        for k, v in props.items():
            if not isinstance(k, str):
                continue
            if k in bl:
                continue
            if _is_user_device_specific_prop_key(k):
                continue
            if v is None or v == "":
                continue
            sv = str(v)
            if len(sv) > MAX_SAMPLE_VALUE_LEN:
                too_long[en].add(k)
                continue
            presence[en][k] += 1
            distinct[en][k].add(sv[:200])

    key_session_values: dict[str, dict[str, dict[str, set[str]]]] = defaultdict(
        lambda: defaultdict(lambda: defaultdict(set))
    )
    for sid, event_name, props in sampled:
        en = event_name or "?"
        for k, v in props.items():
            if k in bl:
                continue
            if k in too_long[en] or k not in presence[en]:
                continue
            if v is None or v == "":
                continue
            sv = str(v)[:200]
            if len(key_session_values[en][k][sid]) < 50:
                key_session_values[en][k][sid].add(sv)

    selected_by_event: dict[str, tuple[str, ...]] = {}
    print(f"\n  Prop-key profiling ({n_rows} sampled events)")
    if bl:
        print(f"  (excluding {len(bl)} keys on global session-constant blacklist)")
    print(
        "  Per event_name: keep keys with cardinality >1 (no upper bound)"
    )
    for en in sorted(event_rows.keys()):
        n_en_rows = event_rows[en]
        if n_en_rows <= 0:
            continue
        candidates: list[tuple[float, str]] = []
        for k, pr in presence[en].items():
            if k in too_long[en]:
                continue
            d = len(distinct[en].get(k, ()))
            if d <= 1:
                continue
            frac = pr / n_en_rows
            if frac < MIN_KEY_PRESENCE_FRAC:
                continue
            n_sess_k = len(key_session_values[en].get(k, {}))
            candidates.append((float(pr) * (1.0 + float(d)), k))

        candidates.sort(key=lambda x: (-x[0], x[1]))
        if max_keys > 0:
            final = [k for _, k in candidates[:max_keys]]
        else:
            final = [k for _, k in candidates]
        final.sort()
        if final:
            selected_by_event[en] = tuple(final)
            print(
                f"  Event={_short_key(en, 35)} rows={n_en_rows:>4} "
                f"selected_keys={list(final)}"
            )

    return selected_by_event


# ═══════════════════════════════════════════════════════════════════════
# Event key building
# ═══════════════════════════════════════════════════════════════════════

def build_event_key(
    event_name: str | None,
    props: dict[str, Any],
    prop_keys_by_event: dict[str, tuple[str, ...]],
) -> str:
    en = str(event_name or "?")
    parts: list[str] = [en]
    prop_keys = prop_keys_by_event.get(en, ())
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


def load_events(
    json_dirs: Sequence[Path],
    *,
    prop_keys_by_event: dict[str, tuple[str, ...]],
    pulse_type_filter: str | None = DEFAULT_PULSE_TYPE_FILTER,
) -> pd.DataFrame:
    rows: list[dict[str, Any]] = []
    for sid, ts, en, props in _iter_json_events(
        json_dirs, pulse_type_filter=pulse_type_filter
    ):
        key = build_event_key(
            str(en) if en is not None else None,
            props,
            prop_keys_by_event,
        )
        rows.append({
            "session_id": sid,
            "timestamp": pd.to_datetime(ts, utc=True),
            "event_name": str(en) if en is not None else "",
            "event_key": key,
            "props": props,
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


def extract_action_reaction_chains(
    df: pd.DataFrame,
    action_events: set[str],
    min_edge_gap: float,
    max_edge_gap: float,
    max_chain_span: float,
    max_len: int,
) -> list[tuple[str, list[Any], list[str], list[dict[str, Any]]]]:
    chains: list[tuple[str, list[Any], list[str], list[dict[str, Any]]]] = []
    for sid, g in df.groupby("session_id", sort=False):
        g = g.sort_values("timestamp")
        ts = g["timestamp"].tolist()
        names = g["event_name"].tolist()
        props_rows = g["props"].tolist()
        m = len(ts)
        if m < 2:
            continue
        for i in range(m):
            en = str(names[i]) if names[i] is not None else ""
            if en not in action_events:
                continue
            chain_ts: list[Any] = [ts[i]]
            chain_names: list[str] = [en]
            chain_props: list[dict[str, Any]] = [props_rows[i] if isinstance(props_rows[i], dict) else {}]
            prev_idx = i
            for j in range(i + 1, m):
                gap = (ts[j] - ts[prev_idx]).total_seconds()
                total_span = (ts[j] - ts[i]).total_seconds()
                if total_span > max_chain_span:
                    break
                if gap > max_edge_gap:
                    break
                if gap < min_edge_gap:
                    continue
                next_name = str(names[j]) if names[j] is not None else ""
                if next_name in action_events:
                    break
                chain_ts.append(ts[j])
                chain_names.append(next_name)
                chain_props.append(props_rows[j] if isinstance(props_rows[j], dict) else {})
                prev_idx = j
                if len(chain_names) >= max_len:
                    break
            if len(chain_names) >= 2:
                chains.append((str(sid), chain_ts, chain_names, chain_props))
    return chains


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
                # Skip low-information loops where the exact same key repeats back-to-back.
                if any(pat[j] == pat[j + 1] for j in range(len(pat) - 1)):
                    continue
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
            e_cv = e_sig / max(e_mu, CV_DENOM_FLOOR_S)
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


def _meets_session_support(
    unique_sessions: int,
    min_sessions: int,
    *,
    exclusive: bool,
) -> bool:
    """exclusive=True requires unique_sessions > min_sessions (e.g. >50 when min_sessions=50)."""
    if exclusive:
        return unique_sessions > min_sessions
    return unique_sessions >= min_sessions


def discover_action_interactions(
    chains: list[tuple[str, list[Any], list[str], list[dict[str, Any]]]],
    total_sessions: int,
    min_len: int,
    max_len: int,
    min_sessions: int,
    chain_cv_max: float,
    edge_cv_max: float,
    action_session_counts: dict[str, int],
    event_session_counts: dict[str, int],
    action_events: set[str],
    blacklisted_prop_keys: frozenset[str] | set[str] | None = None,
    *,
    min_confidence: float = MIN_CONFIDENCE,
    min_sessions_exclusive: bool = False,
    auto_pattern_cv: bool = False,
    auto_pattern_cv_percentile: float = AUTO_PATTERN_CV_PERCENTILE,
    auto_pattern_cv_chain_cap: float = AUTO_PATTERN_CV_CHAIN_CAP,
    auto_pattern_cv_edge_cap: float = AUTO_PATTERN_CV_EDGE_CAP,
    pattern_accumulator: PatternDiscoveryAccumulator | None = None,
) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    bl = blacklisted_prop_keys or frozenset()
    if pattern_accumulator is not None:
        acc = pattern_accumulator
    else:
        acc = PatternDiscoveryAccumulator()
        acc.ingest_chains(
            chains,
            action_events=action_events,
            min_len=min_len,
            max_len=max_len,
            blacklisted_prop_keys=bl,
            is_single_action_at_start_pattern=_is_single_action_at_start_pattern,
            is_user_device_specific_prop_key=_is_user_device_specific_prop_key,
            max_sample_value_len=MAX_SAMPLE_VALUE_LEN,
        )
    return _finalize_discovered_patterns(
        acc,
        total_sessions=total_sessions,
        min_sessions=min_sessions,
        min_confidence=min_confidence,
        min_sessions_exclusive=min_sessions_exclusive,
        chain_cv_max=chain_cv_max,
        edge_cv_max=edge_cv_max,
        action_session_counts=action_session_counts,
        event_session_counts=event_session_counts,
        action_events=action_events,
        auto_pattern_cv=auto_pattern_cv,
        auto_pattern_cv_percentile=auto_pattern_cv_percentile,
        auto_pattern_cv_chain_cap=auto_pattern_cv_chain_cap,
        auto_pattern_cv_edge_cap=auto_pattern_cv_edge_cap,
    )


def _finalize_discovered_patterns(
    acc: PatternDiscoveryAccumulator,
    *,
    total_sessions: int,
    min_sessions: int,
    min_confidence: float = MIN_CONFIDENCE,
    min_sessions_exclusive: bool = False,
    chain_cv_max: float,
    edge_cv_max: float,
    action_session_counts: dict[str, int],
    event_session_counts: dict[str, int],
    action_events: set[str],
    auto_pattern_cv: bool = False,
    auto_pattern_cv_percentile: float = AUTO_PATTERN_CV_PERCENTILE,
    auto_pattern_cv_chain_cap: float = AUTO_PATTERN_CV_CHAIN_CAP,
    auto_pattern_cv_edge_cap: float = AUTO_PATTERN_CV_EDGE_CAP,
) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    pat_spans = acc.pat_spans
    pat_sessions = acc.pat_sessions
    pat_edge_gaps = acc.pat_edge_gaps
    pat_step_props = acc.pat_step_props

    chain_cv_req = float(chain_cv_max)
    edge_cv_req = float(edge_cv_max)
    chain_cv_eff = chain_cv_req
    edge_cv_eff = edge_cv_req
    cv_meta: dict[str, Any] = {
        "auto_pattern_cv": bool(auto_pattern_cv),
        "auto_pattern_cv_percentile": float(auto_pattern_cv_percentile),
        "chain_cv_max_requested": round(chain_cv_req, 4),
        "edge_cv_max_requested": round(edge_cv_req, 4),
        "chain_cv_max_effective": round(chain_cv_eff, 4),
        "edge_cv_max_effective": round(edge_cv_eff, 4),
        "n_patterns_cv_sample": 0,
        "chain_cv_percentile_raw": None,
        "edge_cv_percentile_raw": None,
    }

    if auto_pattern_cv:
        chain_cvs: list[float] = []
        max_edge_cvs: list[float] = []
        for pat, spans in pat_spans.items():
            unique_sessions = len(pat_sessions[pat])
            if not _meets_session_support(
                unique_sessions, min_sessions, exclusive=min_sessions_exclusive
            ):
                continue
            arr = np.array(spans, dtype=float)
            mu = float(np.mean(arr))
            if mu < 0.001:
                continue
            sig = float(np.std(arr, ddof=1)) if len(arr) > 1 else 0.0
            cv = float(sig / max(mu, CV_DENOM_FLOOR_S))
            chain_cvs.append(cv)
            max_e = 0.0
            for e_idx in range(len(pat) - 1):
                gaps = pat_edge_gaps[pat][e_idx]
                e_arr = np.array(gaps, dtype=float)
                e_mu = float(np.mean(e_arr))
                e_sig = float(np.std(e_arr, ddof=1)) if len(e_arr) > 1 else 0.0
                e_cv = float(e_sig / max(e_mu, CV_DENOM_FLOOR_S))
                max_e = max(max_e, e_cv)
            max_edge_cvs.append(max_e)

        cv_meta["n_patterns_cv_sample"] = len(chain_cvs)
        if chain_cvs:
            pct = float(auto_pattern_cv_percentile)
            pct = max(0.0, min(100.0, pct))
            c_arr = np.array(chain_cvs, dtype=float)
            e_arr = np.array(max_edge_cvs, dtype=float)
            pct_chain = float(np.percentile(c_arr, pct))
            pct_edge = float(np.percentile(e_arr, pct))
            cv_meta["chain_cv_percentile_raw"] = round(pct_chain, 4)
            cv_meta["edge_cv_percentile_raw"] = round(pct_edge, 4)
            chain_cv_eff = min(
                float(auto_pattern_cv_chain_cap),
                max(chain_cv_req, pct_chain),
            )
            edge_cv_eff = min(
                float(auto_pattern_cv_edge_cap),
                max(edge_cv_req, pct_edge),
            )
            cv_meta["chain_cv_max_effective"] = round(chain_cv_eff, 4)
            cv_meta["edge_cv_max_effective"] = round(edge_cv_eff, 4)

    results: list[dict[str, Any]] = []
    for pat, spans in pat_spans.items():
        if not _is_single_action_at_start_pattern(pat, action_events):
            continue
        unique_sessions = len(pat_sessions[pat])
        if not _meets_session_support(
            unique_sessions, min_sessions, exclusive=min_sessions_exclusive
        ):
            continue
        arr = np.array(spans, dtype=float)
        mu = float(np.mean(arr))
        if mu < 0.001:
            continue
        sig = float(np.std(arr, ddof=1)) if len(arr) > 1 else 0.0
        cv = sig / max(mu, CV_DENOM_FLOOR_S)
        if cv >= chain_cv_eff:
            continue
        edges: list[dict[str, Any]] = []
        all_edges_pass = True
        for e_idx in range(len(pat) - 1):
            gaps = pat_edge_gaps[pat][e_idx]
            e_arr = np.array(gaps, dtype=float)
            e_mu = float(np.mean(e_arr))
            e_sig = float(np.std(e_arr, ddof=1)) if len(e_arr) > 1 else 0.0
            e_cv = e_sig / max(e_mu, CV_DENOM_FLOOR_S)
            e_median = float(np.median(e_arr))
            e_p5 = float(np.percentile(e_arr, 5))
            e_p95 = float(np.percentile(e_arr, 95))
            if e_cv >= edge_cv_eff:
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
        action_name = pat[0]
        terminal_name = pat[-1]
        action_sessions = max(1, action_session_counts.get(action_name, 1))
        terminal_base = (
            event_session_counts.get(terminal_name, 0) / total_sessions if total_sessions > 0 else 0.0
        )
        confidence = unique_sessions / action_sessions
        if confidence <= float(min_confidence):
            continue
        lift = confidence / max(terminal_base, 1e-6)
        interaction_score = (
            math.log1p(unique_sessions) * max(0.0, confidence) * max(0.0, min(lift, 50.0))
        )
        step_prop_hints = _build_step_prop_hints(
            pat, pat_step_props[pat], min_sessions,
        )
        results.append({
            "pattern": list(pat),
            "length": len(pat),
            "total_occurrences": len(spans),
            "unique_sessions": unique_sessions,
            "session_pct": session_pct,
            "confidence": round(confidence, 4),
            "lift": round(lift, 4),
            "interaction_score": round(interaction_score, 4),
            "mean_span_s": round(mu, 4),
            "std_span_s": round(sig, 4),
            "cv": round(cv, 4),
            "min_span_s": round(float(np.min(arr)), 4),
            "max_span_s": round(float(np.max(arr)), 4),
            "median_span_s": round(float(np.median(arr)), 4),
            "p5_span_s": round(float(np.percentile(arr, 5)), 4),
            "p95_span_s": round(float(np.percentile(arr, 95)), 4),
            "edges": edges,
            "step_prop_hints": step_prop_hints,
        })
    results.sort(
        key=lambda r: (
            -r["interaction_score"],
            -r["session_pct"],
            -r["confidence"],
            r["cv"],
        )
    )
    return results, cv_meta


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


def select_diverse_top_suggestions(
    patterns: list[dict[str, Any]],
    k: int,
) -> list[dict[str, Any]]:
    """
    Keep at most *k* patterns. Greedy order: each step pick the remaining pattern
    that introduces the most new event names (vs. union of events already chosen);
    tie-break by interaction_score, unique_sessions, session_pct.

    Later picks naturally favor patterns that repeat events already covered.
    """
    if k <= 0:
        return [{**p, "suggestion_rank": i + 1} for i, p in enumerate(patterns)]
    if len(patterns) <= k:
        return [{**p, "suggestion_rank": i + 1} for i, p in enumerate(patterns)]

    pool: list[dict[str, Any]] = [dict(p) for p in patterns]
    selected: list[dict[str, Any]] = []
    covered: set[str] = set()
    rank = 0
    while rank < k and pool:
        best_i = -1
        best_key: tuple[Any, ...] = ()
        for i, p in enumerate(pool):
            pat = p.get("pattern") or ()
            ev = {str(x) for x in pat}
            novel_n = len(ev - covered)
            sc = float(p.get("interaction_score", 0))
            us = int(p.get("unique_sessions", 0))
            sp = float(p.get("session_pct", 0))
            key = (novel_n, sc, us, sp)
            if best_i < 0 or key > best_key:
                best_key = key
                best_i = i
        if best_i < 0:
            break
        p = pool.pop(best_i)
        rank += 1
        pat = p.get("pattern") or ()
        ev = {str(x) for x in pat}
        novel_names = sorted(ev - covered)
        p["suggestion_rank"] = rank
        p["novel_event_count_at_pick"] = len(novel_names)
        p["novel_event_names_at_pick"] = novel_names
        covered.update(ev)
        selected.append(p)
    return selected


# ═══════════════════════════════════════════════════════════════════════
# Pipeline
# ═══════════════════════════════════════════════════════════════════════

def run_pipeline(
    df: pd.DataFrame,
    args: argparse.Namespace,
    inferred_actions_top: list[dict[str, Any]],
    inferred_noise_events: list[dict[str, Any]],
    runtime_params: dict[str, Any],
    *,
    global_prop_blacklist: frozenset[str] | set[str] | None = None,
    pattern_accumulator: PatternDiscoveryAccumulator | None = None,
    total_sessions_override: int | None = None,
    event_session_counts_override: dict[str, int] | None = None,
) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    bl = global_prop_blacklist or frozenset()
    sink_meta: dict[str, Any] = {
        "patterns_after_filters": 0,
        "patterns_after_dedup": 0,
        "suggestions_returned": 0,
    }
    total_sessions = (
        int(total_sessions_override)
        if total_sessions_override is not None
        else int(df["session_id"].nunique())
    )
    print(f"\nTotal sessions: {total_sessions}")

    action_events = {r["event_name"] for r in inferred_actions_top}
    print(
        f"Inferred action events: {len(action_events)} "
        f"(top_k={args.action_top_k}, min_sessions>={args.action_min_sessions})"
    )
    print(
        f"  {'Event':<36} {'Score':>7} {'Sess':>7} {'Out':>5} {'In':>5} {'H':>3} {'N':>3} {'Gap':>7}"
    )
    for row in inferred_actions_top[: max(1, args.action_print_top)]:
        print(
            f"  {_short_key(row['event_name'], 36):<36} "
            f"{row['score']:>7.3f} {row['session_support']:>7} "
            f"{row['out_degree']:>5} {row['in_degree']:>5} "
            f"{'Y' if row['heuristic_name_match'] else 'N':>3} "
                f"{'Y' if row.get('non_action_name_match') else 'N':>3} "
            f"{row['median_next_gap_s']:>7.3f}"
        )
    if inferred_noise_events:
        print(f"Likely noise events filtered from anchors: {len(inferred_noise_events)}")
        for row in inferred_noise_events[:10]:
            print(
                f"  - {_short_key(row['event_name'], 36)} "
                f"(sess={row['session_support']}, out={row['out_degree']}, H={'Y' if row['heuristic_name_match'] else 'N'})"
            )

    min_edge_gap = float(runtime_params["min_edge_gap"])
    max_edge_gap = float(runtime_params["max_edge_gap"])
    max_chain_span = float(runtime_params["max_chain_span"])
    min_sessions = int(runtime_params["min_sessions"])
    print(
        "Runtime params: "
        f"min_sessions={min_sessions}, min_edge_gap={min_edge_gap}, "
        f"max_edge_gap={max_edge_gap}, max_chain_span={max_chain_span}, "
        f"auto_derived={runtime_params.get('auto_derived', False)}"
    )

    # Step 1: Build action -> reaction chains (skipped when using chunked accumulator)
    if pattern_accumulator is None:
        chains = extract_action_reaction_chains(
            df, action_events, min_edge_gap, max_edge_gap, max_chain_span, CASCADE_MAX_LEN,
        )
        chain_count = len(chains)
    else:
        chains = []
        chain_count = pattern_accumulator.chains_ingested

    print(
        f"Action-reaction chains extracted: {chain_count}  "
        f"(edge gap: {min_edge_gap}s-{max_edge_gap}s, "
        f"max span: {max_chain_span}s)"
    )
    if chain_count == 0:
        print("No action chains found. Try adjusting --min-edge-gap / --max-edge-gap.")
        return [], sink_meta

    if chains:
        sizes = [len(k) for _, _, k, _ in chains]
        print(f"Chain sizes: mean={np.mean(sizes):.1f} median={np.median(sizes):.0f} "
              f"min={min(sizes)} max={max(sizes)}")

    # Step 2: Find patterns
    if event_session_counts_override is not None:
        event_session_counts = event_session_counts_override
    else:
        event_session_counts = {
            str(en): int(n)
            for en, n in df.groupby("event_name")["session_id"].nunique().to_dict().items()
        }
    action_session_counts = {
        en: event_session_counts.get(en, 0)
        for en in action_events
    }
    patterns, cv_meta = discover_action_interactions(
        chains, total_sessions,
        CASCADE_MIN_LEN, CASCADE_MAX_LEN,
        min_sessions, args.chain_cv_max, args.edge_cv_max,
        action_session_counts, event_session_counts,
        action_events,
        blacklisted_prop_keys=bl,
        min_confidence=float(args.min_confidence),
        min_sessions_exclusive=bool(args.min_sessions_exclusive),
        auto_pattern_cv=bool(args.auto_pattern_cv),
        auto_pattern_cv_percentile=float(args.auto_pattern_cv_percentile),
        auto_pattern_cv_chain_cap=float(args.auto_pattern_cv_chain_cap),
        auto_pattern_cv_edge_cap=float(args.auto_pattern_cv_edge_cap),
        pattern_accumulator=pattern_accumulator,
    )
    sink_meta.update(cv_meta)
    ceff = cv_meta.get("chain_cv_max_effective", args.chain_cv_max)
    eeff = cv_meta.get("edge_cv_max_effective", args.edge_cv_max)
    sess_rule = f"sessions>{min_sessions}" if args.min_sessions_exclusive else f"sessions>={min_sessions}"
    print(
        f"Patterns passing all filters "
        f"({sess_rule}, confidence>{args.min_confidence}, chain_cv<{ceff}, "
        f"edge_cv<{eeff}): {len(patterns)}"
    )
    if cv_meta.get("auto_pattern_cv"):
        print(
            f"  Pattern CV auto: requested chain<={cv_meta.get('chain_cv_max_requested')} "
            f"edge<={cv_meta.get('edge_cv_max_requested')} | "
            f"pctl={cv_meta.get('auto_pattern_cv_percentile')} over "
            f"{cv_meta.get('n_patterns_cv_sample')} candidates -> "
            f"effective chain<={ceff} edge<={eeff}"
        )
    sink_meta["patterns_after_filters"] = len(patterns)

    # Step 3: Dedup
    before = len(patterns)
    patterns = remove_subpatterns(patterns)
    if before != len(patterns):
        print(f"After dedup: {len(patterns)} (removed {before - len(patterns)} sub-patterns)")

    after_dedup_n = len(patterns)
    sink_meta["patterns_after_dedup"] = after_dedup_n
    top_k = int(getattr(args, "top_suggestions", TOP_SUGGESTIONS_DEFAULT))
    patterns = select_diverse_top_suggestions(patterns, top_k)
    sink_meta["suggestions_returned"] = len(patterns)
    if top_k > 0 and after_dedup_n > len(patterns):
        print(
            f"Diversity-ranked top suggestions: {len(patterns)} "
            f"(from {after_dedup_n} after dedup; cap={top_k}, novel event names first)"
        )
    elif top_k > 0:
        print(
            f"Diversity-ranked suggestions: {len(patterns)} "
            f"(cap={top_k}; novel event names first, then score/support)"
        )
    elif top_k <= 0:
        print("Top suggestions cap disabled (--top-suggestions 0); returning all post-dedup patterns.")

    # Print
    print(f"\n{'=' * 90}")
    print(
        "INTERACTIONS (diversity-ranked: new event names prioritized, "
        "then interaction_score / sessions)"
    )
    print(f"{'=' * 90}")

    for i, p in enumerate(patterns[:TOP_N_PRINT]):
        print(f"\n  #{i + 1}  (pick rank {p.get('suggestion_rank', i + 1)}, "
              f"novel_events={p.get('novel_event_count_at_pick', '-')})")
        print(f"  ├─ Sessions:     {p['unique_sessions']} / {total_sessions}  "
              f"({p['session_pct']}% of all sessions)")
        print(f"  ├─ Occurrences:  {p['total_occurrences']}")
        print(f"  ├─ Steps:        {p['length']}")
        print(f"  ├─ Score:        {p.get('interaction_score', 0):.4f} "
              f"(conf={p.get('confidence', 0):.4f}, lift={p.get('lift', 0):.4f})")
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
        if p.get("step_prop_hints"):
            print(f"  ├─ Prop hints:")
            for hint in p["step_prop_hints"]:
                print(f"  │    {hint['event']}: {', '.join(hint['hints'])}")
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

    return patterns, sink_meta


# ═══════════════════════════════════════════════════════════════════════
# CLI
# ═══════════════════════════════════════════════════════════════════════

def _parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__,
                                formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument(
        "--project-id",
        type=str,
        default=None,
        help=(
            "Project id (S3 path segment and X-Project-ID for API). "
            "When set without --json-dir, reads parquet from S3."
        ),
    )
    p.add_argument("--json-dir", type=Path, nargs="*", default=None,
                   help="Local JSONL directories (legacy). Skips S3 when provided.")
    p.add_argument(
        "--aws-profile",
        type=str,
        default=None,
        help=(
            "AWS shared credentials profile for S3 access. "
            "Falls back to AWS_PROFILE env when omitted."
        ),
    )
    p.add_argument("--s3-bucket", type=str, default=DEFAULT_S3_BUCKET,
                   help=f"S3 bucket for parquet input (default {DEFAULT_S3_BUCKET}).")
    p.add_argument("--s3-table", type=str, default=DEFAULT_S3_TABLE,
                   help=f"S3 table folder under project id (default {DEFAULT_S3_TABLE}).")
    p.add_argument("--lookback-days", type=int, default=DEFAULT_LOOKBACK_DAYS,
                   help=f"Number of calendar days of partitions to read (default {DEFAULT_LOOKBACK_DAYS}).")
    p.add_argument(
        "--end-date",
        type=str,
        default=None,
        help="Last partition date (YYYY-MM-DD, UTC). Default: today UTC.",
    )
    p.add_argument(
        "--s3-output",
        type=str,
        default=None,
        help=(
            "S3 URI prefix for JSON results, e.g. "
            "s3://pulse-otel-ingestion/fancode/suggested_interactions/"
        ),
    )
    p.add_argument(
        "--no-s3-chunked",
        action="store_true",
        help="Load all S3 partitions into memory at once (may OOM on large ranges).",
    )
    p.add_argument("--min-edge-gap", type=float, default=MIN_EDGE_GAP,
                   help=f"Min gap between events in seconds (default {MIN_EDGE_GAP})")
    p.add_argument("--max-edge-gap", type=float, default=MAX_EDGE_GAP,
                   help=f"Max gap between events in seconds (default {MAX_EDGE_GAP})")
    p.add_argument("--max-chain-span", type=float, default=MAX_CHAIN_SPAN,
                   help=f"Max total chain duration in seconds (default {MAX_CHAIN_SPAN})")
    p.add_argument("--min-sessions", type=int, default=MIN_SESSIONS,
                   help=f"Min sessions for a pattern (default {MIN_SESSIONS})")
    p.add_argument(
        "--min-sessions-exclusive",
        action="store_true",
        help="Require unique_sessions > --min-sessions (strict), not >=.",
    )
    p.add_argument(
        "--min-confidence",
        type=float,
        default=MIN_CONFIDENCE,
        help=(
            "Drop patterns with confidence <= this value "
            f"(default {MIN_CONFIDENCE}; use 0.7 to keep only >70% action-session coverage)."
        ),
    )
    p.add_argument("--chain-cv-max", type=float, default=CHAIN_CV_MAX,
                   help=f"Max CV for total chain span (default {CHAIN_CV_MAX})")
    p.add_argument("--edge-cv-max", type=float, default=EDGE_CV_MAX,
                   help=f"Max CV for each edge (default {EDGE_CV_MAX}); floor when --auto-pattern-cv is on.")
    p.add_argument(
        "--auto-pattern-cv",
        action=argparse.BooleanOptionalAction,
        default=True,
        help=(
            "Widen chain/edge CV caps using the P-th percentile of CVs among patterns "
            "that meet min-sessions (caps never go below --chain-cv-max / --edge-cv-max). "
            "Default: on. Use --no-auto-pattern-cv for fixed caps only."
        ),
    )
    p.add_argument(
        "--auto-pattern-cv-percentile",
        type=float,
        default=AUTO_PATTERN_CV_PERCENTILE,
        help="Percentile (0-100) of observed pattern CVs used to raise CV ceilings.",
    )
    p.add_argument(
        "--auto-pattern-cv-chain-cap",
        type=float,
        default=AUTO_PATTERN_CV_CHAIN_CAP,
        help="Hard maximum for auto-derived chain CV cap.",
    )
    p.add_argument(
        "--auto-pattern-cv-edge-cap",
        type=float,
        default=AUTO_PATTERN_CV_EDGE_CAP,
        help="Hard maximum for auto-derived edge CV cap.",
    )
    p.add_argument("--max-prop-keys", type=int, default=MAX_DYNAMIC_PROP_KEYS,
                   help=f"Max prop keys to include (0 = no cap, default {MAX_DYNAMIC_PROP_KEYS})")
    p.add_argument("--max-prop-cardinality", type=int, default=MAX_PROP_CARDINALITY,
                   help="Deprecated; upper cardinality cap is no longer applied.")
    p.add_argument("--profile-events", type=int, default=PROFILE_MAX_EVENTS)
    p.add_argument(
        "--profile-sessions",
        type=int,
        default=CHUNKED_PROFILE_MAX_SESSIONS,
        help=(
            "Chunked S3 pass 1: reservoir-sample this many unique sessions, then "
            "use all events from those sessions for prop blacklist and key profiling."
        ),
    )
    p.add_argument(
        "--pulse-type-filter",
        type=str,
        default=DEFAULT_PULSE_TYPE_FILTER,
        help=(
            'Global filter: only events whose pulse.type prop matches this value '
            f'(default: {DEFAULT_PULSE_TYPE_FILTER!r}). Pass empty string to disable.'
        ),
    )
    p.add_argument("--action-top-k", type=int, default=ACTION_TOP_K,
                   help=f"Top inferred action events to use as anchors (default {ACTION_TOP_K})")
    p.add_argument("--action-min-sessions", type=int, default=ACTION_MIN_SESSIONS,
                   help=f"Minimum sessions for action inference candidate events (default {ACTION_MIN_SESSIONS})")
    p.add_argument("--action-print-top", type=int, default=ACTION_PRINT_TOP,
                   help=f"How many inferred actions to print (default {ACTION_PRINT_TOP})")
    p.add_argument("--auto-thresholds", action="store_true",
                   help="Auto-derive edge-gap/chain-span thresholds from tenant data.")
    p.add_argument("--min-session-pct", type=float, default=AUTO_MIN_SESSION_PCT,
                   help="Raise min pattern support to ceil(pct/100 * total_sessions) (0=off).")
    p.add_argument(
        "--no-prop-session-blacklist",
        action="store_true",
        help="Disable global prop blacklist built from per-session prop cardinality.",
    )
    p.add_argument(
        "--prop-blacklist-sessions-per-event",
        type=int,
        default=PROP_BLACKLIST_SESSIONS_PER_EVENT,
        help="Sessions to sample per event_name when building prop blacklist.",
    )
    p.add_argument(
        "--prop-blacklist-max-event-types",
        type=int,
        default=PROP_BLACKLIST_MAX_EVENT_TYPES,
        help="Cap distinct event_names when building blacklist (0 = no cap).",
    )
    p.add_argument(
        "--prop-blacklist-low-frac",
        type=float,
        default=SESSION_CONSTANT_THRESHOLD,
        help="Blacklist prop key if this fraction of (session,key) samples have card<=1.",
    )
    p.add_argument(
        "--prop-blacklist-min-samples",
        type=int,
        default=PROP_BLACKLIST_MIN_KEY_SAMPLES,
        help="Min (session,key) observations before a key can be blacklisted.",
    )
    p.add_argument(
        "--top-suggestions",
        type=int,
        default=TOP_SUGGESTIONS_DEFAULT,
        help=(
            "Max interactions in output after dedup: greedy rank favors patterns "
            "that introduce new event names vs. already-chosen patterns, then "
            "interaction_score / session support. Use 0 for no cap."
        ),
    )
    p.add_argument("--output", type=Path, default=None)
    p.add_argument(
        "--api-integrate",
        action="store_true",
        help=(
            "Enable API pre/post-processing: fetch existing interactions and suggestions, "
            "dedupe mined patterns, cap to --top-suggestions, optionally POST results."
        ),
    )
    p.add_argument("--api-base-url", type=str, default=None, help="Pulse API base URL.")
    p.add_argument("--auth-token", type=str, default=None, help="Bearer token for Authorization.")
    p.add_argument(
        "--api-push",
        action="store_true",
        help="POST final suggestions to /v1/interactions/suggestions (requires --api-integrate).",
    )
    p.add_argument(
        "--api-replace-pending",
        action="store_true",
        default=True,
        help="Replace existing PENDING suggestions when pushing (default: true).",
    )
    p.add_argument(
        "--no-api-replace-pending",
        action="store_false",
        dest="api_replace_pending",
        help="Append suggestions without deleting existing PENDING rows.",
    )
    return p.parse_args()


def _resolve_api_config(args: argparse.Namespace) -> PulseApiConfig:
    import os

    base_url = args.api_base_url or os.environ.get("PULSE_API_BASE_URL", "")
    project_id = args.project_id or os.environ.get("PULSE_PROJECT_ID", "")
    auth_token = args.auth_token or os.environ.get("PULSE_AUTH_TOKEN", "")
    if not base_url or not project_id or not auth_token:
        raise SystemExit(
            "API mode requires --api-base-url, --project-id, --auth-token "
            "or PULSE_API_BASE_URL, PULSE_PROJECT_ID, PULSE_AUTH_TOKEN."
        )
    return PulseApiConfig(
        base_url=base_url.rstrip("/"),
        project_id=project_id,
        auth_token=auth_token,
    )


def run_api_preprocessing(client: PulseInteractionApiClient) -> dict[str, Any]:
    interactions = client.get_interaction_configs()
    catalog = client.get_suggestion_catalog()
    by_status: Counter[str] = Counter(str(s.get("status", "UNKNOWN")) for s in catalog)
    print(
        f"\n>>> API pre-check: {len(interactions)} active interaction(s), "
        f"{len(catalog)} catalog suggestion(s) "
        f"(PENDING={by_status.get('PENDING', 0)}, "
        f"DISMISSED={by_status.get('DISMISSED', 0)}, "
        f"ACTIVATED={by_status.get('ACTIVATED', 0)})"
    )
    return {"interactions": interactions, "catalog": catalog, "by_status": dict(by_status)}


def run_api_postprocessing(
    patterns: list[dict[str, Any]],
    *,
    client: PulseInteractionApiClient,
    top_k: int,
    preloaded: dict[str, Any] | None = None,
) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    preload = preloaded or run_api_preprocessing(client)
    interactions = preload["interactions"]
    catalog = preload["catalog"]
    filtered, filter_stats = filter_patterns_against_existing(
        patterns,
        interactions=interactions,
        catalog_suggestions=catalog,
    )
    print(
        f">>> API post-filter: removed {filter_stats['excluded_interactions']} "
        f"interaction duplicate(s), {filter_stats['excluded_suggestions']} "
        f"suggestion duplicate(s); {filter_stats['kept']} candidate(s) remain"
    )
    final = select_diverse_top_suggestions(filtered, top_k)
    meta = {
        "api_filter_stats": filter_stats,
        "suggestions_after_api_filter": len(filtered),
        "suggestions_returned": len(final),
    }
    return final, meta


def _parse_end_date_arg(value: str | None) -> date | None:
    if not value:
        return None
    return date.fromisoformat(value.strip())


def _use_s3_input(args: argparse.Namespace) -> bool:
    return bool(args.project_id) and not args.json_dir


def _resolve_pulse_type_filter_arg(value: str | None) -> str | None:
    if value is None:
        return DEFAULT_PULSE_TYPE_FILTER
    text = str(value).strip()
    return text or None


def main() -> None:
    args = _parse_args()
    pulse_type_filter = _resolve_pulse_type_filter_arg(args.pulse_type_filter)
    run_ts = datetime.now(timezone.utc)
    aws_profile = resolve_aws_profile(getattr(args, "aws_profile", None))
    s3_input_meta: dict[str, Any] = {}
    partition_uris: list[str] = []
    data_start: date | None = None
    data_end: date | None = None

    if _use_s3_input(args):
        if aws_profile:
            print(f">>> AWS profile: {aws_profile}")
        s3_input = S3InputConfig(
            project_id=args.project_id.strip(),
            bucket=args.s3_bucket.strip(),
            table=args.s3_table.strip(),
            lookback_days=max(1, int(args.lookback_days)),
            end_date=_parse_end_date_arg(args.end_date),
        )
        partition_uris, data_start, data_end = list_existing_partition_uris(
            s3_input, aws_profile=aws_profile
        )
        print(
            f"\n>>> S3 input: project={s3_input.project_id} "
            f"partitions={len(partition_uris)} "
            f"({data_start} .. {data_end})"
        )
        for uri in partition_uris:
            print(f"    {uri}")
        s3_input_meta = {
            "bucket": s3_input.bucket,
            "table": s3_input.table,
            "lookback_days": s3_input.lookback_days,
            "aws_profile": aws_profile,
            "partition_uris": partition_uris,
            "data_start": str(data_start),
            "data_end": str(data_end),
            "chunked": not args.no_s3_chunked,
            "pulse_type_filter": pulse_type_filter,
        }
        if pulse_type_filter:
            print(f">>> Pulse type filter: {pulse_type_filter!r} (global)")
    else:
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
            raise SystemExit(
                "No valid input: pass --project-id for S3 parquet or --json-dir for local JSONL."
            )
        if pulse_type_filter:
            print(f">>> Pulse type filter: {pulse_type_filter!r} (global)")
        df = load_events(
            json_dirs,
            prop_keys_by_event={},
            pulse_type_filter=pulse_type_filter,
        )

    pattern_accumulator: PatternDiscoveryAccumulator | None = None
    total_sessions_override: int | None = None
    event_session_counts_override: dict[str, int] | None = None

    if _use_s3_input(args) and not args.no_s3_chunked:
        chunked_meta = run_chunked_s3_workflow(
            partition_uris,
            args=args,
            project_id=args.project_id.strip(),
            aws_profile=aws_profile,
            parse_props_fn=parse_props,
        )
        df = pd.DataFrame(columns=["session_id", "timestamp", "event_name", "props", "event_key"])
        prop_blacklist = chunked_meta["prop_blacklist"]
        prop_blacklist_stats = chunked_meta["prop_blacklist_stats"]
        prop_keys_by_event = chunked_meta["prop_keys_by_event"]
        inferred_actions_top = chunked_meta["inferred_actions_top"]
        inferred_noise_events = chunked_meta["inferred_noise_events"]
        runtime_params = chunked_meta["runtime_params"]
        pattern_accumulator = chunked_meta["pattern_accumulator"]
        total_sessions_override = int(chunked_meta["total_sessions"])
        event_session_counts_override = chunked_meta["event_session_counts"]
        print(f"\n>>> Chunked S3 load complete ({total_sessions_override:,} sessions)")
    elif _use_s3_input(args):
        df = load_events_parquet(
            partition_uris,
            project_id=args.project_id.strip(),
            parse_props_fn=parse_props,
            aws_profile=aws_profile,
            pulse_type_filter=pulse_type_filter,
        )
        print(f"Loaded {len(df)} events (non-chunked S3; prop keys deferred)")
        prop_blacklist = frozenset()
        prop_blacklist_stats: dict[str, Any] = {}
        if not args.no_prop_session_blacklist:
            prop_blacklist, prop_blacklist_stats = build_global_prop_blacklist_from_sessions(
                df,
                sessions_per_event=max(1, args.prop_blacklist_sessions_per_event),
                max_event_types=max(0, args.prop_blacklist_max_event_types),
                low_card_frac=float(args.prop_blacklist_low_frac),
                min_key_samples=max(1, args.prop_blacklist_min_samples),
                seed=PROP_BLACKLIST_SEED,
            )
        profile_sample = _sample_events_from_df(
            df, max(1, args.profile_events), PROFILE_SAMPLE_SPREAD,
        )
        prop_keys_by_event = profile_and_select_prop_keys(
            sampled_rows=profile_sample,
            max_profile_events=max(1, args.profile_events),
            max_cardinality=args.max_prop_cardinality,
            max_keys=max(0, args.max_prop_keys),
            spread=PROFILE_SAMPLE_SPREAD,
            blacklisted_prop_keys=prop_blacklist,
        )
        df["event_key"] = [
            build_event_key(str(en), props if isinstance(props, dict) else {}, prop_keys_by_event)
            for en, props in zip(df["event_name"], df["props"])
        ]
        inferred_action_events, inferred_actions_top, inferred_noise_events = infer_action_events(
            df, top_k=max(1, args.action_top_k), min_sessions=max(1, args.action_min_sessions),
        )
        runtime_params = derive_runtime_params(df, inferred_action_events, args)
    else:
        print(f"Loaded {len(df)} events (prop keys deferred until after blacklist + profiling)")
        prop_blacklist = frozenset()
        prop_blacklist_stats = {}
        if not args.no_prop_session_blacklist:
            prop_blacklist, prop_blacklist_stats = build_global_prop_blacklist_from_sessions(
                df,
                sessions_per_event=max(1, args.prop_blacklist_sessions_per_event),
                max_event_types=max(0, args.prop_blacklist_max_event_types),
                low_card_frac=float(args.prop_blacklist_low_frac),
                min_key_samples=max(1, args.prop_blacklist_min_samples),
                seed=PROP_BLACKLIST_SEED,
            )
        prop_keys_by_event = profile_and_select_prop_keys(
            json_dirs,
            max_profile_events=max(1, args.profile_events),
            max_cardinality=args.max_prop_cardinality,
            max_keys=max(0, args.max_prop_keys),
            spread=PROFILE_SAMPLE_SPREAD,
            blacklisted_prop_keys=prop_blacklist,
        )
        df["event_key"] = [
            build_event_key(str(en), props if isinstance(props, dict) else {}, prop_keys_by_event)
            for en, props in zip(df["event_name"], df["props"])
        ]
        inferred_action_events, inferred_actions_top, inferred_noise_events = infer_action_events(
            df, top_k=max(1, args.action_top_k), min_sessions=max(1, args.action_min_sessions),
        )
        runtime_params = derive_runtime_params(df, inferred_action_events, args)

    print(f"\n>>> Event types with selected prop keys: {len(prop_keys_by_event)}")
    print(f">>> Auto-inferred action anchors: {len(inferred_actions_top)}")
    pipeline_args = args
    api_client: PulseInteractionApiClient | None = None
    api_preload: dict[str, Any] | None = None
    if args.api_integrate:
        api_client = PulseInteractionApiClient(_resolve_api_config(args))
        api_preload = run_api_preprocessing(api_client)
        if args.top_suggestions > 0:
            pipeline_args = argparse.Namespace(**{**vars(args), "top_suggestions": 0})

    patterns, suggestion_meta = run_pipeline(
        df,
        pipeline_args,
        inferred_actions_top,
        inferred_noise_events,
        runtime_params,
        global_prop_blacklist=prop_blacklist,
        pattern_accumulator=pattern_accumulator,
        total_sessions_override=total_sessions_override,
        event_session_counts_override=event_session_counts_override,
    )

    if args.api_integrate and api_client is not None:
        top_k = max(1, int(args.top_suggestions)) if args.top_suggestions > 0 else TOP_SUGGESTIONS_DEFAULT
        patterns, api_meta = run_api_postprocessing(
            patterns,
            client=api_client,
            top_k=top_k,
            preloaded=api_preload,
        )
        suggestion_meta.update(api_meta)
        if args.api_push:
            payload = [pattern_to_api_suggestion(p) for p in patterns]
            result = api_client.post_suggestions(
                payload,
                replace_pending=bool(args.api_replace_pending),
            )
            print(
                f"\n>>> API push: created {result.get('createdCount', 0)} suggestion(s), "
                f"replaced {result.get('replacedPendingCount', 0)} pending row(s)"
            )
            suggestion_meta["api_push_result"] = result

    output_payload: dict[str, Any] | None = None
    if args.output or args.s3_output or _use_s3_input(args):
        output_payload = {
            "run": {
                "timestamp_utc": run_ts.isoformat(),
                "project_id": args.project_id,
                "input_mode": "s3" if _use_s3_input(args) else "json",
            },
            "config": {
                "min_edge_gap": args.min_edge_gap,
                "max_edge_gap": args.max_edge_gap,
                "max_chain_span": args.max_chain_span,
                "min_sessions": args.min_sessions,
                "min_sessions_exclusive": bool(args.min_sessions_exclusive),
                "min_confidence": float(args.min_confidence),
                "chain_cv_max": args.chain_cv_max,
                "edge_cv_max": args.edge_cv_max,
                "auto_pattern_cv": args.auto_pattern_cv,
                "auto_pattern_cv_percentile": args.auto_pattern_cv_percentile,
                "auto_pattern_cv_chain_cap": args.auto_pattern_cv_chain_cap,
                "auto_pattern_cv_edge_cap": args.auto_pattern_cv_edge_cap,
                "pattern_cv": {
                    "auto_pattern_cv": suggestion_meta.get("auto_pattern_cv"),
                    "auto_pattern_cv_percentile": suggestion_meta.get("auto_pattern_cv_percentile"),
                    "chain_cv_max_requested": suggestion_meta.get("chain_cv_max_requested"),
                    "edge_cv_max_requested": suggestion_meta.get("edge_cv_max_requested"),
                    "chain_cv_max_effective": suggestion_meta.get("chain_cv_max_effective"),
                    "edge_cv_max_effective": suggestion_meta.get("edge_cv_max_effective"),
                    "n_patterns_cv_sample": suggestion_meta.get("n_patterns_cv_sample"),
                    "chain_cv_percentile_raw": suggestion_meta.get("chain_cv_percentile_raw"),
                    "edge_cv_percentile_raw": suggestion_meta.get("edge_cv_percentile_raw"),
                },
                "auto_thresholds": args.auto_thresholds,
                "min_session_pct": args.min_session_pct,
                "action_top_k": args.action_top_k,
                "action_min_sessions": args.action_min_sessions,
                "top_suggestions": args.top_suggestions,
                "api_integrate": args.api_integrate,
                "api_push": args.api_push,
                "project_id": args.project_id,
                "s3_bucket": args.s3_bucket if _use_s3_input(args) else None,
                "s3_table": args.s3_table if _use_s3_input(args) else None,
                "lookback_days": args.lookback_days if _use_s3_input(args) else None,
                "profile_sessions": getattr(args, "profile_sessions", None)
                if _use_s3_input(args) and not args.no_s3_chunked
                else None,
                "pulse_type_filter": pulse_type_filter,
                "aws_profile": aws_profile,
                "s3_input": s3_input_meta if _use_s3_input(args) else None,
                "s3_chunked": _use_s3_input(args) and not args.no_s3_chunked,
                "suggestion_ranking": "diversity_greedy_by_event_names",
                "patterns_after_filters": suggestion_meta.get("patterns_after_filters"),
                "patterns_after_dedup": suggestion_meta.get("patterns_after_dedup"),
                "suggestions_returned": suggestion_meta.get("suggestions_returned"),
                "global_prop_blacklist": sorted(prop_blacklist),
                "prop_blacklist_stats": prop_blacklist_stats,
                "prop_keys_by_event": {
                    en: list(keys) for en, keys in prop_keys_by_event.items()
                },
                "inferred_actions_top": inferred_actions_top,
                "inferred_noise_events": inferred_noise_events,
                "runtime_params": runtime_params,
            },
            "total_events": len(df),
            "total_sessions": int(df["session_id"].nunique()),
            "interactions": patterns,
        }

    if output_payload and args.output:
        args.output.write_text(json.dumps(output_payload, indent=2, default=str))
        print(f"\nResults written to {args.output}")

    if output_payload and (args.s3_output or _use_s3_input(args)):
        if not args.project_id:
            raise SystemExit("--project-id is required for S3 output.")
        if args.s3_output:
            out_bucket, out_prefix = parse_s3_output_uri(args.s3_output, args.project_id)
        else:
            out_bucket = args.s3_bucket
            out_prefix = default_output_prefix(args.project_id)
        end_d = data_end or resolve_end_date(_parse_end_date_arg(args.end_date))
        start_d = data_start or (
            iter_lookback_dates(end_d, max(1, int(args.lookback_days)))[0]
        )
        s3_out = S3OutputConfig(
            project_id=args.project_id,
            bucket=out_bucket,
            output_prefix=out_prefix,
            run_timestamp=run_ts,
            start_date=start_d,
            end_date=end_d,
            suggestion_count=len(patterns),
        )
        written_uri = write_json_to_s3(output_payload, s3_out, aws_profile=aws_profile)
        print(f"\nResults written to {written_uri}")


if __name__ == "__main__":
    main()
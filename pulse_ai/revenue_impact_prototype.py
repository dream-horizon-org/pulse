#!/usr/bin/env python3
"""
Pulse - Causal Revenue Impact Prototype
========================================
Tests the hypothesis that causal analysis on auto-instrumented telemetry
can reveal revenue leakage.

Connects to ClickHouse, pulls session data, auto-discovers conversion
proxies, and runs propensity score matching for each technical issue.

Usage:
    python revenue_impact_prototype.py --project-id fancode
    python revenue_impact_prototype.py --project-id fancode --lookback-days 60
"""

import argparse
import os
import sys
import warnings
from collections import Counter
from dataclasses import dataclass, field
from typing import Optional

import numpy as np
import pandas as pd
from dotenv import load_dotenv
from sklearn.linear_model import LogisticRegression
from sklearn.neighbors import NearestNeighbors
from sklearn.preprocessing import LabelEncoder
from tabulate import tabulate

warnings.filterwarnings("ignore")

# ═══════════════════════════════════════════════════════════════════
# Data Models
# ═══════════════════════════════════════════════════════════════════

@dataclass
class IssueAnalysis:
    issue_type: str           # crash, anr, non_fatal, network_error, jank
    issue_label: str          # human-readable name
    screen_name: str
    affected_count: int
    control_count: int
    affected_conversion_rate: float
    control_conversion_rate: float
    conversion_delta: float   # control - affected (positive = issue hurts conversion)
    ci_lower: float
    ci_upper: float
    is_significant: bool
    priority_score: float

@dataclass
class ConversionProxy:
    proxy_type: str           # graphql_op, url_path, session_depth
    identifier: str           # operation name or URL
    sessions_reached: int
    total_sessions: int
    conversion_rate: float

@dataclass
class FunnelStep:
    screen_name: str
    sessions_at_step: int
    sessions_total: int
    dropoff_rate: float       # fraction that leave at this step
    conversion_rate: float    # fraction of sessions at this step that eventually convert
    is_bottleneck: bool       # significantly worse than expected


# ═══════════════════════════════════════════════════════════════════
# ClickHouse Connection
# ═══════════════════════════════════════════════════════════════════

def get_ch_client():
    """Create ClickHouse client from .env credentials."""
    import clickhouse_connect

    ch_host = os.getenv("CLICKHOUSE_HOST", "localhost")
    ch_host = ch_host.replace("https://", "").replace("http://", "").rstrip("/")

    return clickhouse_connect.get_client(
        host=ch_host,
        port=int(os.getenv("CLICKHOUSE_PORT", "8123")),
        username=os.getenv("CLICKHOUSE_USER", "default"),
        password=os.getenv("CLICKHOUSE_PASSWORD", ""),
        database=os.getenv("CLICKHOUSE_DATABASE", "otel"),
    )


# ═══════════════════════════════════════════════════════════════════
# Section 1: Build Session Feature Vectors
# ═══════════════════════════════════════════════════════════════════

def build_session_profiles(client, project_id: str, lookback_days: int) -> pd.DataFrame:
    """Pull session-level features from otel_traces."""

    query = """
    SELECT
        SessionId,
        any(UserId) as user_id,
        any(DeviceModel) as device_model,
        any(OsVersion) as os_version,
        any(AppVersion) as app_version,
        any(Platform) as platform,
        any(GeoCountry) as geo_country,
        any(NetworkProvider) as network_provider,
        toHour(min(Timestamp)) as session_hour,
        -- Screen metrics
        count(DISTINCT CASE
            WHEN PulseType IN ('screen_session', 'screen_load')
            THEN SpanAttributes['screen.name']
            END) as unique_screens,
        -- Network metrics
        countIf(PulseType LIKE 'network.%%') as total_network_calls,
        countIf(PulseType LIKE 'network.4%%' OR PulseType LIKE 'network.5%%') as net_error_count,
        countIf(PulseType = 'network.0') as net_timeout_count,
        -- Duration
        dateDiff('second', min(Timestamp), max(Timestamp)) as session_duration_sec
    FROM otel_traces
    WHERE ProjectId = %(pid)s
      AND Timestamp >= now() - INTERVAL %(days)s DAY
      AND SessionId != ''
    GROUP BY SessionId
    HAVING unique_screens > 0
    """
    r = client.query(query, parameters={"pid": project_id, "days": lookback_days})
    cols = [c[0] for c in r.column_names_with_types] if hasattr(r, 'column_names_with_types') else [
        "session_id", "user_id", "device_model", "os_version", "app_version",
        "platform", "geo_country", "network_provider", "session_hour",
        "unique_screens", "total_network_calls", "net_error_count",
        "net_timeout_count", "session_duration_sec"
    ]
    df = pd.DataFrame(r.result_rows, columns=cols)
    # Normalize column names
    df.columns = [c.lower().replace("sessionid", "session_id") for c in df.columns]
    if "sessionid" in df.columns:
        df = df.rename(columns={"sessionid": "session_id"})
    return df


def get_crash_anr_sessions(client, project_id: str, lookback_days: int) -> pd.DataFrame:
    """Pull crash/ANR/non-fatal data from stack_trace_events."""

    query = """
    SELECT
        SessionId as session_id,
        PulseType as pulse_type,
        ScreenName as screen_name,
        ExceptionType as exception_type,
        count() as event_count
    FROM stack_trace_events
    WHERE ProjectId = %(pid)s
      AND Timestamp >= now() - INTERVAL %(days)s DAY
      AND SessionId != ''
    GROUP BY session_id, pulse_type, screen_name, exception_type
    """
    r = client.query(query, parameters={"pid": project_id, "days": lookback_days})
    return pd.DataFrame(r.result_rows, columns=["session_id", "pulse_type", "screen_name", "exception_type", "event_count"])


# ═══════════════════════════════════════════════════════════════════
# Section 1b: Pull otel_logs signals (jank, clicks, lifecycle)
# ═══════════════════════════════════════════════════════════════════

def get_logs_session_signals(client, project_id: str, lookback_days: int) -> pd.DataFrame:
    """Pull per-session signals from otel_logs: jank, clicks, network changes, lifecycle."""

    query = """
    SELECT
        SessionId as session_id,
        -- Jank signals
        countIf(PulseType = 'app.jank.slow') as jank_slow_count,
        countIf(PulseType = 'app.jank.frozen') as jank_frozen_count,
        -- Click signals
        countIf(PulseType = 'app.click') as click_count,
        -- Network stability
        countIf(PulseType = 'network.change') as network_change_count,
        -- App lifecycle (background/foreground transitions)
        countIf(PulseType = 'device.app.lifecycle') as lifecycle_event_count,
        -- Session boundaries
        countIf(PulseType = 'session.start') as session_start_count,
        countIf(PulseType = 'session.end') as session_end_count
    FROM otel_logs
    WHERE ProjectId = %(pid)s
      AND Timestamp >= now() - INTERVAL %(days)s DAY
      AND SessionId != ''
    GROUP BY session_id
    """
    r = client.query(query, parameters={"pid": project_id, "days": lookback_days})
    return pd.DataFrame(r.result_rows, columns=[
        "session_id", "jank_slow_count", "jank_frozen_count", "click_count",
        "network_change_count", "lifecycle_event_count",
        "session_start_count", "session_end_count",
    ])


def get_rage_tap_sessions(client, project_id: str, lookback_days: int) -> pd.DataFrame:
    """
    Detect rage taps: 3+ clicks within 2 seconds on the same screen.
    Returns per-session, per-screen rage tap groups.
    """
    query = """
    SELECT
        SessionId as session_id,
        LogAttributes['screen.name'] as screen_name,
        count() as tap_count,
        dateDiff('millisecond', min(Timestamp), max(Timestamp)) as window_ms
    FROM otel_logs
    WHERE ProjectId = %(pid)s
      AND PulseType = 'app.click'
      AND Timestamp >= now() - INTERVAL %(days)s DAY
      AND SessionId != ''
    GROUP BY session_id, screen_name,
             toUnixTimestamp(Timestamp) / 2  -- 2-second windows
    HAVING tap_count >= 3
    """
    r = client.query(query, parameters={"pid": project_id, "days": lookback_days})
    if not r.result_rows:
        return pd.DataFrame(columns=["session_id", "screen_name", "tap_count", "window_ms"])
    return pd.DataFrame(r.result_rows, columns=["session_id", "screen_name", "tap_count", "window_ms"])


def get_jank_by_screen(client, project_id: str, lookback_days: int) -> pd.DataFrame:
    """Get jank events grouped by session and screen for issue-level analysis."""
    query = """
    SELECT
        SessionId as session_id,
        PulseType as pulse_type,
        LogAttributes['screen.name'] as screen_name,
        count() as event_count
    FROM otel_logs
    WHERE ProjectId = %(pid)s
      AND PulseType IN ('app.jank.slow', 'app.jank.frozen')
      AND Timestamp >= now() - INTERVAL %(days)s DAY
      AND SessionId != ''
    GROUP BY session_id, pulse_type, screen_name
    """
    r = client.query(query, parameters={"pid": project_id, "days": lookback_days})
    if not r.result_rows:
        return pd.DataFrame(columns=["session_id", "pulse_type", "screen_name", "event_count"])
    return pd.DataFrame(r.result_rows, columns=["session_id", "pulse_type", "screen_name", "event_count"])


# ═══════════════════════════════════════════════════════════════════
# Section 1c: Composite Frustration Score
#   Ref: Huang, White & Dumais (CHI 2011) "No Clicks, No Problem"
#   Ref: Guo & Agichtein (WWW 2012) "Beyond Dwell Time"
#   Approach: percentile-rank each signal, then weighted sum → 0-100
# ═══════════════════════════════════════════════════════════════════

# Weights reflect severity: crashes > ANR > frozen > errors > jank > rage > instability
FRUSTRATION_WEIGHTS = {
    "has_crash":             30,   # binary: session had a crash
    "has_anr":               25,   # binary: session had an ANR
    "jank_frozen_count":     15,   # frozen frames (>700ms) — very visible
    "net_error_count":       12,   # 4xx/5xx network errors
    "net_timeout_count":     10,   # network timeouts
    "jank_slow_count":        8,   # slow frames (>300ms) — common, less severe
    "rage_tap_count":        15,   # rage taps detected
    "network_change_count":   5,   # connection instability
    "short_session":         10,   # abnormally short session (bottom 15%)
}


def compute_frustration_scores(
    sessions_df: pd.DataFrame,
    crash_anr_df: pd.DataFrame,
    rage_tap_df: pd.DataFrame,
) -> pd.DataFrame:
    """
    Compute a composite frustration score (0-100) per session.

    Method: percentile-rank each signal within the dataset, multiply
    by weight, sum, then normalize to 0-100.
    """
    df = sessions_df[["session_id"]].copy()

    # ── Binary flags from stack_trace_events ──
    crash_sessions = set()
    anr_sessions = set()
    if not crash_anr_df.empty:
        crash_sessions = set(crash_anr_df[crash_anr_df["pulse_type"] == "device.crash"]["session_id"])
        anr_sessions = set(crash_anr_df[crash_anr_df["pulse_type"] == "device.anr"]["session_id"])
    df["has_crash"] = df["session_id"].isin(crash_sessions).astype(float)
    df["has_anr"] = df["session_id"].isin(anr_sessions).astype(float)

    # ── Rage tap count per session ──
    if rage_tap_df is not None and not rage_tap_df.empty:
        rage_per_session = rage_tap_df.groupby("session_id")["tap_count"].sum().reset_index()
        rage_per_session.columns = ["session_id", "rage_tap_count"]
        df = df.merge(rage_per_session, on="session_id", how="left")
        df["rage_tap_count"] = df["rage_tap_count"].fillna(0)
    else:
        df["rage_tap_count"] = 0.0

    # ── Numeric signals from sessions_df ──
    for col in ["jank_slow_count", "jank_frozen_count", "net_error_count",
                "net_timeout_count", "network_change_count"]:
        if col in sessions_df.columns:
            df[col] = sessions_df[col].fillna(0).astype(float).values
        else:
            df[col] = 0.0

    # ── Short session flag (bottom 15% duration) ──
    if "session_duration_sec" in sessions_df.columns:
        threshold = sessions_df["session_duration_sec"].quantile(0.15)
        df["short_session"] = (sessions_df["session_duration_sec"].values <= threshold).astype(float)
    else:
        df["short_session"] = 0.0

    # ── Percentile-rank each signal, multiply by weight, sum ──
    max_possible = sum(FRUSTRATION_WEIGHTS.values())
    raw_scores = np.zeros(len(df))

    for signal, weight in FRUSTRATION_WEIGHTS.items():
        if signal not in df.columns:
            continue
        vals = df[signal].values.astype(float)
        if vals.max() == vals.min():
            # Constant column — either all 0 or all same value
            if vals.max() > 0:
                pct = np.ones(len(vals))  # everyone has it
            else:
                pct = np.zeros(len(vals))  # nobody has it
        else:
            # Percentile rank: 0.0 (lowest) to 1.0 (highest)
            from scipy.stats import rankdata
            pct = (rankdata(vals, method="average") - 1) / (len(vals) - 1)
        raw_scores += pct * weight

    # Normalize to 0-100
    df["frustration_score"] = (raw_scores / max_possible * 100).round(1)

    return df[["session_id", "frustration_score", "has_crash", "has_anr", "rage_tap_count"]]


def print_frustration_summary(sessions_df: pd.DataFrame):
    """Print frustration score distribution and high-frustration sessions."""
    if "frustration_score" not in sessions_df.columns:
        return

    scores = sessions_df["frustration_score"]
    print(f"\n  Frustration Score Distribution (0=calm, 100=max frustration):")
    print(f"    Min: {scores.min():.0f}  |  P25: {scores.quantile(0.25):.0f}  |  "
          f"Median: {scores.median():.0f}  |  P75: {scores.quantile(0.75):.0f}  |  Max: {scores.max():.0f}")

    # Buckets
    buckets = [
        ("Low (0-25)", (scores >= 0) & (scores < 25)),
        ("Medium (25-50)", (scores >= 25) & (scores < 50)),
        ("High (50-75)", (scores >= 50) & (scores < 75)),
        ("Critical (75-100)", (scores >= 75)),
    ]
    for label, mask in buckets:
        n = mask.sum()
        pct = n / len(scores) * 100
        bar = "█" * int(pct / 2)
        print(f"    {label:20s} {n:>4} sessions ({pct:5.1f}%) {bar}")


# ═══════════════════════════════════════════════════════════════════
# Section 1d: Process Mining — Funnel Discovery & Drop-off Analysis
#   Ref: Leemans, Fahland & van der Aalst (BPM 2013) "Inductive Miner"
#   Ref: Jäger et al. (2020) "Process Mining for Digital Customer Journey"
#   Ref: Bolt, de Leoni & van der Aalst (2017) - Variant Analysis
#   Approach: lightweight variant analysis using screen sequences
# ═══════════════════════════════════════════════════════════════════

def get_screen_sequences(client, project_id: str, lookback_days: int) -> pd.DataFrame:
    """
    Pull ordered screen events per session for process mining.
    Returns: session_id, screen_name, timestamp (ordered).
    """
    query = """
    SELECT
        SessionId as session_id,
        SpanAttributes['screen.name'] as screen_name,
        Timestamp as ts
    FROM otel_traces
    WHERE ProjectId = %(pid)s
      AND PulseType IN ('screen_session', 'screen_load')
      AND Timestamp >= now() - INTERVAL %(days)s DAY
      AND SessionId != ''
      AND SpanAttributes['screen.name'] != ''
    ORDER BY session_id, ts
    """
    r = client.query(query, parameters={"pid": project_id, "days": lookback_days})
    return pd.DataFrame(r.result_rows, columns=["session_id", "screen_name", "ts"])


def build_session_traces(screen_events_df: pd.DataFrame) -> dict:
    """
    Build deduplicated screen sequences per session.
    Consecutive duplicates removed (e.g., [A, A, B, B, A] → [A, B, A]).
    Returns: {session_id: [screen1, screen2, ...]}
    """
    traces = {}
    for session_id, group in screen_events_df.groupby("session_id"):
        screens = group.sort_values("ts")["screen_name"].tolist()
        # Remove consecutive duplicates
        deduped = [screens[0]]
        for s in screens[1:]:
            if s != deduped[-1]:
                deduped.append(s)
        traces[session_id] = deduped
    return traces


def discover_funnels(
    traces: dict,
    converted_session_ids: set,
    min_variant_sessions: int = 5,
    top_n_variants: int = 10,
) -> tuple:
    """
    Discover the most common screen-flow variants and analyze
    conversion rates / drop-off at each step.

    Returns: (variants_analysis, funnel_steps)
    """
    # ── 1. Extract variants (screen sequences as tuples) ──
    variant_counter = Counter()
    variant_sessions = {}  # variant_tuple → list of session_ids

    for session_id, screens in traces.items():
        key = tuple(screens)
        variant_counter[key] += 1
        if key not in variant_sessions:
            variant_sessions[key] = []
        variant_sessions[key].append(session_id)

    # ── 2. Top N variants by frequency ──
    top_variants = variant_counter.most_common(top_n_variants)

    variants_analysis = []
    for variant, count in top_variants:
        if count < min_variant_sessions:
            continue
        sessions = variant_sessions[variant]
        conv_count = sum(1 for s in sessions if s in converted_session_ids)
        conv_rate = conv_count / len(sessions) if sessions else 0
        variants_analysis.append({
            "variant": list(variant),
            "sessions": count,
            "converted": conv_count,
            "conversion_rate": conv_rate,
            "session_ids": sessions,
        })

    # ── 3. Build global funnel: step-by-step drop-off ──
    # Find the most common starting screens and trace the dominant path
    all_sessions = list(traces.keys())
    total = len(all_sessions)

    # Count how many sessions visit each screen at each depth
    screen_at_depth = {}  # depth → Counter of screen_name
    max_depth = max((len(v) for v in traces.values()), default=0)

    for session_id, screens in traces.items():
        for depth, screen in enumerate(screens):
            if depth not in screen_at_depth:
                screen_at_depth[depth] = Counter()
            screen_at_depth[depth][screen] += 1

    # Build the "golden path" — most common screen at each depth
    funnel_steps = []
    sessions_remaining = total

    for depth in range(min(max_depth, 15)):  # cap at 15 steps
        if depth not in screen_at_depth:
            break
        most_common_screen, count_at_step = screen_at_depth[depth].most_common(1)[0]

        # How many sessions made it to this depth?
        sessions_at_depth = sum(1 for t in traces.values() if len(t) > depth)

        # Of those at this depth, how many eventually convert?
        converting_at_depth = sum(
            1 for sid, t in traces.items()
            if len(t) > depth and sid in converted_session_ids
        )

        dropoff = 1 - (sessions_at_depth / sessions_remaining) if sessions_remaining > 0 else 0
        conv_rate = converting_at_depth / sessions_at_depth if sessions_at_depth > 0 else 0

        funnel_steps.append(FunnelStep(
            screen_name=most_common_screen,
            sessions_at_step=sessions_at_depth,
            sessions_total=total,
            dropoff_rate=dropoff,
            conversion_rate=conv_rate,
            is_bottleneck=False,  # set below
        ))
        sessions_remaining = sessions_at_depth

    # ── 4. Mark bottlenecks: steps with abnormally high drop-off ──
    if len(funnel_steps) >= 2:
        dropoffs = [s.dropoff_rate for s in funnel_steps[1:]]  # skip step 0 (entry)
        if dropoffs:
            avg_dropoff = np.mean(dropoffs)
            std_dropoff = np.std(dropoffs)
            for step in funnel_steps[1:]:
                if step.dropoff_rate > avg_dropoff + std_dropoff:
                    step.is_bottleneck = True

    return variants_analysis, funnel_steps


def analyze_dropoff_issues(
    traces: dict,
    sessions_df: pd.DataFrame,
    converted_session_ids: set,
    funnel_steps: list,
) -> list[IssueAnalysis]:
    """
    For each bottleneck step in the funnel, run causal analysis:
    sessions that dropped off at that step vs sessions that continued.
    """
    results = []

    for i, step in enumerate(funnel_steps):
        if not step.is_bottleneck:
            continue
        if i + 1 >= len(funnel_steps):
            continue

        # Sessions that reached this step but NOT the next
        dropped_ids = set()
        continued_ids = set()

        for session_id, screens in traces.items():
            if len(screens) <= i:
                continue  # didn't reach this step
            # Check if they got to depth i but not i+1
            if len(screens) == i + 1:
                dropped_ids.add(session_id)
            elif len(screens) > i + 1:
                continued_ids.add(session_id)

        if len(dropped_ids) < 3:
            continue

        label = f"Drop-off at step {i+1}: {step.screen_name}"

        analysis = propensity_score_matching(
            sessions_df,
            dropped_ids,
            converted_session_ids,
            k=min(3, max(1, len(sessions_df) // 10)),
            min_affected=3,
            min_control=5,
        )
        if analysis:
            analysis.issue_type = "funnel_dropoff"
            analysis.issue_label = label
            analysis.screen_name = step.screen_name
            results.append(analysis)

    return results


def print_process_mining_report(
    variants_analysis: list,
    funnel_steps: list,
    converted_session_ids: set,
    total_sessions: int,
):
    """Print the process mining / funnel discovery report."""

    # ── Funnel visualization ──
    print(f"\n  {'─'*70}")
    print(f"  FUNNEL ANALYSIS (Golden Path)")
    print(f"  {'─'*70}")

    if not funnel_steps:
        print("    No funnel steps discovered.")
        return

    for i, step in enumerate(funnel_steps):
        pct = step.sessions_at_step / step.sessions_total * 100 if step.sessions_total > 0 else 0
        bar_len = int(pct / 2)
        bar = "█" * bar_len + "░" * (50 - bar_len)

        bottleneck = " ⚠ BOTTLENECK" if step.is_bottleneck else ""
        dropoff_str = f"  (-{step.dropoff_rate:.0%} drop)" if i > 0 else ""

        print(f"    Step {i+1}: {step.screen_name}")
        print(f"           {bar} {step.sessions_at_step:>4}/{step.sessions_total} ({pct:.0f}%){dropoff_str}{bottleneck}")
        print(f"           Conv. rate at this depth: {step.conversion_rate:.1%}")

    # ── Top variants ──
    print(f"\n  {'─'*70}")
    print(f"  TOP USER JOURNEY VARIANTS")
    print(f"  {'─'*70}")

    if not variants_analysis:
        print("    No significant variants found.")
        return

    table_data = []
    for va in variants_analysis[:10]:
        path_str = " → ".join(va["variant"][:6])
        if len(va["variant"]) > 6:
            path_str += f" → ...({len(va['variant'])} steps)"
        table_data.append([
            path_str,
            va["sessions"],
            f"{va['sessions'] / total_sessions:.1%}",
            f"{va['conversion_rate']:.1%}",
        ])

    headers = ["Journey Path", "Sessions", "% of Total", "Conv. Rate"]
    print(tabulate(table_data, headers=headers, tablefmt="simple"))

    # ── Compare converting vs non-converting journey lengths ──
    if variants_analysis:
        conv_lengths = []
        nonconv_lengths = []
        for va in variants_analysis:
            for sid in va["session_ids"]:
                if sid in converted_session_ids:
                    conv_lengths.append(len(va["variant"]))
                else:
                    nonconv_lengths.append(len(va["variant"]))

        if conv_lengths and nonconv_lengths:
            print(f"\n  Journey Length Comparison:")
            print(f"    Converting sessions:     avg {np.mean(conv_lengths):.1f} screens (median {np.median(conv_lengths):.0f})")
            print(f"    Non-converting sessions: avg {np.mean(nonconv_lengths):.1f} screens (median {np.median(nonconv_lengths):.0f})")


# ═══════════════════════════════════════════════════════════════════
# Section 2: Auto-Discover Conversion Proxies
# ═══════════════════════════════════════════════════════════════════

# GraphQL operations that typically indicate revenue/subscription/payment intent
CONVERSION_KEYWORDS = [
    "payment", "pay", "purchase", "order", "checkout", "subscribe",
    "entitlement", "transaction", "billing", "cart", "buy",
    "redeem", "coupon", "promo", "reward",
]

ENGAGEMENT_KEYWORDS = [
    "watchlist", "follow", "preference", "notification", "profile",
    "review", "feedback", "share", "invite",
]


def discover_conversion_proxies(client, project_id: str, lookback_days: int, total_sessions: int) -> list[ConversionProxy]:
    """Discover conversion signals from GraphQL operation names and URL patterns."""

    # Strategy 1: GraphQL operation names
    query = """
    SELECT
        SpanAttributes['http.request.header.operation_name'] as op_name,
        SpanAttributes['http.method'] as method,
        uniqCombined64(SessionId) as unique_sessions,
        countIf(PulseType LIKE 'network.2%%') as success_count,
        count() as total_calls
    FROM otel_traces
    WHERE ProjectId = %(pid)s
      AND PulseType LIKE 'network.%%'
      AND Timestamp >= now() - INTERVAL %(days)s DAY
      AND SpanAttributes['http.request.header.operation_name'] != ''
    GROUP BY op_name, method
    HAVING unique_sessions >= 3
    ORDER BY unique_sessions DESC
    """
    r = client.query(query, parameters={"pid": project_id, "days": lookback_days})
    ops = pd.DataFrame(r.result_rows, columns=["op_name", "method", "unique_sessions", "success_count", "total_calls"])

    proxies = []

    # Find conversion-related operations
    for _, row in ops.iterrows():
        op_lower = row["op_name"].lower()
        is_conversion = any(kw in op_lower for kw in CONVERSION_KEYWORDS)
        is_engagement = any(kw in op_lower for kw in ENGAGEMENT_KEYWORDS)

        if is_conversion:
            proxies.append(ConversionProxy(
                proxy_type="graphql_conversion",
                identifier=f"{row['method']} {row['op_name']}",
                sessions_reached=int(row["unique_sessions"]),
                total_sessions=total_sessions,
                conversion_rate=row["unique_sessions"] / total_sessions if total_sessions > 0 else 0,
            ))
        elif is_engagement:
            proxies.append(ConversionProxy(
                proxy_type="graphql_engagement",
                identifier=f"{row['method']} {row['op_name']}",
                sessions_reached=int(row["unique_sessions"]),
                total_sessions=total_sessions,
                conversion_rate=row["unique_sessions"] / total_sessions if total_sessions > 0 else 0,
            ))

    # Strategy 2: URL path patterns (for non-GraphQL endpoints)
    query2 = """
    SELECT
        SpanAttributes['http.target'] as target,
        SpanAttributes['http.method'] as method,
        uniqCombined64(SessionId) as unique_sessions,
        countIf(PulseType LIKE 'network.2%%') as success_count
    FROM otel_traces
    WHERE ProjectId = %(pid)s
      AND PulseType LIKE 'network.%%'
      AND Timestamp >= now() - INTERVAL %(days)s DAY
      AND SpanAttributes['http.target'] != ''
      AND SpanAttributes['http.target'] != '/graphql'
    GROUP BY target, method
    HAVING unique_sessions >= 3
    ORDER BY unique_sessions DESC
    LIMIT 20
    """
    r2 = client.query(query2, parameters={"pid": project_id, "days": lookback_days})
    urls = pd.DataFrame(r2.result_rows, columns=["target", "method", "unique_sessions", "success_count"])

    for _, row in urls.iterrows():
        target_lower = row["target"].lower()
        if any(kw in target_lower for kw in CONVERSION_KEYWORDS):
            proxies.append(ConversionProxy(
                proxy_type="url_conversion",
                identifier=f"{row['method']} {row['target']}",
                sessions_reached=int(row["unique_sessions"]),
                total_sessions=total_sessions,
                conversion_rate=row["unique_sessions"] / total_sessions if total_sessions > 0 else 0,
            ))

    # Sort by proxy_type priority (conversion > engagement) then by sessions
    type_priority = {"graphql_conversion": 0, "url_conversion": 1, "graphql_engagement": 2}
    proxies.sort(key=lambda p: (type_priority.get(p.proxy_type, 99), -p.sessions_reached))

    return proxies


def get_conversion_sessions(client, project_id: str, lookback_days: int, proxy: ConversionProxy) -> set:
    """Get the set of session IDs that reached the conversion proxy."""

    if proxy.proxy_type in ("graphql_conversion", "graphql_engagement"):
        # Extract method and op_name from identifier like "GET validateEntitlement"
        parts = proxy.identifier.split(" ", 1)
        method, op_name = parts[0], parts[1]

        query = """
        SELECT DISTINCT SessionId
        FROM otel_traces
        WHERE ProjectId = %(pid)s
          AND PulseType LIKE 'network.2%%'
          AND Timestamp >= now() - INTERVAL %(days)s DAY
          AND SpanAttributes['http.request.header.operation_name'] = %(op)s
        """
        r = client.query(query, parameters={"pid": project_id, "days": lookback_days, "op": op_name})
    else:
        parts = proxy.identifier.split(" ", 1)
        target = parts[1] if len(parts) > 1 else parts[0]

        query = """
        SELECT DISTINCT SessionId
        FROM otel_traces
        WHERE ProjectId = %(pid)s
          AND PulseType LIKE 'network.2%%'
          AND Timestamp >= now() - INTERVAL %(days)s DAY
          AND SpanAttributes['http.target'] = %(target)s
        """
        r = client.query(query, parameters={"pid": project_id, "days": lookback_days, "target": target})

    return {row[0] for row in r.result_rows}


def session_depth_conversion(sessions_df: pd.DataFrame) -> set:
    """Fallback: top 25% by unique_screens as 'converted'."""
    threshold = sessions_df["unique_screens"].quantile(0.75)
    return set(sessions_df[sessions_df["unique_screens"] >= threshold]["session_id"])


# ═══════════════════════════════════════════════════════════════════
# Section 3: Causal Engine - Propensity Score Matching
# ═══════════════════════════════════════════════════════════════════

MATCHING_FEATURES = [
    "device_model", "os_version", "app_version", "network_provider",
    "session_hour", "unique_screens",
    # otel_logs signals (added when available)
    "jank_slow_count", "jank_frozen_count", "click_count",
    "network_change_count", "lifecycle_event_count",
]


def encode_features(df: pd.DataFrame, features: list[str]) -> np.ndarray:
    """Label-encode categorical features and return numeric matrix."""
    encoded = pd.DataFrame()
    for feat in features:
        if feat not in df.columns:
            continue
        col = df[feat].fillna("unknown").astype(str)
        if col.nunique() <= 1:
            continue  # skip constant columns
        le = LabelEncoder()
        encoded[feat] = le.fit_transform(col)

    if encoded.empty:
        return np.zeros((len(df), 1))
    return encoded.values.astype(float)


def propensity_score_matching(
    sessions_df: pd.DataFrame,
    affected_session_ids: set,
    converted_session_ids: set,
    k: int = 3,
    min_affected: int = 5,
    min_control: int = 10,
) -> Optional[IssueAnalysis]:
    """
    Run propensity score matching.

    Returns IssueAnalysis or None if insufficient data.
    """
    # Tag sessions
    sessions = sessions_df.copy()
    sessions["is_affected"] = sessions["session_id"].isin(affected_session_ids).astype(int)
    sessions["converted"] = sessions["session_id"].isin(converted_session_ids).astype(int)

    affected = sessions[sessions["is_affected"] == 1]
    control = sessions[sessions["is_affected"] == 0]

    if len(affected) < min_affected or len(control) < min_control:
        return None

    # Encode features
    X = encode_features(sessions, MATCHING_FEATURES)

    if X.shape[1] == 0:
        return None

    # Fit propensity model
    y = sessions["is_affected"].values
    try:
        model = LogisticRegression(max_iter=1000, random_state=42)
        model.fit(X, y)
        propensity_scores = model.predict_proba(X)[:, 1]
    except Exception:
        # If logistic regression fails (e.g., perfect separation), use features directly
        propensity_scores = X.mean(axis=1)

    sessions["propensity"] = propensity_scores

    # Match: for each affected session, find k nearest control sessions
    affected_idx = sessions[sessions["is_affected"] == 1].index
    control_idx = sessions[sessions["is_affected"] == 0].index

    if len(control_idx) < k:
        k = max(1, len(control_idx))

    control_propensities = sessions.loc[control_idx, "propensity"].values.reshape(-1, 1)
    nn = NearestNeighbors(n_neighbors=k, metric="euclidean")
    nn.fit(control_propensities)

    affected_propensities = sessions.loc[affected_idx, "propensity"].values.reshape(-1, 1)
    distances, indices = nn.kneighbors(affected_propensities)

    # Get matched control session indices
    matched_control_idx = control_idx[indices.flatten()].unique()

    # Compute conversion rates
    affected_converted = sessions.loc[affected_idx, "converted"]
    matched_control_converted = sessions.loc[matched_control_idx, "converted"]

    affected_rate = affected_converted.mean()
    control_rate = matched_control_converted.mean()
    delta = control_rate - affected_rate

    # Bootstrap confidence interval
    ci_lower, ci_upper, is_sig = bootstrap_ci(
        affected_converted.values,
        matched_control_converted.values,
    )

    priority = abs(delta) * len(affected_idx) * (1.0 if is_sig else 0.5)

    return IssueAnalysis(
        issue_type="",  # filled by caller
        issue_label="",  # filled by caller
        screen_name="",
        affected_count=len(affected_idx),
        control_count=len(matched_control_idx),
        affected_conversion_rate=affected_rate,
        control_conversion_rate=control_rate,
        conversion_delta=delta,
        ci_lower=ci_lower,
        ci_upper=ci_upper,
        is_significant=is_sig,
        priority_score=priority,
    )


def bootstrap_ci(affected: np.ndarray, control: np.ndarray, n_boot: int = 1000, alpha: float = 0.05):
    """Bootstrap 95% confidence interval for the conversion delta."""
    rng = np.random.RandomState(42)
    deltas = []
    for _ in range(n_boot):
        a = rng.choice(affected, size=len(affected), replace=True)
        c = rng.choice(control, size=len(control), replace=True)
        deltas.append(c.mean() - a.mean())

    lower = np.percentile(deltas, 100 * alpha / 2)
    upper = np.percentile(deltas, 100 * (1 - alpha / 2))
    is_significant = (lower > 0 and upper > 0) or (lower < 0 and upper < 0)  # CI excludes zero
    return lower, upper, is_significant


# ═══════════════════════════════════════════════════════════════════
# Section 4: Analyze All Issues
# ═══════════════════════════════════════════════════════════════════

def analyze_issues(
    sessions_df: pd.DataFrame,
    crash_anr_df: pd.DataFrame,
    converted_session_ids: set,
    jank_by_screen_df: pd.DataFrame = None,
    rage_tap_df: pd.DataFrame = None,
) -> list[IssueAnalysis]:
    """Run causal analysis for each distinct issue."""

    results = []

    # Group by (pulse_type, screen_name) to get distinct issues
    if not crash_anr_df.empty:
        issue_groups = crash_anr_df.groupby(["pulse_type", "screen_name"])

        for (pulse_type, screen_name), group in issue_groups:
            affected_ids = set(group["session_id"].unique())
            label = f"{pulse_type} on {screen_name or 'unknown'}"

            if len(affected_ids) < 3:
                print(f"    {label}: only {len(affected_ids)} sessions, skipping (need 3+)")
                continue

            analysis = propensity_score_matching(
                sessions_df,
                affected_ids,
                converted_session_ids,
                k=min(3, max(1, len(sessions_df) // 10)),
                min_affected=3,   # lowered for small dataset
                min_control=5,
            )

            if analysis is None:
                print(f"    {label}: insufficient data for matching, skipping")
                continue

            analysis.issue_type = pulse_type
            analysis.issue_label = label
            analysis.screen_name = screen_name
            results.append(analysis)

    # Also check sessions with high network errors
    if "net_error_count" in sessions_df.columns:
        high_error_sessions = set(
            sessions_df[sessions_df["net_error_count"] >= 3]["session_id"]
        )
        if len(high_error_sessions) >= 3:
            analysis = propensity_score_matching(
                sessions_df,
                high_error_sessions,
                converted_session_ids,
                k=min(3, max(1, len(sessions_df) // 10)),
                min_affected=3,
                min_control=5,
            )
            if analysis:
                analysis.issue_type = "network_errors"
                analysis.issue_label = "Sessions with 3+ network errors (4xx/5xx)"
                analysis.screen_name = "multiple"
                results.append(analysis)

    # ── otel_logs: Jank by screen ──
    if jank_by_screen_df is not None and not jank_by_screen_df.empty:
        jank_groups = jank_by_screen_df.groupby(["pulse_type", "screen_name"])
        for (pulse_type, screen_name), group in jank_groups:
            if not screen_name:
                continue
            affected_ids = set(group["session_id"].unique())
            label = f"{pulse_type} on {screen_name}"

            if len(affected_ids) < 3:
                print(f"    {label}: only {len(affected_ids)} sessions, skipping")
                continue

            analysis = propensity_score_matching(
                sessions_df, affected_ids, converted_session_ids,
                k=min(3, max(1, len(sessions_df) // 10)),
                min_affected=3, min_control=5,
            )
            if analysis:
                analysis.issue_type = pulse_type
                analysis.issue_label = label
                analysis.screen_name = screen_name
                results.append(analysis)

    # ── otel_logs: Sessions with high jank (overall) ──
    if "jank_slow_count" in sessions_df.columns:
        jank_threshold = sessions_df["jank_slow_count"].quantile(0.90)
        if jank_threshold > 0:
            high_jank_sessions = set(
                sessions_df[sessions_df["jank_slow_count"] >= jank_threshold]["session_id"]
            )
            if len(high_jank_sessions) >= 3:
                analysis = propensity_score_matching(
                    sessions_df, high_jank_sessions, converted_session_ids,
                    k=min(3, max(1, len(sessions_df) // 10)),
                    min_affected=3, min_control=5,
                )
                if analysis:
                    analysis.issue_type = "high_jank"
                    analysis.issue_label = f"Sessions with high jank (>={jank_threshold:.0f} slow frames)"
                    analysis.screen_name = "multiple"
                    results.append(analysis)

    # ── otel_logs: Rage taps by screen ──
    if rage_tap_df is not None and not rage_tap_df.empty:
        rage_groups = rage_tap_df.groupby("screen_name")
        for screen_name, group in rage_groups:
            if not screen_name:
                continue
            affected_ids = set(group["session_id"].unique())
            label = f"Rage taps on {screen_name}"

            if len(affected_ids) < 3:
                print(f"    {label}: only {len(affected_ids)} sessions, skipping")
                continue

            analysis = propensity_score_matching(
                sessions_df, affected_ids, converted_session_ids,
                k=min(3, max(1, len(sessions_df) // 10)),
                min_affected=3, min_control=5,
            )
            if analysis:
                analysis.issue_type = "rage_tap"
                analysis.issue_label = label
                analysis.screen_name = screen_name
                results.append(analysis)

    # Sort by priority score descending
    results.sort(key=lambda r: r.priority_score, reverse=True)
    return results


# ═══════════════════════════════════════════════════════════════════
# Section 5: Report
# ═══════════════════════════════════════════════════════════════════

def print_report(results: list[IssueAnalysis], proxy: ConversionProxy):
    """Print the final ranked revenue impact report."""

    if not results:
        print("\n  No issues had enough data for causal analysis.")
        print("  This could mean:")
        print("    - Too few sessions overall (<50)")
        print("    - Issues are too rare (<3 affected sessions)")
        print("    - All sessions are affected (no clean control group)")
        return

    print(f"\n{'='*80}")
    print(f"  REVENUE IMPACT REPORT")
    print(f"  Conversion proxy: {proxy.identifier} ({proxy.proxy_type})")
    print(f"{'='*80}\n")

    table_data = []
    for r in results:
        sig_marker = "YES" if r.is_significant else "no"
        delta_str = f"{r.conversion_delta:+.1%}"
        ci_str = f"[{r.ci_lower:+.1%}, {r.ci_upper:+.1%}]"

        table_data.append([
            r.issue_label,
            r.affected_count,
            r.control_count,
            f"{r.affected_conversion_rate:.1%}",
            f"{r.control_conversion_rate:.1%}",
            delta_str,
            ci_str,
            sig_marker,
            f"{r.priority_score:.2f}",
        ])

    headers = ["Issue", "Affected", "Control", "Aff. Conv%", "Ctrl Conv%",
               "Delta", "95% CI", "Significant?", "Priority"]
    print(tabulate(table_data, headers=headers, tablefmt="grid"))

    # Detailed breakdown for each significant result
    significant = [r for r in results if r.is_significant]
    if significant:
        print(f"\n{'─'*80}")
        print("  SIGNIFICANT FINDINGS (causal impact detected)")
        print(f"{'─'*80}")
        for r in significant:
            direction = "REDUCES" if r.conversion_delta > 0 else "INCREASES"
            print(f"""
  {r.issue_label}
  {'─' * len(r.issue_label)}
  This issue {direction} the likelihood of reaching '{proxy.identifier}' by
  approximately {abs(r.conversion_delta):.1%} (95% CI: [{r.ci_lower:+.1%}, {r.ci_upper:+.1%}])

  Affected sessions:  {r.affected_count}
  Matched controls:   {r.control_count}
  Affected conv rate: {r.affected_conversion_rate:.1%}
  Control conv rate:  {r.control_conversion_rate:.1%}

  Interpretation: If this issue were fixed, we would expect ~{abs(r.conversion_delta) * r.affected_count:.0f}
  additional conversions per {r.affected_count} affected sessions.
""")
    else:
        print("\n  No statistically significant results found.")
        print("  This likely means sample sizes are too small for conclusive causal claims.")
        print("  The directional estimates above are still useful for prioritization.")


# ═══════════════════════════════════════════════════════════════════
# Main
# ═══════════════════════════════════════════════════════════════════

def main():
    parser = argparse.ArgumentParser(description="Pulse Causal Revenue Impact Prototype")
    parser.add_argument("--project-id", required=True, help="ClickHouse ProjectId to analyze")
    parser.add_argument("--lookback-days", type=int, default=30, help="Days of data to analyze (default: 30)")
    args = parser.parse_args()

    load_dotenv()

    print("=" * 70)
    print("  PULSE - Causal Revenue Impact Prototype")
    print("  + Composite Frustration Score (Huang et al. CHI 2011)")
    print("  + Process Mining / Funnel Discovery (Leemans et al. BPM 2013)")
    print("=" * 70)

    # ── Step 1: Connect ──
    print(f"\n[1/7] Connecting to ClickHouse...")
    try:
        client = get_ch_client()
        r = client.query("SELECT 1")
        print(f"  Connected successfully")
    except Exception as e:
        print(f"  FAILED to connect: {e}")
        sys.exit(1)

    project_id = args.project_id
    lookback = args.lookback_days

    # ── Step 2: Build session profiles ──
    print(f"\n[2/7] Building session profiles (last {lookback} days, project='{project_id}')...")
    sessions_df = build_session_profiles(client, project_id, lookback)
    print(f"  Found {len(sessions_df)} sessions with screen events")

    if sessions_df.empty:
        print("  ERROR: No sessions found. Check project ID and date range.")
        sys.exit(1)

    # Stats
    print(f"  Unique devices:    {sessions_df['device_model'].nunique()}")
    print(f"  App versions:      {sessions_df['app_version'].nunique()}")
    print(f"  Avg screens/sess:  {sessions_df['unique_screens'].mean():.1f}")
    print(f"  Avg duration:      {sessions_df['session_duration_sec'].mean():.0f}s")

    # Get crash/ANR data from stack_trace_events
    print(f"\n  Loading crash/ANR data from stack_trace_events...")
    crash_anr_df = get_crash_anr_sessions(client, project_id, lookback)

    if not crash_anr_df.empty:
        for pt in crash_anr_df["pulse_type"].unique():
            subset = crash_anr_df[crash_anr_df["pulse_type"] == pt]
            n_sessions = subset["session_id"].nunique()
            n_events = subset["event_count"].sum()
            print(f"    {pt}: {n_events} events across {n_sessions} sessions")
    else:
        print("    No crash/ANR data found in stack_trace_events")

    # Get otel_logs signals (jank, clicks, lifecycle, network changes)
    print(f"\n  Loading otel_logs signals (jank, clicks, lifecycle)...")
    logs_signals_df = get_logs_session_signals(client, project_id, lookback)
    jank_by_screen_df = get_jank_by_screen(client, project_id, lookback)
    rage_tap_df = get_rage_tap_sessions(client, project_id, lookback)

    if not logs_signals_df.empty:
        # Merge log signals into session profiles
        sessions_df = sessions_df.merge(logs_signals_df, on="session_id", how="left")
        # Fill NaN with 0 for sessions that had no log entries
        log_cols = ["jank_slow_count", "jank_frozen_count", "click_count",
                    "network_change_count", "lifecycle_event_count",
                    "session_start_count", "session_end_count"]
        for col in log_cols:
            if col in sessions_df.columns:
                sessions_df[col] = sessions_df[col].fillna(0).astype(int)

        jank_sessions = (sessions_df["jank_slow_count"] + sessions_df["jank_frozen_count"] > 0).sum()
        click_sessions = (sessions_df["click_count"] > 0).sum()
        net_change_sessions = (sessions_df["network_change_count"] > 0).sum()
        print(f"    Jank (slow+frozen): {sessions_df['jank_slow_count'].sum() + sessions_df['jank_frozen_count'].sum()} events across {jank_sessions} sessions")
        print(f"    Clicks:             {sessions_df['click_count'].sum()} events across {click_sessions} sessions")
        print(f"    Network changes:    {sessions_df['network_change_count'].sum()} events across {net_change_sessions} sessions")
    else:
        print("    No otel_logs data found")

    if not jank_by_screen_df.empty:
        jank_screens = jank_by_screen_df["screen_name"].nunique()
        print(f"    Jank by screen:     {len(jank_by_screen_df)} entries across {jank_screens} screens")
    else:
        print("    No per-screen jank data found")

    if not rage_tap_df.empty:
        rage_sessions = rage_tap_df["session_id"].nunique()
        rage_screens = rage_tap_df["screen_name"].nunique()
        print(f"    Rage taps:          {len(rage_tap_df)} bursts across {rage_sessions} sessions, {rage_screens} screens")
    else:
        print("    No rage taps detected")

    # ── Step 3: Composite Frustration Score ──
    print(f"\n[3/7] Computing composite frustration scores...")
    frustration_df = compute_frustration_scores(sessions_df, crash_anr_df, rage_tap_df)
    sessions_df = sessions_df.merge(frustration_df[["session_id", "frustration_score"]], on="session_id", how="left")
    print_frustration_summary(sessions_df)

    # Add high-frustration as a matching feature
    MATCHING_FEATURES.append("frustration_score")

    # ── Step 4: Discover conversion proxies ──
    print(f"\n[4/7] Discovering conversion proxies...")
    proxies = discover_conversion_proxies(client, project_id, lookback, len(sessions_df))

    if proxies:
        print(f"  Found {len(proxies)} conversion-related signals:")
        for p in proxies:
            print(f"    [{p.proxy_type}] {p.identifier:50s} {p.sessions_reached:>4} sessions ({p.conversion_rate:.1%})")

        # Use the best conversion proxy
        primary_proxy = proxies[0]
        print(f"\n  Using primary proxy: {primary_proxy.identifier}")
        converted_sessions = get_conversion_sessions(client, project_id, lookback, primary_proxy)
        print(f"  {len(converted_sessions)}/{len(sessions_df)} sessions ({len(converted_sessions)/len(sessions_df):.1%}) reached conversion")
    else:
        print("  No conversion-related endpoints found.")
        print("  Falling back to session depth proxy (top 25% by screens visited = 'converted')")
        converted_sessions = session_depth_conversion(sessions_df)
        primary_proxy = ConversionProxy(
            proxy_type="session_depth",
            identifier=f"Top 25% session depth (>={sessions_df['unique_screens'].quantile(0.75):.0f} screens)",
            sessions_reached=len(converted_sessions),
            total_sessions=len(sessions_df),
            conversion_rate=len(converted_sessions) / len(sessions_df),
        )
        print(f"  {len(converted_sessions)}/{len(sessions_df)} sessions ({primary_proxy.conversion_rate:.1%}) classified as 'converted'")

    # ── Step 5: Process Mining — Funnel Discovery ──
    print(f"\n[5/7] Process mining — discovering user journey funnels...")
    screen_events_df = get_screen_sequences(client, project_id, lookback)
    traces = {}
    variants_analysis = []
    funnel_steps = []
    dropoff_results = []

    if not screen_events_df.empty:
        traces = build_session_traces(screen_events_df)
        print(f"  Built screen sequences for {len(traces)} sessions")
        print(f"  Avg journey length: {np.mean([len(t) for t in traces.values()]):.1f} screens")
        print(f"  Max journey length: {max(len(t) for t in traces.values())} screens")

        variants_analysis, funnel_steps = discover_funnels(
            traces, converted_sessions,
            min_variant_sessions=3,
            top_n_variants=15,
        )
        print(f"  Discovered {len(variants_analysis)} significant journey variants")

        bottlenecks = [s for s in funnel_steps if s.is_bottleneck]
        if bottlenecks:
            print(f"  Found {len(bottlenecks)} funnel bottleneck(s):")
            for b in bottlenecks:
                print(f"    ⚠ {b.screen_name} — {b.dropoff_rate:.0%} drop-off")

        # Run causal analysis on drop-off points
        dropoff_results = analyze_dropoff_issues(
            traces, sessions_df, converted_sessions, funnel_steps,
        )
    else:
        print("  No screen events found for process mining")

    # ── Step 6: Run causal analysis ──
    print(f"\n[6/7] Running causal analysis...")
    results = analyze_issues(
        sessions_df, crash_anr_df, converted_sessions,
        jank_by_screen_df=jank_by_screen_df,
        rage_tap_df=rage_tap_df,
    )

    # Add dropoff issues from process mining
    if dropoff_results:
        results.extend(dropoff_results)
        results.sort(key=lambda r: r.priority_score, reverse=True)

    # Add high-frustration sessions as an issue
    if "frustration_score" in sessions_df.columns:
        high_frust_threshold = sessions_df["frustration_score"].quantile(0.85)
        if high_frust_threshold > 0:
            high_frust_sessions = set(
                sessions_df[sessions_df["frustration_score"] >= high_frust_threshold]["session_id"]
            )
            if len(high_frust_sessions) >= 3:
                analysis = propensity_score_matching(
                    sessions_df, high_frust_sessions, converted_sessions,
                    k=min(3, max(1, len(sessions_df) // 10)),
                    min_affected=3, min_control=5,
                )
                if analysis:
                    analysis.issue_type = "high_frustration"
                    analysis.issue_label = f"High-frustration sessions (score >={high_frust_threshold:.0f})"
                    analysis.screen_name = "multiple"
                    results.append(analysis)
                    results.sort(key=lambda r: r.priority_score, reverse=True)

    # ── Step 7: Report ──
    print(f"\n[7/7] Generating report...")
    print_report(results, primary_proxy)

    # Print process mining report
    if traces:
        print_process_mining_report(
            variants_analysis, funnel_steps, converted_sessions, len(sessions_df),
        )

    # Frustration vs conversion correlation
    if "frustration_score" in sessions_df.columns:
        sessions_df["converted"] = sessions_df["session_id"].isin(converted_sessions).astype(int)
        conv_frust = sessions_df[sessions_df["converted"] == 1]["frustration_score"].mean()
        nonconv_frust = sessions_df[sessions_df["converted"] == 0]["frustration_score"].mean()
        print(f"\n  {'─'*70}")
        print(f"  FRUSTRATION vs CONVERSION")
        print(f"  {'─'*70}")
        print(f"    Avg frustration score (converting):     {conv_frust:.1f}")
        print(f"    Avg frustration score (non-converting): {nonconv_frust:.1f}")
        if nonconv_frust > conv_frust:
            print(f"    → Non-converting sessions are {nonconv_frust - conv_frust:.1f} points more frustrated")
        else:
            print(f"    → Converting sessions are slightly more frustrated (selection bias — engaged users hit more issues)")

    # Also run with engagement proxy if we have one
    engagement_proxies = [p for p in proxies if p.proxy_type == "graphql_engagement"]
    if engagement_proxies and proxies[0].proxy_type != "graphql_engagement":
        eng_proxy = engagement_proxies[0]
        print(f"\n{'='*80}")
        print(f"  BONUS: Analysis with engagement proxy: {eng_proxy.identifier}")
        print(f"{'='*80}")
        eng_converted = get_conversion_sessions(client, project_id, lookback, eng_proxy)
        print(f"  {len(eng_converted)}/{len(sessions_df)} sessions reached this endpoint")
        eng_results = analyze_issues(
            sessions_df, crash_anr_df, eng_converted,
            jank_by_screen_df=jank_by_screen_df,
            rage_tap_df=rage_tap_df,
        )
        print_report(eng_results, eng_proxy)

    print(f"\n{'='*70}")
    print("  Analysis complete!")
    print(f"{'='*70}\n")


if __name__ == "__main__":
    main()

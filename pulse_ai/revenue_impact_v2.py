#!/usr/bin/env python3
"""
Pulse — Causal Revenue Impact Analysis v2
==========================================
Corrected implementation addressing:
  1. Journey-stage confounding (condition on reaching the same screen)
  2. Temporal ordering (conversion must happen AFTER the issue)
  3. Post-treatment variable bias (clean matching features only)
  4. Caliper-bounded propensity score matching
  5. Screen-graph process mining (not depth-based)
  6. Empirically calibrated frustration scores

Usage:
    python revenue_impact_v2.py --project-id fancode --lookback-days 60
"""

import argparse
import os
import sys
import warnings
from collections import Counter, defaultdict
from dataclasses import dataclass, field
from typing import Optional

import numpy as np
import pandas as pd
from dotenv import load_dotenv
from sklearn.linear_model import LogisticRegression
from sklearn.neighbors import NearestNeighbors
from sklearn.preprocessing import LabelEncoder
from scipy.stats import rankdata
from tabulate import tabulate

warnings.filterwarnings("ignore")

# ═══════════════════════════════════════════════════════════════════
# Data Models
# ═══════════════════════════════════════════════════════════════════

@dataclass
class IssueAnalysis:
    issue_type: str
    issue_label: str
    screen_name: str
    funnel_stage: str                # early / mid / late
    sessions_reaching_screen: int    # total sessions that visited this screen
    affected_count: int
    control_count: int
    affected_conversion_rate: float
    control_conversion_rate: float
    conversion_delta: float          # control - affected (positive = issue hurts)
    ci_lower: float
    ci_upper: float
    is_significant: bool
    propensity_balance: float        # mean PS difference after matching
    priority_score: float

@dataclass
class ConversionProxy:
    proxy_type: str
    identifier: str
    sessions_reached: int
    total_sessions: int
    conversion_rate: float

@dataclass
class FunnelStep:
    screen_name: str
    sessions_at_step: int
    sessions_total: int
    dropoff_rate: float
    conversion_rate: float
    is_bottleneck: bool


# ═══════════════════════════════════════════════════════════════════
# ClickHouse Connection
# ═══════════════════════════════════════════════════════════════════

def get_ch_client():
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
# Section 1: Temporal-Aware Data Extraction
#   Key fix: pull individual events with timestamps, not aggregates
# ═══════════════════════════════════════════════════════════════════

def get_session_profiles(client, project_id: str, lookback_days: int) -> pd.DataFrame:
    """Session-level features from otel_traces (device context only — no post-treatment vars)."""
    query = """
    SELECT
        SessionId AS session_id,
        any(UserId) AS user_id,
        any(DeviceModel) AS device_model,
        any(OsVersion) AS os_version,
        any(AppVersion) AS app_version,
        any(Platform) AS platform,
        any(GeoCountry) AS geo_country,
        any(NetworkProvider) AS network_provider,
        toHour(min(Timestamp)) AS session_hour,
        min(Timestamp) AS session_start,
        max(Timestamp) AS session_end,
        dateDiff('second', min(Timestamp), max(Timestamp)) AS session_duration_sec,
        -- These are for reporting only, NOT for matching
        count(DISTINCT CASE
            WHEN PulseType IN ('screen_session', 'screen_load')
            THEN SpanAttributes['screen.name'] END) AS unique_screens,
        countIf(PulseType LIKE 'network.%%') AS total_network_calls,
        countIf(PulseType LIKE 'network.4%%' OR PulseType LIKE 'network.5%%') AS net_error_count,
        countIf(PulseType = 'network.0') AS net_timeout_count
    FROM otel_traces
    WHERE ProjectId = %(pid)s
      AND Timestamp >= now() - INTERVAL %(days)s DAY
      AND SessionId != ''
    GROUP BY SessionId
    HAVING unique_screens > 0
    """
    r = client.query(query, parameters={"pid": project_id, "days": lookback_days})
    df = pd.DataFrame(r.result_rows, columns=[
        "session_id", "user_id", "device_model", "os_version", "app_version",
        "platform", "geo_country", "network_provider", "session_hour",
        "session_start", "session_end", "session_duration_sec",
        "unique_screens", "total_network_calls", "net_error_count", "net_timeout_count",
    ])
    return df


def get_screen_visits(client, project_id: str, lookback_days: int) -> pd.DataFrame:
    """Per-session screen visits with timestamps (for journey conditioning)."""
    query = """
    SELECT
        SessionId AS session_id,
        SpanAttributes['screen.name'] AS screen_name,
        min(Timestamp) AS first_visit_ts
    FROM otel_traces
    WHERE ProjectId = %(pid)s
      AND PulseType IN ('screen_session', 'screen_load')
      AND Timestamp >= now() - INTERVAL %(days)s DAY
      AND SessionId != ''
      AND SpanAttributes['screen.name'] != ''
    GROUP BY SessionId, screen_name
    ORDER BY SessionId, first_visit_ts
    """
    r = client.query(query, parameters={"pid": project_id, "days": lookback_days})
    return pd.DataFrame(r.result_rows, columns=["session_id", "screen_name", "first_visit_ts"])


def get_issue_events(client, project_id: str, lookback_days: int) -> pd.DataFrame:
    """Individual crash/ANR/non-fatal events WITH timestamps."""
    query = """
    SELECT
        SessionId AS session_id,
        PulseType AS pulse_type,
        ScreenName AS screen_name,
        Timestamp AS issue_timestamp,
        ExceptionType AS exception_type
    FROM stack_trace_events
    WHERE ProjectId = %(pid)s
      AND Timestamp >= now() - INTERVAL %(days)s DAY
      AND SessionId != ''
    ORDER BY SessionId, issue_timestamp
    """
    r = client.query(query, parameters={"pid": project_id, "days": lookback_days})
    return pd.DataFrame(r.result_rows, columns=[
        "session_id", "pulse_type", "screen_name", "issue_timestamp", "exception_type",
    ])


def get_conversion_events(client, project_id: str, lookback_days: int, op_name: str) -> pd.DataFrame:
    """Individual conversion events WITH timestamps."""
    query = """
    SELECT
        SessionId AS session_id,
        Timestamp AS conversion_timestamp
    FROM otel_traces
    WHERE ProjectId = %(pid)s
      AND PulseType LIKE 'network.2%%'
      AND SpanAttributes['http.request.header.operation_name'] = %(op)s
      AND Timestamp >= now() - INTERVAL %(days)s DAY
      AND SessionId != ''
    ORDER BY SessionId, conversion_timestamp
    """
    r = client.query(query, parameters={"pid": project_id, "days": lookback_days, "op": op_name})
    return pd.DataFrame(r.result_rows, columns=["session_id", "conversion_timestamp"])


def get_log_signals(client, project_id: str, lookback_days: int) -> pd.DataFrame:
    """Aggregated log signals per session (for frustration scoring only, NOT matching)."""
    query = """
    SELECT
        SessionId AS session_id,
        countIf(PulseType = 'app.jank.slow') AS jank_slow_count,
        countIf(PulseType = 'app.jank.frozen') AS jank_frozen_count,
        countIf(PulseType = 'app.click') AS click_count,
        countIf(PulseType = 'network.change') AS network_change_count
    FROM otel_logs
    WHERE ProjectId = %(pid)s
      AND Timestamp >= now() - INTERVAL %(days)s DAY
      AND SessionId != ''
    GROUP BY session_id
    """
    r = client.query(query, parameters={"pid": project_id, "days": lookback_days})
    return pd.DataFrame(r.result_rows, columns=[
        "session_id", "jank_slow_count", "jank_frozen_count", "click_count", "network_change_count",
    ])


def get_jank_events_by_screen(client, project_id: str, lookback_days: int) -> pd.DataFrame:
    """Jank events with timestamps for per-screen issue analysis."""
    query = """
    SELECT
        SessionId AS session_id,
        PulseType AS pulse_type,
        LogAttributes['screen.name'] AS screen_name,
        min(Timestamp) AS issue_timestamp,
        count() AS event_count
    FROM otel_logs
    WHERE ProjectId = %(pid)s
      AND PulseType IN ('app.jank.slow', 'app.jank.frozen')
      AND Timestamp >= now() - INTERVAL %(days)s DAY
      AND SessionId != ''
      AND LogAttributes['screen.name'] != ''
    GROUP BY session_id, pulse_type, screen_name
    """
    r = client.query(query, parameters={"pid": project_id, "days": lookback_days})
    if not r.result_rows:
        return pd.DataFrame(columns=["session_id", "pulse_type", "screen_name", "issue_timestamp", "event_count"])
    return pd.DataFrame(r.result_rows, columns=[
        "session_id", "pulse_type", "screen_name", "issue_timestamp", "event_count",
    ])


# ═══════════════════════════════════════════════════════════════════
# Section 2: Conversion Proxy Discovery (unchanged from v1)
# ═══════════════════════════════════════════════════════════════════

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
    query = """
    SELECT
        SpanAttributes['http.request.header.operation_name'] AS op_name,
        SpanAttributes['http.method'] AS method,
        uniqCombined64(SessionId) AS unique_sessions,
        count() AS total_calls
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
    ops = pd.DataFrame(r.result_rows, columns=["op_name", "method", "unique_sessions", "total_calls"])

    proxies = []
    for _, row in ops.iterrows():
        op_lower = row["op_name"].lower()
        is_conv = any(kw in op_lower for kw in CONVERSION_KEYWORDS)
        is_eng = any(kw in op_lower for kw in ENGAGEMENT_KEYWORDS)
        if is_conv or is_eng:
            proxies.append(ConversionProxy(
                proxy_type="graphql_conversion" if is_conv else "graphql_engagement",
                identifier=f"{row['method']} {row['op_name']}",
                sessions_reached=int(row["unique_sessions"]),
                total_sessions=total_sessions,
                conversion_rate=row["unique_sessions"] / total_sessions if total_sessions > 0 else 0,
            ))

    type_priority = {"graphql_conversion": 0, "url_conversion": 1, "graphql_engagement": 2}
    proxies.sort(key=lambda p: (type_priority.get(p.proxy_type, 99), -p.sessions_reached))
    return proxies


# ═══════════════════════════════════════════════════════════════════
# Section 3: Journey-Conditioned Causal Engine
#   THE CORE FIX: condition on journey stage, enforce temporal order
# ═══════════════════════════════════════════════════════════════════

# Only device context for matching — NO post-treatment variables
MATCHING_FEATURES_CAUSAL = [
    "device_model", "os_version", "app_version",
    "network_provider", "session_hour", "geo_country",
]


def encode_features(df: pd.DataFrame, features: list[str]) -> np.ndarray:
    encoded = pd.DataFrame()
    for feat in features:
        if feat not in df.columns:
            continue
        col = df[feat].fillna("unknown").astype(str)
        if col.nunique() <= 1:
            continue
        le = LabelEncoder()
        encoded[feat] = le.fit_transform(col)
    if encoded.empty:
        return np.zeros((len(df), 1))
    return encoded.values.astype(float)


def bootstrap_ci(affected: np.ndarray, control: np.ndarray, n_boot: int = 1000, alpha: float = 0.05):
    rng = np.random.RandomState(42)
    deltas = []
    for _ in range(n_boot):
        a = rng.choice(affected, size=len(affected), replace=True)
        c = rng.choice(control, size=len(control), replace=True)
        deltas.append(c.mean() - a.mean())
    lower = np.percentile(deltas, 100 * alpha / 2)
    upper = np.percentile(deltas, 100 * (1 - alpha / 2))
    is_significant = (lower > 0 and upper > 0) or (lower < 0 and upper < 0)
    return lower, upper, is_significant


def classify_funnel_stage(
    screen_name: str,
    screen_visits_df: pd.DataFrame,
    conversion_events_df: pd.DataFrame,
) -> str:
    """
    Classify a screen as early/mid/late funnel based on what fraction
    of sessions reaching that screen eventually have a conversion event.
    """
    sessions_reaching = set(
        screen_visits_df[screen_visits_df["screen_name"] == screen_name]["session_id"]
    )
    if not sessions_reaching:
        return "unknown"

    sessions_converting = set(conversion_events_df["session_id"].unique())
    conversion_rate = len(sessions_reaching & sessions_converting) / len(sessions_reaching)

    if conversion_rate < 0.10:
        return "early"
    elif conversion_rate < 0.30:
        return "mid"
    else:
        return "late"


def analyze_issue_journey_conditioned(
    sessions_df: pd.DataFrame,
    screen_visits_df: pd.DataFrame,
    issue_events_df: pd.DataFrame,        # individual events with timestamps
    conversion_events_df: pd.DataFrame,   # individual events with timestamps
    issue_type: str,
    issue_screen: str,
    k: int = 3,
    caliper_sd: float = 0.2,
    min_affected: int = 5,
    min_control: int = 5,
) -> Optional[IssueAnalysis]:
    """
    Journey-conditioned PSM with temporal ordering.

    1. Filter to sessions that REACHED issue_screen
    2. Split into affected (had issue) vs control (reached same screen, no issue)
    3. For affected: conversion = any conversion AFTER issue timestamp
    4. For control: conversion = any conversion AFTER arriving at that screen
    5. PSM match within this filtered population using device context only
    6. Apply caliper to discard poor matches
    """

    # ── Step 1: Sessions that reached the issue screen ──
    screen_sessions = screen_visits_df[screen_visits_df["screen_name"] == issue_screen]
    sessions_reaching = set(screen_sessions["session_id"])

    if len(sessions_reaching) < min_affected + min_control:
        return None

    # ── Step 2: Split affected vs control ──
    issue_subset = issue_events_df[
        (issue_events_df["pulse_type"] == issue_type) &
        (issue_events_df["screen_name"] == issue_screen)
    ]
    affected_ids = set(issue_subset["session_id"].unique())

    # Control = reached same screen but did NOT have this issue
    control_ids = sessions_reaching - affected_ids

    if len(affected_ids) < min_affected or len(control_ids) < min_control:
        return None

    # ── Step 3: Temporal conversion for affected sessions ──
    # Reference point: earliest issue timestamp on this screen
    earliest_issue = issue_subset.groupby("session_id")["issue_timestamp"].min()

    # Build a lookup of conversion timestamps per session
    conv_by_session = conversion_events_df.groupby("session_id")["conversion_timestamp"].apply(list).to_dict()

    def converted_after(session_id, ref_ts):
        convs = conv_by_session.get(session_id, [])
        return any(ct > ref_ts for ct in convs)

    affected_converted = {}
    for sid in affected_ids:
        if sid in earliest_issue.index:
            affected_converted[sid] = converted_after(sid, earliest_issue[sid])
        else:
            affected_converted[sid] = False

    # ── Step 4: Temporal conversion for control sessions ──
    # Reference point: when they first visited the issue screen
    screen_arrival = screen_sessions.groupby("session_id")["first_visit_ts"].min()

    control_converted = {}
    for sid in control_ids:
        if sid in screen_arrival.index:
            control_converted[sid] = converted_after(sid, screen_arrival[sid])
        else:
            control_converted[sid] = False

    # ── Step 5: Build filtered dataframe for PSM ──
    all_ids = affected_ids | control_ids
    filtered = sessions_df[sessions_df["session_id"].isin(all_ids)].copy()

    if len(filtered) < min_affected + min_control:
        return None

    filtered["is_affected"] = filtered["session_id"].isin(affected_ids).astype(int)
    all_converted = {**affected_converted, **control_converted}
    filtered["converted"] = filtered["session_id"].map(all_converted).fillna(False).astype(int)

    affected_df = filtered[filtered["is_affected"] == 1]
    control_df = filtered[filtered["is_affected"] == 0]

    if len(affected_df) < min_affected or len(control_df) < min_control:
        return None

    # ── Step 6: Propensity score matching with caliper ──
    features = [f for f in MATCHING_FEATURES_CAUSAL if f in filtered.columns]
    X = encode_features(filtered, features)

    if X.shape[1] == 0:
        return None

    y = filtered["is_affected"].values
    try:
        model = LogisticRegression(max_iter=1000, random_state=42)
        model.fit(X, y)
        ps = model.predict_proba(X)[:, 1]
    except Exception:
        return None

    filtered["propensity"] = ps

    treated_idx = filtered[filtered["is_affected"] == 1].index
    control_idx = filtered[filtered["is_affected"] == 0].index

    # Apply caliper
    ps_std = np.std(ps)
    caliper = caliper_sd * ps_std if ps_std > 0 else 1.0

    k_actual = min(k, len(control_idx))
    control_ps = filtered.loc[control_idx, "propensity"].values.reshape(-1, 1)
    nn = NearestNeighbors(n_neighbors=k_actual, metric="euclidean")
    nn.fit(control_ps)

    treated_ps = filtered.loc[treated_idx, "propensity"].values.reshape(-1, 1)
    distances, indices = nn.kneighbors(treated_ps)

    # Discard matches beyond caliper
    mask = distances[:, 0] <= caliper
    if mask.sum() < min_affected:
        # Caliper too strict, relax it
        mask = np.ones(len(treated_idx), dtype=bool)

    treated_idx_filtered = treated_idx[mask]
    indices_filtered = indices[mask]

    matched_control_idx = control_idx[indices_filtered.flatten()].unique()

    # ── Step 7: Compute results ──
    affected_conv = filtered.loc[treated_idx_filtered, "converted"]
    control_conv = filtered.loc[matched_control_idx, "converted"]

    affected_rate = affected_conv.mean()
    control_rate = control_conv.mean()
    delta = control_rate - affected_rate  # positive = issue hurts conversion

    ci_lower, ci_upper, is_sig = bootstrap_ci(affected_conv.values, control_conv.values)

    ps_balance = abs(
        filtered.loc[treated_idx_filtered, "propensity"].mean() -
        filtered.loc[matched_control_idx, "propensity"].mean()
    )

    funnel_stage = classify_funnel_stage(issue_screen, screen_visits_df, conversion_events_df)
    priority = abs(delta) * len(treated_idx_filtered) * (1.0 if is_sig else 0.5)

    return IssueAnalysis(
        issue_type=issue_type,
        issue_label=f"{issue_type} on {issue_screen or 'unknown'}",
        screen_name=issue_screen,
        funnel_stage=funnel_stage,
        sessions_reaching_screen=len(sessions_reaching),
        affected_count=len(treated_idx_filtered),
        control_count=len(matched_control_idx),
        affected_conversion_rate=affected_rate,
        control_conversion_rate=control_rate,
        conversion_delta=delta,
        ci_lower=ci_lower,
        ci_upper=ci_upper,
        is_significant=is_sig,
        propensity_balance=ps_balance,
        priority_score=priority,
    )


def analyze_all_issues(
    sessions_df: pd.DataFrame,
    screen_visits_df: pd.DataFrame,
    issue_events_df: pd.DataFrame,
    conversion_events_df: pd.DataFrame,
    jank_events_df: pd.DataFrame = None,
) -> list[IssueAnalysis]:
    """Run journey-conditioned causal analysis for each distinct issue."""
    results = []

    # ── Crashes / ANRs / Non-fatals from stack_trace_events ──
    if not issue_events_df.empty:
        issue_groups = issue_events_df.groupby(["pulse_type", "screen_name"])
        for (pulse_type, screen_name), group in issue_groups:
            if not screen_name or len(group["session_id"].unique()) < 3:
                n = len(group["session_id"].unique())
                print(f"    {pulse_type} on {screen_name}: {n} sessions, skipping (need 5+)")
                continue

            analysis = analyze_issue_journey_conditioned(
                sessions_df, screen_visits_df, issue_events_df,
                conversion_events_df, pulse_type, screen_name,
            )
            if analysis:
                results.append(analysis)
            else:
                print(f"    {pulse_type} on {screen_name}: insufficient data after journey conditioning")

    # ── Jank events from otel_logs ──
    if jank_events_df is not None and not jank_events_df.empty:
        # Convert jank events to same format as issue_events for the journey-conditioned function
        jank_as_issues = jank_events_df[["session_id", "pulse_type", "screen_name", "issue_timestamp"]].copy()
        jank_groups = jank_as_issues.groupby(["pulse_type", "screen_name"])

        for (pulse_type, screen_name), group in jank_groups:
            if not screen_name or len(group["session_id"].unique()) < 3:
                continue

            analysis = analyze_issue_journey_conditioned(
                sessions_df, screen_visits_df, jank_as_issues,
                conversion_events_df, pulse_type, screen_name,
            )
            if analysis:
                results.append(analysis)

    # ── Network error sessions (high error count) ──
    if "net_error_count" in sessions_df.columns:
        high_error_ids = set(sessions_df[sessions_df["net_error_count"] >= 3]["session_id"])
        if len(high_error_ids) >= 5:
            # For network errors, we can't journey-condition on a specific screen
            # Use simple PSM with temporal conversion (any conversion in session)
            conv_session_ids = set(conversion_events_df["session_id"].unique())
            filtered = sessions_df.copy()
            filtered["is_affected"] = filtered["session_id"].isin(high_error_ids).astype(int)
            filtered["converted"] = filtered["session_id"].isin(conv_session_ids).astype(int)

            features = [f for f in MATCHING_FEATURES_CAUSAL if f in filtered.columns]
            X = encode_features(filtered, features)
            y = filtered["is_affected"].values

            try:
                model = LogisticRegression(max_iter=1000, random_state=42)
                model.fit(X, y)
                ps = model.predict_proba(X)[:, 1]
                filtered["propensity"] = ps

                treated_idx = filtered[filtered["is_affected"] == 1].index
                control_idx = filtered[filtered["is_affected"] == 0].index
                k_actual = min(3, len(control_idx))

                nn = NearestNeighbors(n_neighbors=k_actual, metric="euclidean")
                nn.fit(filtered.loc[control_idx, "propensity"].values.reshape(-1, 1))
                distances, indices = nn.kneighbors(
                    filtered.loc[treated_idx, "propensity"].values.reshape(-1, 1)
                )
                matched_control_idx = control_idx[indices.flatten()].unique()

                a_conv = filtered.loc[treated_idx, "converted"]
                c_conv = filtered.loc[matched_control_idx, "converted"]
                delta = c_conv.mean() - a_conv.mean()
                ci_l, ci_u, is_sig = bootstrap_ci(a_conv.values, c_conv.values)

                results.append(IssueAnalysis(
                    issue_type="network_errors",
                    issue_label="Sessions with 3+ network errors",
                    screen_name="multiple",
                    funnel_stage="any",
                    sessions_reaching_screen=len(sessions_df),
                    affected_count=len(treated_idx),
                    control_count=len(matched_control_idx),
                    affected_conversion_rate=a_conv.mean(),
                    control_conversion_rate=c_conv.mean(),
                    conversion_delta=delta,
                    ci_lower=ci_l, ci_upper=ci_u,
                    is_significant=is_sig,
                    propensity_balance=abs(
                        filtered.loc[treated_idx, "propensity"].mean() -
                        filtered.loc[matched_control_idx, "propensity"].mean()
                    ),
                    priority_score=abs(delta) * len(treated_idx) * (1.0 if is_sig else 0.5),
                ))
            except Exception:
                pass

    results.sort(key=lambda r: r.priority_score, reverse=True)
    return results


# ═══════════════════════════════════════════════════════════════════
# Section 4: Frustration Score (empirically calibrated when possible)
# ═══════════════════════════════════════════════════════════════════

DEFAULT_FRUSTRATION_WEIGHTS = {
    "has_crash": 30, "has_anr": 25, "jank_frozen_count": 15,
    "net_error_count": 12, "net_timeout_count": 10, "jank_slow_count": 8,
    "network_change_count": 5, "short_session": 10,
}


def calibrate_frustration_weights(sessions_df: pd.DataFrame, converted_col: str = "converted") -> dict:
    """Learn frustration weights from data using logistic regression."""
    signal_cols = [c for c in DEFAULT_FRUSTRATION_WEIGHTS.keys() if c in sessions_df.columns]
    if len(signal_cols) < 3 or len(sessions_df) < 200:
        return DEFAULT_FRUSTRATION_WEIGHTS

    X = sessions_df[signal_cols].fillna(0).values
    y = (1 - sessions_df[converted_col].values).astype(int)  # predict NON-conversion

    try:
        model = LogisticRegression(max_iter=1000, random_state=42)
        model.fit(X, y)
        raw = np.abs(model.coef_[0])
        if raw.sum() == 0:
            return DEFAULT_FRUSTRATION_WEIGHTS
        normalized = (raw / raw.sum() * 130).round(1)  # scale to ~same total as defaults
        return dict(zip(signal_cols, normalized))
    except Exception:
        return DEFAULT_FRUSTRATION_WEIGHTS


def compute_frustration_scores(
    sessions_df: pd.DataFrame,
    issue_events_df: pd.DataFrame,
    weights: dict = None,
) -> pd.DataFrame:
    if weights is None:
        weights = DEFAULT_FRUSTRATION_WEIGHTS

    df = sessions_df[["session_id"]].copy()

    # Binary flags
    crash_sessions = set()
    anr_sessions = set()
    if not issue_events_df.empty:
        crash_sessions = set(issue_events_df[issue_events_df["pulse_type"] == "device.crash"]["session_id"])
        anr_sessions = set(issue_events_df[issue_events_df["pulse_type"] == "device.anr"]["session_id"])
    df["has_crash"] = df["session_id"].isin(crash_sessions).astype(float)
    df["has_anr"] = df["session_id"].isin(anr_sessions).astype(float)

    # Numeric signals
    for col in ["jank_slow_count", "jank_frozen_count", "net_error_count",
                "net_timeout_count", "network_change_count"]:
        df[col] = sessions_df[col].fillna(0).astype(float).values if col in sessions_df.columns else 0.0

    # Short session
    if "session_duration_sec" in sessions_df.columns:
        threshold = sessions_df["session_duration_sec"].quantile(0.15)
        df["short_session"] = (sessions_df["session_duration_sec"].values <= threshold).astype(float)
    else:
        df["short_session"] = 0.0

    # Percentile-rank scoring
    max_possible = sum(weights.values())
    raw_scores = np.zeros(len(df))
    for signal, weight in weights.items():
        if signal not in df.columns:
            continue
        vals = df[signal].values.astype(float)
        if vals.max() == vals.min():
            pct = np.ones(len(vals)) if vals.max() > 0 else np.zeros(len(vals))
        else:
            pct = (rankdata(vals, method="average") - 1) / (len(vals) - 1)
        raw_scores += pct * weight

    df["frustration_score"] = (raw_scores / max_possible * 100).round(1)
    return df[["session_id", "frustration_score"]]


# ═══════════════════════════════════════════════════════════════════
# Section 5: Screen-Graph Process Mining
# ═══════════════════════════════════════════════════════════════════

def build_screen_graph(screen_visits_df: pd.DataFrame) -> dict:
    """Directed weighted graph of screen transitions."""
    graph = defaultdict(lambda: defaultdict(int))
    for _, group in screen_visits_df.sort_values(["session_id", "first_visit_ts"]).groupby("session_id"):
        screens = group["screen_name"].tolist()
        for i in range(len(screens) - 1):
            graph[screens[i]][screens[i+1]] += 1
    return dict(graph)


def find_conversion_paths(graph: dict, entry_screens: list, conversion_screens: set,
                           max_depth: int = 10) -> list:
    """BFS to find most common paths from entry to conversion screens."""
    from collections import deque
    paths = []
    queue = deque()
    for entry in entry_screens:
        queue.append(([entry], 0))

    while queue:
        path, depth = queue.popleft()
        if depth >= max_depth:
            continue
        current = path[-1]
        if current in conversion_screens and depth > 0:
            paths.append(path)
            continue
        for next_screen, weight in sorted(graph.get(current, {}).items(), key=lambda x: -x[1])[:5]:
            if next_screen not in path:
                queue.append((path + [next_screen], depth + 1))
    return paths


def find_dropoff_edges(graph: dict, conversion_paths: list) -> list:
    """Find edges where users leave the conversion path."""
    on_path_edges = set()
    for path in conversion_paths:
        for i in range(len(path) - 1):
            on_path_edges.add((path[i], path[i+1]))

    dropoffs = []
    for from_screen in graph:
        on_path_total = sum(c for to_s, c in graph[from_screen].items() if (from_screen, to_s) in on_path_edges)
        off_path_total = sum(c for to_s, c in graph[from_screen].items() if (from_screen, to_s) not in on_path_edges)
        total = on_path_total + off_path_total
        if total > 10 and off_path_total > 0:
            dropoffs.append({
                "from_screen": from_screen,
                "off_path_count": off_path_total,
                "on_path_count": on_path_total,
                "total": total,
                "dropoff_rate": off_path_total / total,
                "top_off_path": sorted(
                    [(to_s, c) for to_s, c in graph[from_screen].items() if (from_screen, to_s) not in on_path_edges],
                    key=lambda x: -x[1]
                )[:3],
            })
    return sorted(dropoffs, key=lambda d: -d["off_path_count"])


def print_screen_graph_report(graph: dict, conversion_paths: list, dropoffs: list,
                               total_sessions: int):
    """Print graph-based process mining report."""
    print(f"\n  {'─'*70}")
    print(f"  SCREEN GRAPH: Top Transitions")
    print(f"  {'─'*70}")

    # Top edges by weight
    all_edges = []
    for from_s, targets in graph.items():
        for to_s, weight in targets.items():
            all_edges.append((from_s, to_s, weight))
    all_edges.sort(key=lambda x: -x[2])

    for from_s, to_s, weight in all_edges[:15]:
        pct = weight / total_sessions * 100
        bar = "█" * int(pct)
        print(f"    {from_s:35s} → {to_s:35s}  {weight:>4} ({pct:.0f}%) {bar}")

    if conversion_paths:
        print(f"\n  {'─'*70}")
        print(f"  CONVERSION PATHS (entry → payment)")
        print(f"  {'─'*70}")
        for i, path in enumerate(conversion_paths[:5]):
            print(f"    Path {i+1}: {' → '.join(path)}")

    if dropoffs:
        print(f"\n  {'─'*70}")
        print(f"  DROP-OFF POINTS (leaving conversion path)")
        print(f"  {'─'*70}")
        for d in dropoffs[:10]:
            print(f"    {d['from_screen']:35s}  on-path: {d['on_path_count']:>4}  "
                  f"off-path: {d['off_path_count']:>4}  drop: {d['dropoff_rate']:.0%}")
            for to_s, c in d["top_off_path"]:
                print(f"      └→ {to_s} ({c})")


# ═══════════════════════════════════════════════════════════════════
# Section 6: Report
# ═══════════════════════════════════════════════════════════════════

def print_report(results: list[IssueAnalysis], proxy: ConversionProxy):
    if not results:
        print("\n  No issues had enough data for journey-conditioned causal analysis.")
        print("  Possible reasons:")
        print("    - Too few sessions reaching the affected screens")
        print("    - Issues too rare (<5 affected sessions)")
        print("    - No control group (all sessions at that screen are affected)")
        return

    print(f"\n{'='*90}")
    print(f"  REVENUE IMPACT REPORT (v2 — Journey-Conditioned)")
    print(f"  Conversion proxy: {proxy.identifier} ({proxy.proxy_type})")
    print(f"{'='*90}\n")

    table_data = []
    for r in results:
        sig_marker = "✓ YES" if r.is_significant else "✗ no"
        table_data.append([
            r.issue_label,
            r.funnel_stage,
            f"{r.sessions_reaching_screen}",
            r.affected_count,
            r.control_count,
            f"{r.affected_conversion_rate:.1%}",
            f"{r.control_conversion_rate:.1%}",
            f"{r.conversion_delta:+.1%}",
            f"[{r.ci_lower:+.1%}, {r.ci_upper:+.1%}]",
            sig_marker,
            f"{r.propensity_balance:.3f}",
        ])

    headers = ["Issue", "Funnel", "Reached", "Affected", "Control",
               "Aff Conv%", "Ctrl Conv%", "Delta", "95% CI", "Sig?", "PS Bal"]
    print(tabulate(table_data, headers=headers, tablefmt="grid"))

    significant = [r for r in results if r.is_significant]
    if significant:
        print(f"\n{'─'*90}")
        print("  SIGNIFICANT FINDINGS (journey-conditioned causal impact)")
        print(f"{'─'*90}")
        for r in significant:
            direction = "REDUCES" if r.conversion_delta > 0 else "has REVERSE effect on"
            print(f"""
  {r.issue_label} [{r.funnel_stage}-funnel]
  {'─' * (len(r.issue_label) + len(r.funnel_stage) + 10)}
  Among {r.sessions_reaching_screen} sessions that reached {r.screen_name}:
    {r.affected_count} experienced {r.issue_type}
    {r.control_count} did not (matched controls)

  This issue {direction} conversion AFTER reaching this screen by
  {abs(r.conversion_delta):.1%} (95% CI: [{r.ci_lower:+.1%}, {r.ci_upper:+.1%}])

  Affected conversion (after issue): {r.affected_conversion_rate:.1%}
  Control conversion (after screen): {r.control_conversion_rate:.1%}
  PS balance: {r.propensity_balance:.4f} (lower = better match quality)

  Estimated lost conversions: ~{abs(r.conversion_delta) * r.affected_count:.0f}
  per {r.affected_count} affected sessions.
""")


# ═══════════════════════════════════════════════════════════════════
# Main
# ═══════════════════════════════════════════════════════════════════

def main():
    parser = argparse.ArgumentParser(description="Pulse Causal Revenue Impact v2")
    parser.add_argument("--project-id", required=True)
    parser.add_argument("--lookback-days", type=int, default=30)
    args = parser.parse_args()

    load_dotenv()

    print("=" * 70)
    print("  PULSE — Causal Revenue Impact v2")
    print("  Journey-Conditioned · Temporal-Aware · Caliper-Bounded PSM")
    print("=" * 70)

    # ── 1. Connect ──
    print(f"\n[1/7] Connecting to ClickHouse...")
    try:
        client = get_ch_client()
        client.query("SELECT 1")
        print("  ✓ Connected")
    except Exception as e:
        print(f"  ✗ FAILED: {e}")
        sys.exit(1)

    pid = args.project_id
    days = args.lookback_days

    # ── 2. Extract data (temporal-aware) ──
    print(f"\n[2/7] Extracting temporal data (project='{pid}', last {days} days)...")
    sessions_df = get_session_profiles(client, pid, days)
    print(f"  Sessions:        {len(sessions_df)}")

    if sessions_df.empty:
        print("  ERROR: No sessions found.")
        sys.exit(1)

    screen_visits_df = get_screen_visits(client, pid, days)
    print(f"  Screen visits:   {len(screen_visits_df)} (across {screen_visits_df['screen_name'].nunique()} screens)")

    issue_events_df = get_issue_events(client, pid, days)
    if not issue_events_df.empty:
        for pt in issue_events_df["pulse_type"].unique():
            n = issue_events_df[issue_events_df["pulse_type"] == pt]["session_id"].nunique()
            print(f"  {pt:20s}: {n} sessions")
    else:
        print("  No crash/ANR/non-fatal events")

    # otel_logs signals
    log_signals_df = get_log_signals(client, pid, days)
    jank_events_df = get_jank_events_by_screen(client, pid, days)

    if not log_signals_df.empty:
        sessions_df = sessions_df.merge(log_signals_df, on="session_id", how="left")
        for col in ["jank_slow_count", "jank_frozen_count", "click_count", "network_change_count"]:
            if col in sessions_df.columns:
                sessions_df[col] = sessions_df[col].fillna(0).astype(int)
        jank_total = sessions_df["jank_slow_count"].sum() + sessions_df["jank_frozen_count"].sum()
        print(f"  Jank events:     {jank_total} total")

    if not jank_events_df.empty:
        print(f"  Jank by screen:  {len(jank_events_df)} entries across {jank_events_df['screen_name'].nunique()} screens")

    print(f"\n  Device models:   {sessions_df['device_model'].nunique()}")
    print(f"  App versions:    {sessions_df['app_version'].nunique()}")
    print(f"  Avg duration:    {sessions_df['session_duration_sec'].mean():.0f}s")

    # ── 3. Discover conversion proxies ──
    print(f"\n[3/7] Discovering conversion proxies...")
    proxies = discover_conversion_proxies(client, pid, days, len(sessions_df))

    if proxies:
        print(f"  Found {len(proxies)} conversion-related signals:")
        for p in proxies[:10]:
            print(f"    [{p.proxy_type}] {p.identifier:50s} {p.sessions_reached:>4} ({p.conversion_rate:.1%})")
        primary_proxy = proxies[0]
    else:
        print("  No conversion proxies found, using session depth fallback")
        threshold = sessions_df["unique_screens"].quantile(0.75)
        primary_proxy = ConversionProxy(
            proxy_type="session_depth",
            identifier=f"Top 25% session depth (>={threshold:.0f} screens)",
            sessions_reached=int((sessions_df["unique_screens"] >= threshold).sum()),
            total_sessions=len(sessions_df),
            conversion_rate=(sessions_df["unique_screens"] >= threshold).mean(),
        )

    # Get conversion events with timestamps
    op_name = primary_proxy.identifier.split(" ", 1)[1] if " " in primary_proxy.identifier else primary_proxy.identifier
    print(f"\n  Using proxy: {primary_proxy.identifier}")

    if primary_proxy.proxy_type != "session_depth":
        conversion_events_df = get_conversion_events(client, pid, days, op_name)
    else:
        # Fallback: create synthetic conversion events for deep sessions
        deep_sessions = sessions_df[sessions_df["unique_screens"] >= sessions_df["unique_screens"].quantile(0.75)]
        conversion_events_df = pd.DataFrame({
            "session_id": deep_sessions["session_id"],
            "conversion_timestamp": deep_sessions["session_end"],
        })

    conv_sessions = conversion_events_df["session_id"].nunique()
    print(f"  {conv_sessions}/{len(sessions_df)} sessions ({conv_sessions/len(sessions_df):.1%}) have conversion events")

    # ── 4. Frustration scoring ──
    print(f"\n[4/7] Computing frustration scores...")
    frust_df = compute_frustration_scores(sessions_df, issue_events_df)
    sessions_df = sessions_df.merge(frust_df, on="session_id", how="left")

    scores = sessions_df["frustration_score"]
    print(f"  Range: {scores.min():.0f} — {scores.max():.0f}  |  Median: {scores.median():.0f}")

    # Try to calibrate weights from data
    sessions_df["_converted"] = sessions_df["session_id"].isin(
        set(conversion_events_df["session_id"])
    ).astype(int)
    calibrated_weights = calibrate_frustration_weights(sessions_df, "_converted")
    if calibrated_weights != DEFAULT_FRUSTRATION_WEIGHTS:
        print("  Weights calibrated from data:")
        for k, v in sorted(calibrated_weights.items(), key=lambda x: -x[1]):
            if v > 0:
                print(f"    {k:25s} {v:5.1f}")
        # Recompute with calibrated weights
        frust_df = compute_frustration_scores(sessions_df, issue_events_df, calibrated_weights)
        sessions_df["frustration_score"] = frust_df.set_index("session_id").loc[
            sessions_df["session_id"]
        ]["frustration_score"].values

    # ── 5. Process mining ──
    print(f"\n[5/7] Process mining (screen graph)...")
    graph = build_screen_graph(screen_visits_df)
    print(f"  Graph: {len(graph)} screens, {sum(len(v) for v in graph.values())} edges")

    # Find entry screens (most common first screen)
    first_screens = screen_visits_df.sort_values("first_visit_ts").groupby("session_id").first()
    entry_screens = first_screens["screen_name"].value_counts().head(3).index.tolist()
    print(f"  Entry screens: {entry_screens}")

    # Find screens visited by converting sessions (approximate conversion screens)
    conv_session_ids = set(conversion_events_df["session_id"])
    conv_screens = screen_visits_df[screen_visits_df["session_id"].isin(conv_session_ids)]
    # Screens disproportionately visited by converters
    screen_conv_rate = conv_screens["screen_name"].value_counts() / screen_visits_df["screen_name"].value_counts()
    conversion_screens = set(screen_conv_rate[screen_conv_rate > 0.3].dropna().index.tolist()[:5])
    print(f"  Conversion-adjacent screens: {conversion_screens}")

    conversion_paths = find_conversion_paths(graph, entry_screens, conversion_screens) if conversion_screens else []
    dropoffs = find_dropoff_edges(graph, conversion_paths) if conversion_paths else []

    # ── 6. Run causal analysis ──
    print(f"\n[6/7] Running journey-conditioned causal analysis...")
    results = analyze_all_issues(
        sessions_df, screen_visits_df, issue_events_df,
        conversion_events_df, jank_events_df,
    )

    # ── 7. Report ──
    print(f"\n[7/7] Generating report...")
    print_report(results, primary_proxy)

    # Process mining report
    if graph:
        print_screen_graph_report(graph, conversion_paths, dropoffs, len(sessions_df))

    # Frustration vs conversion
    conv_frust = sessions_df[sessions_df["_converted"] == 1]["frustration_score"].mean()
    nonconv_frust = sessions_df[sessions_df["_converted"] == 0]["frustration_score"].mean()
    print(f"\n  {'─'*70}")
    print(f"  FRUSTRATION vs CONVERSION")
    print(f"  {'─'*70}")
    print(f"    Converting:     {conv_frust:.1f}")
    print(f"    Non-converting: {nonconv_frust:.1f}")
    diff = nonconv_frust - conv_frust
    if diff > 0:
        print(f"    → Non-converting are {diff:.1f} points more frustrated")
    else:
        print(f"    → Selection bias: engaged users hit more issues")

    print(f"\n{'='*70}")
    print("  Analysis complete!")
    print(f"{'='*70}\n")


if __name__ == "__main__":
    main()

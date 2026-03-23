"""
Analysis Engine — journey-conditioned causal analysis with temporal ordering.

This is the core orchestrator that:
  1. Filters to sessions that REACHED the issue screen (journey conditioning)
  2. Enforces temporal ordering (conversion must happen AFTER the issue)
  3. Delegates to the PSM matching engine for causal estimation
  4. Applies Benjamini-Hochberg FDR correction across all tests
  5. Classifies funnel stage per screen
  6. Enriches results with user counts and exception details

Why journey conditioning matters:
  Without it, late-funnel crashes appear to HELP conversion because crash
  victims are already high-intent users. Conditioning on "reached the same
  screen" removes this selection bias.
"""
import numpy as np
import pandas as pd
from typing import Optional

from .config import CausalConfig
from .matching import propensity_match
from .models import IssueAnalysis


# ─── Funnel Classification ────────────────────────────────────────

def classify_funnel_stage(
    screen_name: str,
    screen_visits_df: pd.DataFrame,
    conversion_session_ids: set,
    cfg: CausalConfig,
) -> str:
    """
    Classify a screen as early/mid/late funnel based on what fraction
    of sessions reaching that screen eventually convert.

    Args:
        screen_name: The screen to classify.
        screen_visits_df: Per-session screen visits with timestamps.
        conversion_session_ids: Set of session_ids that had a conversion event.
        cfg: Configuration with funnel cutoffs.

    Returns:
        "early", "mid", "late", or "unknown"
    """
    sessions_reaching = set(
        screen_visits_df[screen_visits_df["screen_name"] == screen_name]["session_id"]
    )
    if not sessions_reaching:
        return "unknown"

    conversion_rate = len(sessions_reaching & conversion_session_ids) / len(sessions_reaching)

    if conversion_rate < cfg.funnel_early_cutoff:
        return "early"
    elif conversion_rate < cfg.funnel_late_cutoff:
        return "mid"
    else:
        return "late"


# ─── Temporal Conversion Check ────────────────────────────────────

def _build_conversion_lookup(conversion_events_df: pd.DataFrame) -> dict:
    """Build a lookup: session_id → list of conversion timestamps."""
    if conversion_events_df.empty:
        return {}
    return (
        conversion_events_df
        .groupby("session_id")["conversion_timestamp"]
        .apply(list)
        .to_dict()
    )


def _converted_after(session_id, ref_ts, conv_lookup: dict) -> bool:
    """Check if a session had any conversion event AFTER ref_ts."""
    convs = conv_lookup.get(session_id, [])
    return any(ct > ref_ts for ct in convs)


# ─── Journey-Conditioned Analysis (single issue) ─────────────────

def analyze_issue_journey_conditioned(
    sessions_df: pd.DataFrame,
    screen_visits_df: pd.DataFrame,
    issue_events_df: pd.DataFrame,
    conversion_events_df: pd.DataFrame,
    issue_type: str,
    issue_screen: str,
    cfg: CausalConfig,
) -> Optional[IssueAnalysis]:
    """
    Journey-conditioned PSM with temporal ordering for a single issue.

    Algorithm:
        1. Filter to sessions that REACHED issue_screen
        2. Split: affected (had issue on this screen) vs control (same screen, no issue)
        3. For affected: conversion = any conversion event AFTER issue timestamp
        4. For control: conversion = any conversion event AFTER arriving at screen
        5. Run PSM within this filtered population using device context only
        6. Return causal estimate with bootstrap CI

    Args:
        sessions_df: Session-level feature vectors (device context).
        screen_visits_df: Per-session screen visits with first_visit_ts.
        issue_events_df: Individual issue events with issue_timestamp.
        conversion_events_df: Individual conversion events with conversion_timestamp.
        issue_type: PulseType to analyze (e.g., "device.crash").
        issue_screen: Screen where the issue occurred.
        cfg: Analysis configuration.

    Returns:
        IssueAnalysis result, or None if insufficient data.
    """
    # ── Step 1: Sessions that reached the issue screen ──
    screen_sessions = screen_visits_df[screen_visits_df["screen_name"] == issue_screen]
    sessions_reaching = set(screen_sessions["session_id"])

    if len(sessions_reaching) < cfg.min_affected + cfg.min_control:
        return None

    # ── Step 2: Split affected vs control ──
    issue_subset = issue_events_df[
        (issue_events_df["pulse_type"] == issue_type) &
        (issue_events_df["screen_name"] == issue_screen)
    ]
    affected_ids = set(issue_subset["session_id"].unique())
    control_ids = sessions_reaching - affected_ids

    if len(affected_ids) < cfg.min_affected or len(control_ids) < cfg.min_control:
        return None

    # ── Step 3: Temporal conversion for affected sessions ──
    earliest_issue = issue_subset.groupby("session_id")["issue_timestamp"].min()
    conv_lookup = _build_conversion_lookup(conversion_events_df)

    affected_converted = {}
    for sid in affected_ids:
        if sid in earliest_issue.index:
            affected_converted[sid] = _converted_after(sid, earliest_issue[sid], conv_lookup)
        else:
            affected_converted[sid] = False

    # ── Step 4: Temporal conversion for control sessions ──
    screen_arrival = screen_sessions.groupby("session_id")["first_visit_ts"].min()

    control_converted = {}
    for sid in control_ids:
        if sid in screen_arrival.index:
            control_converted[sid] = _converted_after(sid, screen_arrival[sid], conv_lookup)
        else:
            control_converted[sid] = False

    # ── Step 5: Build filtered dataframe for PSM ──
    all_ids = affected_ids | control_ids
    filtered = sessions_df[sessions_df["session_id"].isin(all_ids)].copy()

    if len(filtered) < cfg.min_affected + cfg.min_control:
        return None

    filtered["is_affected"] = filtered["session_id"].isin(affected_ids).astype(int)
    all_converted = {**affected_converted, **control_converted}
    filtered["converted"] = filtered["session_id"].map(all_converted).fillna(False).astype(int)

    # ── Step 6: Delegate to PSM engine ──
    result = propensity_match(
        df=filtered,
        treatment_col="is_affected",
        outcome_col="converted",
        cfg=cfg,
    )

    if result is None:
        return None

    # ── Step 7: Enrich with context ──
    conversion_session_ids = set(conversion_events_df["session_id"].unique())
    funnel_stage = classify_funnel_stage(
        issue_screen, screen_visits_df, conversion_session_ids, cfg,
    )

    # User count (distinct users affected)
    affected_user_count = 0
    if "user_id" in sessions_df.columns:
        affected_user_count = (
            sessions_df[sessions_df["session_id"].isin(affected_ids)]["user_id"]
            .nunique()
        )

    # Top exception type
    exception_detail = ""
    if "exception_type" in issue_events_df.columns:
        exc_types = issue_subset["exception_type"].dropna()
        if not exc_types.empty:
            exception_detail = exc_types.value_counts().index[0]

    priority = abs(result["att"]) * result["n_treated"] * (1.0 if result["is_significant"] else 0.5)

    return IssueAnalysis(
        issue_type=issue_type,
        issue_label=f"{issue_type} on {issue_screen or 'unknown'}",
        screen_name=issue_screen,
        funnel_stage=funnel_stage,
        sessions_reaching_screen=len(sessions_reaching),
        affected_count=result["n_treated"],
        control_count=result["n_control"],
        affected_conversion_rate=result["treated_rate"],
        control_conversion_rate=result["control_rate"],
        conversion_delta=result["att"],
        ci_lower=result["ci_lower"],
        ci_upper=result["ci_upper"],
        is_significant=result["is_significant"],
        bootstrap_p_value=result["p_value"],
        propensity_balance=result["ps_balance"],
        caliper_applied=result["caliper_applied"],
        common_support_pct=result["common_support_pct"],
        priority_score=priority,
        affected_user_count=affected_user_count,
        exception_detail=exception_detail,
    )


# ─── Analyze Network Errors (session-level, no journey conditioning) ──

def analyze_network_errors(
    sessions_df: pd.DataFrame,
    conversion_events_df: pd.DataFrame,
    cfg: CausalConfig,
) -> Optional[IssueAnalysis]:
    """
    Network error analysis — session-level PSM without journey conditioning.

    Network errors are diffuse (not tied to a single screen), so we use
    simple PSM with a threshold: sessions with ≥ net_error_threshold errors
    are "treated."

    Note: This is a weaker causal claim than journey-conditioned analysis.
    The result should be interpreted as associational + adjusted, not fully causal.
    """
    if "net_error_count" not in sessions_df.columns:
        return None

    high_error_ids = set(
        sessions_df[sessions_df["net_error_count"] >= cfg.net_error_threshold]["session_id"]
    )
    if len(high_error_ids) < cfg.min_affected:
        return None

    conv_session_ids = set(conversion_events_df["session_id"].unique())
    filtered = sessions_df.copy()
    filtered["is_affected"] = filtered["session_id"].isin(high_error_ids).astype(int)
    filtered["converted"] = filtered["session_id"].isin(conv_session_ids).astype(int)

    result = propensity_match(
        df=filtered,
        treatment_col="is_affected",
        outcome_col="converted",
        cfg=cfg,
    )

    if result is None:
        return None

    priority = abs(result["att"]) * result["n_treated"] * (1.0 if result["is_significant"] else 0.5)

    return IssueAnalysis(
        issue_type="network_errors",
        issue_label=f"Sessions with {cfg.net_error_threshold}+ network errors",
        screen_name="(session-level)",
        funnel_stage="any",
        sessions_reaching_screen=len(sessions_df),
        affected_count=result["n_treated"],
        control_count=result["n_control"],
        affected_conversion_rate=result["treated_rate"],
        control_conversion_rate=result["control_rate"],
        conversion_delta=result["att"],
        ci_lower=result["ci_lower"],
        ci_upper=result["ci_upper"],
        is_significant=result["is_significant"],
        bootstrap_p_value=result["p_value"],
        propensity_balance=result["ps_balance"],
        caliper_applied=result["caliper_applied"],
        common_support_pct=result["common_support_pct"],
        priority_score=priority,
    )


# ─── FDR Correction ──────────────────────────────────────────────

def apply_fdr_correction(results: list[IssueAnalysis], alpha: float = 0.05) -> list[IssueAnalysis]:
    """
    Apply Benjamini-Hochberg FDR correction across all test results.

    This controls the false discovery rate when running many simultaneous
    tests (one per issue-screen pair). Without FDR, running 20 tests at
    α=0.05 expects ~1 false positive.

    Modifies is_significant in-place based on adjusted p-values.
    """
    if not results:
        return results

    p_values = np.array([r.bootstrap_p_value for r in results])
    n = len(p_values)

    # Benjamini-Hochberg procedure
    sorted_idx = np.argsort(p_values)
    sorted_p = p_values[sorted_idx]
    thresholds = np.arange(1, n + 1) / n * alpha

    # Find the largest k where p_(k) <= k/n * alpha
    significant_mask = sorted_p <= thresholds
    if significant_mask.any():
        max_k = np.max(np.where(significant_mask)[0])
        # All tests with rank <= max_k are significant
        sig_indices = set(sorted_idx[:max_k + 1])
    else:
        sig_indices = set()

    for i, r in enumerate(results):
        r.is_significant = i in sig_indices

    return results


# ─── Main Orchestrator ────────────────────────────────────────────

def analyze_all_issues(
    sessions_df: pd.DataFrame,
    screen_visits_df: pd.DataFrame,
    issue_events_df: pd.DataFrame,
    conversion_events_df: pd.DataFrame,
    cfg: CausalConfig,
    jank_events_df: pd.DataFrame = None,
    verbose: bool = True,
) -> list[IssueAnalysis]:
    """
    Run journey-conditioned causal analysis for every distinct issue-screen pair.

    Analyzes:
      1. Crashes, ANRs, non-fatals (from stack_trace_events) — per screen
      2. Jank events (from otel_logs) — per screen
      3. Network errors (session-level threshold)

    Applies FDR correction if cfg.apply_fdr is True.

    Args:
        sessions_df: Session feature vectors.
        screen_visits_df: Per-session screen visits.
        issue_events_df: Individual crash/ANR/non-fatal events.
        conversion_events_df: Individual conversion events.
        cfg: Analysis configuration.
        jank_events_df: Jank events by screen (optional).
        verbose: Print progress messages.

    Returns:
        List of IssueAnalysis results, sorted by priority_score descending.
    """
    results = []

    # ── 1. Crashes / ANRs / Non-fatals ──
    if not issue_events_df.empty:
        issue_groups = issue_events_df.groupby(["pulse_type", "screen_name"])
        for (pulse_type, screen_name), group in issue_groups:
            if not screen_name:
                continue
            n_sessions = group["session_id"].nunique()
            if n_sessions < cfg.min_affected:
                if verbose:
                    print(f"    {pulse_type} on {screen_name}: {n_sessions} sessions, "
                          f"skipping (need {cfg.min_affected}+)")
                continue

            analysis = analyze_issue_journey_conditioned(
                sessions_df, screen_visits_df, issue_events_df,
                conversion_events_df, pulse_type, screen_name, cfg,
            )
            if analysis:
                results.append(analysis)
                if verbose:
                    sig = "✓" if analysis.is_significant else "✗"
                    print(f"    {sig} {analysis.issue_label}: "
                          f"Δ={analysis.conversion_delta:+.1%} "
                          f"[{analysis.ci_lower:+.1%}, {analysis.ci_upper:+.1%}]")
            elif verbose:
                print(f"    {pulse_type} on {screen_name}: "
                      f"insufficient data after journey conditioning")

    # ── 2. Jank events ──
    if jank_events_df is not None and not jank_events_df.empty:
        jank_as_issues = jank_events_df[
            ["session_id", "pulse_type", "screen_name", "issue_timestamp"]
        ].copy()
        jank_groups = jank_as_issues.groupby(["pulse_type", "screen_name"])

        for (pulse_type, screen_name), group in jank_groups:
            if not screen_name or group["session_id"].nunique() < cfg.min_affected:
                continue

            analysis = analyze_issue_journey_conditioned(
                sessions_df, screen_visits_df, jank_as_issues,
                conversion_events_df, pulse_type, screen_name, cfg,
            )
            if analysis:
                results.append(analysis)
                if verbose:
                    sig = "✓" if analysis.is_significant else "✗"
                    print(f"    {sig} {analysis.issue_label}: "
                          f"Δ={analysis.conversion_delta:+.1%}")

    # ── 3. Network errors (session-level) ──
    net_result = analyze_network_errors(sessions_df, conversion_events_df, cfg)
    if net_result:
        results.append(net_result)
        if verbose:
            sig = "✓" if net_result.is_significant else "✗"
            print(f"    {sig} {net_result.issue_label}: "
                  f"Δ={net_result.conversion_delta:+.1%}")

    # ── 4. FDR correction ──
    if cfg.apply_fdr and len(results) > 1:
        pre_sig = sum(1 for r in results if r.is_significant)
        results = apply_fdr_correction(results, cfg.alpha)
        post_sig = sum(1 for r in results if r.is_significant)
        if verbose and pre_sig != post_sig:
            print(f"\n    FDR correction: {pre_sig} → {post_sig} significant findings")

    # Sort by priority
    results.sort(key=lambda r: r.priority_score, reverse=True)
    return results

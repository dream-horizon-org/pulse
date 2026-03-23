#!/usr/bin/env python3
"""
Pulse — Causal Revenue Impact Analysis
=======================================
Main entry point for running the modular causal analysis pipeline.

Supports two modes:
  - Small scale (<100K sessions): scans raw event tables directly
  - PB scale (20TB/day): reads pre-aggregated MVs + deterministic sampling

The pipeline:
  1. ClickHouse data extraction (MV-aware, with sampling)
  2. Conversion proxy auto-discovery
  3. Frustration scoring (with optional calibration)
  4. Screen-graph process mining
  5. Journey-conditioned causal analysis (PSM with temporal ordering)
  6. Formatted revenue impact report

Usage:
    # Small scale (existing behavior)
    python run_causal_analysis.py --project-id fancode --lookback-days 60

    # PB scale (sampling + MVs)
    python run_causal_analysis.py --project-id fancode --max-sessions 50000

Environment:
    Reads ClickHouse credentials from .env file in the same directory.
    Required: CLICKHOUSE_HOST, CLICKHOUSE_PORT, CLICKHOUSE_USER,
              CLICKHOUSE_PASSWORD, CLICKHOUSE_DATABASE
"""
import argparse
import os
import sys
import warnings

import pandas as pd
from dotenv import load_dotenv

from causal.config import CausalConfig
from causal.data import (
    get_ch_client,
    get_session_profiles,
    get_screen_visits,
    get_issue_events,
    get_conversion_events,
    get_jank_events_by_screen,
    get_log_signals,
    discover_conversion_proxies,
)
from causal.models import ConversionProxy
from causal.analysis import analyze_all_issues
from causal.frustration import (
    compute_frustration_scores,
    calibrate_frustration_weights,
    DEFAULT_FRUSTRATION_WEIGHTS,
)
from causal.mining import (
    build_screen_graph,
    find_entry_screens,
    find_conversion_adjacent_screens,
    find_conversion_paths,
    find_dropoff_edges,
)
from causal.report import (
    print_report,
    print_screen_graph_report,
    print_frustration_report,
)

warnings.filterwarnings("ignore")


def main():
    parser = argparse.ArgumentParser(
        description="Pulse — Causal Revenue Impact Analysis",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  python run_causal_analysis.py --project-id fancode
  python run_causal_analysis.py --project-id fancode --lookback-days 60
  python run_causal_analysis.py --project-id fancode --max-sessions 50000
  python run_causal_analysis.py --project-id fancode --no-fdr --min-affected 5
        """,
    )
    parser.add_argument("--project-id", required=True, help="ClickHouse ProjectId")
    parser.add_argument("--lookback-days", type=int, default=30, help="Days of data to analyze")
    parser.add_argument("--max-sessions", type=int, default=None,
                        help="Cap sessions via deterministic sampling (recommended: 50000 for PB scale)")
    parser.add_argument("--no-fdr", action="store_true", help="Disable FDR correction")
    parser.add_argument("--min-affected", type=int, default=10, help="Minimum affected sessions")
    parser.add_argument("--k-neighbors", type=int, default=5, help="Matched controls per treated")
    parser.add_argument("--quiet", action="store_true", help="Reduce verbose output")
    args = parser.parse_args()

    # Load environment
    load_dotenv()

    # Build config
    cfg = CausalConfig(
        min_affected=args.min_affected,
        min_control=args.min_affected,
        k_neighbors=args.k_neighbors,
        apply_fdr=not args.no_fdr,
    )

    scale_mode = "sampled" if args.max_sessions else "full"
    print("=" * 75)
    print("  PULSE — Causal Revenue Impact Analysis")
    print("  Journey-Conditioned · Temporal-Aware · One-Hot Encoded PSM")
    print("  BCa Bootstrap CI · Common Support · FDR Correction")
    if args.max_sessions:
        print(f"  Scale mode: SAMPLED ({args.max_sessions:,} sessions max)")
    print("=" * 75)

    # ═══════════════════════════════════════════════════════════════
    # Step 1: Connect to ClickHouse
    # ═══════════════════════════════════════════════════════════════
    print(f"\n[1/7] Connecting to ClickHouse...")
    try:
        client = get_ch_client()
        client.query("SELECT 1")
        host = os.getenv("CLICKHOUSE_HOST", "localhost")
        print(f"  ✓ Connected to {host}")
    except Exception as e:
        print(f"  ✗ Connection failed: {e}")
        sys.exit(1)

    pid = args.project_id
    days = args.lookback_days

    # ═══════════════════════════════════════════════════════════════
    # Step 2: Extract temporal data (MV-aware + sampling)
    # ═══════════════════════════════════════════════════════════════
    print(f"\n[2/7] Extracting temporal data (project='{pid}', last {days} days)...")

    sessions_df = get_session_profiles(client, pid, days, max_sessions=args.max_sessions)
    print(f"  Sessions:        {len(sessions_df):,}" +
          (f" (sampled from population)" if args.max_sessions else ""))
    if sessions_df.empty:
        print("  ✗ No sessions found. Check project-id and lookback-days.")
        sys.exit(1)

    # When sampling, pass session_ids to downstream queries to avoid scanning
    # events for sessions we won't analyze. This is the key PB-scale optimization:
    # instead of scanning 12B sessions of events, only fetch for our 50K sample.
    session_ids = set(sessions_df["session_id"]) if args.max_sessions else None

    screen_visits_df = get_screen_visits(client, pid, days, session_ids=session_ids)
    print(f"  Screen visits:   {len(screen_visits_df):,} "
          f"(across {screen_visits_df['screen_name'].nunique()} screens)")

    issue_events_df = get_issue_events(client, pid, days, session_ids=session_ids)
    if not issue_events_df.empty:
        for pt in issue_events_df["pulse_type"].unique():
            n = issue_events_df[issue_events_df["pulse_type"] == pt]["session_id"].nunique()
            print(f"  {pt:20s}: {n} sessions")
    else:
        print("  No crash/ANR/non-fatal events found")

    # Log signals (jank, clicks, network changes)
    log_signals_df = get_log_signals(client, pid, days, session_ids=session_ids)
    jank_events_df = get_jank_events_by_screen(client, pid, days, session_ids=session_ids)

    if not log_signals_df.empty:
        sessions_df = sessions_df.merge(log_signals_df, on="session_id", how="left")
        for col in ["jank_slow_count", "jank_frozen_count", "click_count", "network_change_count"]:
            if col in sessions_df.columns:
                sessions_df[col] = sessions_df[col].fillna(0).astype(int)
        jank_total = sessions_df["jank_slow_count"].sum() + sessions_df["jank_frozen_count"].sum()
        print(f"  Jank events:     {jank_total} total")

    if not jank_events_df.empty:
        print(f"  Jank by screen:  {len(jank_events_df)} entries "
              f"across {jank_events_df['screen_name'].nunique()} screens")

    if not args.quiet:
        print(f"\n  Device models:   {sessions_df['device_model'].nunique()}")
        print(f"  App versions:    {sessions_df['app_version'].nunique()}")
        print(f"  Avg duration:    {sessions_df['session_duration_sec'].mean():.0f}s")

    # ═══════════════════════════════════════════════════════════════
    # Step 3: Discover conversion proxies
    # ═══════════════════════════════════════════════════════════════
    print(f"\n[3/7] Discovering conversion proxies...")
    proxies = discover_conversion_proxies(client, pid, days, len(sessions_df))

    if proxies:
        print(f"  Found {len(proxies)} conversion-related signals:")
        for p in proxies[:10]:
            print(f"    [{p.proxy_type}] {p.identifier:50s} "
                  f"{p.sessions_reached:>4} ({p.conversion_rate:.1%})")
        primary_proxy = proxies[0]
    else:
        print("  No conversion proxies found — using session depth fallback")
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
        conversion_events_df = get_conversion_events(
            client, pid, days, op_name, session_ids=session_ids,
        )
    else:
        # Fallback: synthetic conversion events for deep sessions
        deep = sessions_df[sessions_df["unique_screens"] >= sessions_df["unique_screens"].quantile(0.75)]
        conversion_events_df = pd.DataFrame({
            "session_id": deep["session_id"],
            "conversion_timestamp": deep["session_end"],
        })

    conv_sessions = conversion_events_df["session_id"].nunique()
    print(f"  {conv_sessions}/{len(sessions_df)} sessions "
          f"({conv_sessions/len(sessions_df):.1%}) have conversion events")

    # ═══════════════════════════════════════════════════════════════
    # Step 4: Frustration scoring
    # ═══════════════════════════════════════════════════════════════
    print(f"\n[4/7] Computing frustration scores...")

    frust_df = compute_frustration_scores(sessions_df, issue_events_df)
    sessions_df = sessions_df.merge(frust_df, on="session_id", how="left")

    scores = sessions_df["frustration_score"]
    print(f"  Range: {scores.min():.0f} — {scores.max():.0f}  |  Median: {scores.median():.0f}")

    # Try calibrating weights from conversion data
    sessions_df["_converted"] = sessions_df["session_id"].isin(
        set(conversion_events_df["session_id"])
    ).astype(int)

    calibrated_weights = calibrate_frustration_weights(sessions_df, "_converted")
    calibrated = calibrated_weights != DEFAULT_FRUSTRATION_WEIGHTS

    if calibrated:
        print("  ✓ Weights calibrated from conversion data:")
        for k, v in sorted(calibrated_weights.items(), key=lambda x: -x[1]):
            if v > 0:
                print(f"    {k:25s} {v:5.1f}")
        # Recompute with calibrated weights
        frust_df = compute_frustration_scores(sessions_df, issue_events_df, calibrated_weights)
        sessions_df["frustration_score"] = frust_df.set_index("session_id").loc[
            sessions_df["session_id"]
        ]["frustration_score"].values
    else:
        print("  Using default weights (insufficient data for calibration)")

    # ═══════════════════════════════════════════════════════════════
    # Step 5: Process mining
    # ═══════════════════════════════════════════════════════════════
    print(f"\n[5/7] Process mining (screen graph)...")

    graph = build_screen_graph(screen_visits_df)
    print(f"  Graph: {len(graph)} screens, {sum(len(v) for v in graph.values())} edges")

    entry_screens = find_entry_screens(screen_visits_df)
    print(f"  Entry screens: {entry_screens}")

    conv_session_ids = set(conversion_events_df["session_id"])
    conversion_screens = find_conversion_adjacent_screens(
        screen_visits_df, conv_session_ids,
    )
    print(f"  Conversion-adjacent screens: {conversion_screens}")

    conversion_paths = find_conversion_paths(
        graph, entry_screens, conversion_screens, cfg.max_graph_depth,
    ) if conversion_screens else []
    dropoffs = find_dropoff_edges(
        graph, conversion_paths, cfg.min_edge_weight,
    ) if conversion_paths else []

    # ═══════════════════════════════════════════════════════════════
    # Step 6: Journey-conditioned causal analysis
    # ═══════════════════════════════════════════════════════════════
    print(f"\n[6/7] Running journey-conditioned causal analysis...")
    print(f"  Config: k={cfg.k_neighbors}, caliper={cfg.caliper_sd}σ, "
          f"bootstrap={cfg.n_bootstrap}, FDR={'on' if cfg.apply_fdr else 'off'}")

    results = analyze_all_issues(
        sessions_df=sessions_df,
        screen_visits_df=screen_visits_df,
        issue_events_df=issue_events_df,
        conversion_events_df=conversion_events_df,
        cfg=cfg,
        jank_events_df=jank_events_df,
        verbose=not args.quiet,
    )

    # ═══════════════════════════════════════════════════════════════
    # Step 7: Report
    # ═══════════════════════════════════════════════════════════════
    print(f"\n[7/7] Generating report...")

    print_report(results, primary_proxy)

    if graph:
        print_screen_graph_report(graph, conversion_paths, dropoffs, len(sessions_df))

    # Frustration vs conversion
    conv_frust = sessions_df[sessions_df["_converted"] == 1]["frustration_score"].mean()
    nonconv_frust = sessions_df[sessions_df["_converted"] == 0]["frustration_score"].mean()
    print_frustration_report(conv_frust, nonconv_frust, calibrated=calibrated)

    print(f"\n{'='*75}")
    print(f"  Analysis complete — {len(results)} issues analyzed, "
          f"{sum(1 for r in results if r.is_significant)} significant")
    if args.max_sessions:
        print(f"  Sampled {len(sessions_df):,} sessions (deterministic, reproducible)")
    print(f"{'='*75}\n")


if __name__ == "__main__":
    main()

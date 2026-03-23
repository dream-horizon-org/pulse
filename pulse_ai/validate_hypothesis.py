#!/usr/bin/env python3
"""
Pulse — Hypothesis Validation on Public Dataset
=================================================
Runs our causal revenue impact analysis against the UCI Online Shoppers
Purchasing Intention dataset (12,330 sessions) to validate that:

  1. Technical friction (high bounce/exit rates) causally reduces conversion
  2. Propensity score matching correctly isolates the causal effect
  3. Results are robust under multiple validation tests

Validation methods:
  A. Propensity Score Matching — our core method
  B. Placebo Test — run PSM on a fake "issue" (random assignment) → expect NO effect
  C. Dose-Response — more friction = bigger conversion drop (monotonic)
  D. Stratified Validation — effect holds across OS/Browser/Region subgroups
  E. Sensitivity Analysis — vary matching k, caliper, features → stable results
  F. A/A Test — split control group randomly → expect zero delta

Usage:
    python validate_hypothesis.py
"""

import warnings
warnings.filterwarnings("ignore")

import numpy as np
import pandas as pd
from collections import Counter
from dataclasses import dataclass
from scipy.stats import rankdata
from sklearn.linear_model import LogisticRegression
from sklearn.neighbors import NearestNeighbors
from sklearn.preprocessing import LabelEncoder
from tabulate import tabulate
import os

# ═══════════════════════════════════════════════════════════════════
# Load & Prepare Data
# ═══════════════════════════════════════════════════════════════════

def load_dataset() -> pd.DataFrame:
    """Load UCI Online Shoppers dataset and map to Pulse-like schema."""
    script_dir = os.path.dirname(os.path.abspath(__file__))
    path = os.path.join(script_dir, "datasets", "online_shoppers_intention.csv")
    df = pd.read_csv(path)

    # Rename to Pulse-like schema for familiarity
    df = df.rename(columns={
        "Revenue": "converted",
        "OperatingSystems": "os_version",
        "Browser": "browser",
        "Region": "region",
        "TrafficType": "traffic_type",
        "VisitorType": "visitor_type",
        "Weekend": "is_weekend",
    })
    df["converted"] = df["converted"].astype(int)
    df["session_id"] = [f"s_{i}" for i in range(len(df))]

    # ── Create "technical issue" signals from the data ──
    # BounceRates: % of visitors who enter and immediately leave (higher = worse UX)
    # ExitRates: % of pageviews that were the last in the session (higher = worse UX)
    # These are our "auto-instrumented" friction signals, analogous to:
    #   BounceRate → crash/ANR (session-ending failures)
    #   ExitRate → jank/slow-load (gradual friction causing abandonment)
    #   Low ProductRelated pages → shallow session (like low unique_screens)

    # Define treatment groups (sessions with "technical issues")
    df["high_bounce"] = (df["BounceRates"] >= df["BounceRates"].quantile(0.75)).astype(int)
    df["high_exit"] = (df["ExitRates"] >= df["ExitRates"].quantile(0.75)).astype(int)
    df["very_high_bounce"] = (df["BounceRates"] >= df["BounceRates"].quantile(0.90)).astype(int)
    df["very_high_exit"] = (df["ExitRates"] >= df["ExitRates"].quantile(0.90)).astype(int)

    # Composite frustration score (analogous to our Pulse frustration score)
    signals = {}
    for col in ["BounceRates", "ExitRates", "Administrative_Duration",
                "Informational_Duration", "ProductRelated_Duration"]:
        vals = df[col].values.astype(float)
        if vals.max() > vals.min():
            signals[col] = (rankdata(vals, method="average") - 1) / (len(vals) - 1)
        else:
            signals[col] = np.zeros(len(vals))

    # Invert durations — very SHORT durations indicate frustration/bounce
    # (user left quickly = frustrated)
    for col in ["Administrative_Duration", "Informational_Duration", "ProductRelated_Duration"]:
        if col in signals:
            signals[col] = 1.0 - signals[col]  # flip: low duration = high frustration

    weights = {
        "BounceRates": 30,
        "ExitRates": 25,
        "Administrative_Duration": 15,
        "Informational_Duration": 10,
        "ProductRelated_Duration": 20,
    }
    total_weight = sum(weights.values())
    raw = np.zeros(len(df))
    for col, w in weights.items():
        if col in signals:
            raw += signals[col] * w
    df["frustration_score"] = (raw / total_weight * 100).round(1)

    df["high_frustration"] = (df["frustration_score"] >= df["frustration_score"].quantile(0.80)).astype(int)

    return df


# ═══════════════════════════════════════════════════════════════════
# Core Engine: Propensity Score Matching (same algo as prototype)
# ═══════════════════════════════════════════════════════════════════

MATCHING_FEATURES = ["os_version", "browser", "region", "traffic_type",
                     "visitor_type", "is_weekend", "Month", "SpecialDay"]


def encode_features(df: pd.DataFrame, features: list) -> np.ndarray:
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


def propensity_score_match(
    df: pd.DataFrame,
    treatment_col: str,
    outcome_col: str = "converted",
    features: list = None,
    k: int = 3,
    caliper: float = None,
) -> dict:
    """
    Run PSM and return results dict.
    If caliper is set, discard matches with propensity distance > caliper.
    """
    if features is None:
        features = MATCHING_FEATURES

    X = encode_features(df, features)
    y = df[treatment_col].values

    treated = df[df[treatment_col] == 1]
    control = df[df[treatment_col] == 0]

    if len(treated) < 5 or len(control) < 10:
        return None

    # Fit propensity model
    try:
        model = LogisticRegression(max_iter=1000, random_state=42)
        model.fit(X, y)
        ps = model.predict_proba(X)[:, 1]
    except Exception:
        return None

    df = df.copy()
    df["propensity"] = ps

    treated_idx = df[df[treatment_col] == 1].index
    control_idx = df[df[treatment_col] == 0].index

    k_actual = min(k, len(control_idx))
    control_ps = df.loc[control_idx, "propensity"].values.reshape(-1, 1)
    nn = NearestNeighbors(n_neighbors=k_actual, metric="euclidean")
    nn.fit(control_ps)

    treated_ps = df.loc[treated_idx, "propensity"].values.reshape(-1, 1)
    distances, indices = nn.kneighbors(treated_ps)

    # Apply caliper if specified
    if caliper is not None:
        mask = distances[:, 0] <= caliper
        treated_idx = treated_idx[mask]
        indices = indices[mask]
        distances = distances[mask]

    if len(treated_idx) < 5:
        return None

    matched_control_idx = control_idx[indices.flatten()].unique()

    treated_conv = df.loc[treated_idx, outcome_col]
    control_conv = df.loc[matched_control_idx, outcome_col]

    treated_rate = treated_conv.mean()
    control_rate = control_conv.mean()
    delta = control_rate - treated_rate

    # Bootstrap CI
    ci_lower, ci_upper, is_sig = bootstrap_ci(treated_conv.values, control_conv.values)

    return {
        "treatment": treatment_col,
        "n_treated": len(treated_idx),
        "n_control": len(matched_control_idx),
        "treated_rate": treated_rate,
        "control_rate": control_rate,
        "delta": delta,
        "ci_lower": ci_lower,
        "ci_upper": ci_upper,
        "is_significant": is_sig,
        "propensity_balance": abs(
            df.loc[treated_idx, "propensity"].mean() -
            df.loc[matched_control_idx, "propensity"].mean()
        ),
    }


def bootstrap_ci(treated: np.ndarray, control: np.ndarray, n_boot: int = 1000, alpha: float = 0.05):
    rng = np.random.RandomState(42)
    deltas = []
    for _ in range(n_boot):
        a = rng.choice(treated, size=len(treated), replace=True)
        c = rng.choice(control, size=len(control), replace=True)
        deltas.append(c.mean() - a.mean())
    lower = np.percentile(deltas, 100 * alpha / 2)
    upper = np.percentile(deltas, 100 * (1 - alpha / 2))
    is_sig = (lower > 0 and upper > 0) or (lower < 0 and upper < 0)
    return lower, upper, is_sig


# ═══════════════════════════════════════════════════════════════════
# Process Mining: Funnel from Page Types
# ═══════════════════════════════════════════════════════════════════

def funnel_analysis(df: pd.DataFrame):
    """Analyze the page-type funnel: Administrative → Informational → ProductRelated → Purchase."""
    print(f"\n{'═'*70}")
    print("  PROCESS MINING: Page-Type Funnel")
    print(f"{'═'*70}")

    total = len(df)
    steps = [
        ("All Sessions", total, df["converted"].mean()),
        ("Visited Admin Pages (>0)", len(df[df["Administrative"] > 0]),
         df[df["Administrative"] > 0]["converted"].mean()),
        ("Visited Info Pages (>0)", len(df[df["Informational"] > 0]),
         df[df["Informational"] > 0]["converted"].mean()),
        ("Visited Product Pages (>5)", len(df[df["ProductRelated"] > 5]),
         df[df["ProductRelated"] > 5]["converted"].mean()),
        ("Deep Product Browse (>30)", len(df[df["ProductRelated"] > 30]),
         df[df["ProductRelated"] > 30]["converted"].mean()),
        ("Very Deep Browse (>60)", len(df[df["ProductRelated"] > 60]),
         df[df["ProductRelated"] > 60]["converted"].mean()),
    ]

    for name, count, conv in steps:
        pct = count / total * 100
        bar_len = int(pct / 2)
        bar = "█" * bar_len + "░" * (50 - bar_len)
        print(f"  {name:35s} {bar} {count:>5}/{total} ({pct:4.0f}%)  Conv: {conv:.1%}")

    # Journey depth vs conversion
    print(f"\n  Page Depth vs Conversion:")
    for pages_col, label in [("Administrative", "Admin"), ("Informational", "Info"), ("ProductRelated", "Product")]:
        buckets = pd.qcut(df[pages_col].clip(0), q=4, duplicates="drop")
        grouped = df.groupby(buckets)["converted"].agg(["mean", "count"])
        print(f"    {label} pages:")
        for bucket, row in grouped.iterrows():
            print(f"      {str(bucket):20s}  n={int(row['count']):>5}  conv={row['mean']:.1%}")


# ═══════════════════════════════════════════════════════════════════
# Validation Tests
# ═══════════════════════════════════════════════════════════════════

def run_validation_suite(df: pd.DataFrame):
    """Run all validation tests."""
    all_results = {}

    # ══════════════════════════════════════════════
    # TEST A: Core Hypothesis — Technical Issues → Lower Conversion
    # ══════════════════════════════════════════════
    print(f"\n{'═'*70}")
    print("  TEST A: Core Hypothesis — Friction Causally Reduces Conversion")
    print(f"{'═'*70}")

    issues = [
        ("high_bounce", "High Bounce Rate (≥P75)"),
        ("high_exit", "High Exit Rate (≥P75)"),
        ("very_high_bounce", "Very High Bounce Rate (≥P90)"),
        ("very_high_exit", "Very High Exit Rate (≥P90)"),
        ("high_frustration", "High Frustration Score (≥P80)"),
    ]

    core_results = []
    for col, label in issues:
        result = propensity_score_match(df, col)
        if result:
            result["label"] = label
            core_results.append(result)
            all_results[col] = result

    table = []
    for r in core_results:
        table.append([
            r["label"],
            r["n_treated"],
            r["n_control"],
            f"{r['treated_rate']:.1%}",
            f"{r['control_rate']:.1%}",
            f"{r['delta']:+.1%}",
            f"[{r['ci_lower']:+.1%}, {r['ci_upper']:+.1%}]",
            "✓ YES" if r["is_significant"] else "✗ no",
            f"{r['propensity_balance']:.4f}",
        ])
    headers = ["Issue", "Treated", "Control", "Treated Conv%", "Control Conv%",
               "Delta", "95% CI", "Significant?", "PS Balance"]
    print(tabulate(table, headers=headers, tablefmt="grid"))

    sig_count = sum(1 for r in core_results if r["is_significant"])
    print(f"\n  Result: {sig_count}/{len(core_results)} issues show SIGNIFICANT causal impact")
    if sig_count >= 3:
        print("  ✓ HYPOTHESIS SUPPORTED — technical friction causally reduces conversion")
    else:
        print("  ? Inconclusive — need more data or stronger treatment definition")

    # ══════════════════════════════════════════════
    # TEST B: Placebo Test — Random "Issue" Should Show NO Effect
    # ══════════════════════════════════════════════
    print(f"\n{'═'*70}")
    print("  TEST B: Placebo Test — Random Assignment Should Show No Effect")
    print(f"{'═'*70}")
    print("  (Running 10 placebo trials with random 'issue' assignment...)\n")

    rng = np.random.RandomState(42)
    placebo_significant = 0
    placebo_deltas = []

    for trial in range(10):
        df_placebo = df.copy()
        # Randomly assign ~25% as "affected" (no real relationship to conversion)
        df_placebo["placebo_issue"] = rng.binomial(1, 0.25, len(df_placebo))
        result = propensity_score_match(df_placebo, "placebo_issue")
        if result:
            placebo_deltas.append(result["delta"])
            sig = "SIG ✗" if result["is_significant"] else "n.s. ✓"
            print(f"    Trial {trial+1:2d}: delta = {result['delta']:+.1%}  {sig}")
            if result["is_significant"]:
                placebo_significant += 1

    print(f"\n  Result: {placebo_significant}/10 placebo trials significant (expect ≤1 at 5% level)")
    if placebo_significant <= 1:
        print("  ✓ PLACEBO PASSED — our method doesn't produce false positives")
    else:
        print("  ✗ PLACEBO CONCERN — too many false positives, check methodology")

    # ══════════════════════════════════════════════
    # TEST C: Dose-Response — More Friction = Bigger Drop
    # ══════════════════════════════════════════════
    print(f"\n{'═'*70}")
    print("  TEST C: Dose-Response — More Friction Should Cause Bigger Drop")
    print(f"{'═'*70}")

    # Create dose levels for bounce rate
    df_dose = df.copy()
    bounce_quartiles = pd.qcut(df_dose["BounceRates"], q=5, duplicates="drop", labels=False)
    df_dose["bounce_quintile"] = bounce_quartiles

    print("\n  Bounce Rate Quintiles vs Conversion (raw):")
    dose_data = []
    for q in sorted(df_dose["bounce_quintile"].unique()):
        subset = df_dose[df_dose["bounce_quintile"] == q]
        conv = subset["converted"].mean()
        bounce_range = f"{subset['BounceRates'].min():.3f}-{subset['BounceRates'].max():.3f}"
        dose_data.append([f"Q{q+1}", bounce_range, len(subset), f"{conv:.1%}"])
        print(f"    Q{q+1} (bounce {bounce_range:15s}): n={len(subset):>5}  conv={conv:.1%}")

    # Check monotonicity
    conv_rates = [df_dose[df_dose["bounce_quintile"] == q]["converted"].mean()
                  for q in sorted(df_dose["bounce_quintile"].unique())]
    monotonic_decreasing = all(conv_rates[i] >= conv_rates[i+1]
                                for i in range(len(conv_rates)-1))
    mostly_decreasing = sum(1 for i in range(len(conv_rates)-1)
                            if conv_rates[i] >= conv_rates[i+1]) >= len(conv_rates) - 2

    print(f"\n  Conversion rates by quintile: {[f'{r:.1%}' for r in conv_rates]}")
    if monotonic_decreasing:
        print("  ✓ PERFECTLY MONOTONIC — higher friction = lower conversion at every level")
    elif mostly_decreasing:
        print("  ✓ MOSTLY MONOTONIC — dose-response relationship holds")
    else:
        print("  ? Non-monotonic — friction-conversion relationship is complex")

    # PSM at each dose level vs baseline (Q1)
    print("\n  PSM Dose-Response (each quintile matched against Q1):")
    dose_results = []
    for q in sorted(df_dose["bounce_quintile"].unique()):
        if q == 0:
            continue
        df_q = df_dose[(df_dose["bounce_quintile"] == 0) | (df_dose["bounce_quintile"] == q)].copy()
        df_q["is_dose"] = (df_q["bounce_quintile"] == q).astype(int)
        result = propensity_score_match(df_q, "is_dose")
        if result:
            dose_results.append((q, result))
            sig = "✓" if result["is_significant"] else " "
            print(f"    Q{q+1} vs Q1: delta = {result['delta']:+.1%}  "
                  f"CI [{result['ci_lower']:+.1%}, {result['ci_upper']:+.1%}]  {sig}")

    if dose_results:
        dose_deltas = [r["delta"] for _, r in dose_results]
        if all(d > 0 for d in dose_deltas):
            print("  ✓ DOSE-RESPONSE CONFIRMED — every friction level shows positive delta vs baseline")

    # ══════════════════════════════════════════════
    # TEST D: Stratified Validation — Effect Holds Across Subgroups
    # ══════════════════════════════════════════════
    print(f"\n{'═'*70}")
    print("  TEST D: Stratified Validation — Effect Holds Across Subgroups")
    print(f"{'═'*70}")

    strata = [
        ("visitor_type", "Returning_Visitor", "Returning Visitors"),
        ("visitor_type", "New_Visitor", "New Visitors"),
        ("is_weekend", True, "Weekend Sessions"),
        ("is_weekend", False, "Weekday Sessions"),
    ]

    # Also add top OS groups
    top_os = df["os_version"].value_counts().head(3).index.tolist()
    for os_val in top_os:
        strata.append(("os_version", os_val, f"OS {os_val}"))

    strat_results = []
    for col, val, label in strata:
        subset = df[df[col] == val].copy()
        if len(subset) < 100:
            continue
        result = propensity_score_match(subset, "high_bounce")
        if result:
            result["label"] = label
            strat_results.append(result)

    table = []
    for r in strat_results:
        sig = "✓ YES" if r["is_significant"] else "✗ no"
        table.append([r["label"], r["n_treated"], f"{r['delta']:+.1%}",
                      f"[{r['ci_lower']:+.1%}, {r['ci_upper']:+.1%}]", sig])
    headers = ["Subgroup", "Treated N", "Delta", "95% CI", "Significant?"]
    print(tabulate(table, headers=headers, tablefmt="simple"))

    positive_delta = sum(1 for r in strat_results if r["delta"] > 0)
    print(f"\n  Result: {positive_delta}/{len(strat_results)} subgroups show positive delta (friction hurts conversion)")
    if positive_delta >= len(strat_results) * 0.7:
        print("  ✓ STRATIFIED VALIDATION PASSED — effect is consistent across subgroups")

    # ══════════════════════════════════════════════
    # TEST E: Sensitivity Analysis — Vary Parameters
    # ══════════════════════════════════════════════
    print(f"\n{'═'*70}")
    print("  TEST E: Sensitivity Analysis — Varying k, Features, Caliper")
    print(f"{'═'*70}")

    # Vary k (number of neighbors)
    print("\n  Varying k (neighbors) for high_bounce:")
    for k in [1, 3, 5, 10, 20]:
        result = propensity_score_match(df, "high_bounce", k=k)
        if result:
            sig = "✓" if result["is_significant"] else " "
            print(f"    k={k:2d}: delta = {result['delta']:+.1%}  "
                  f"CI [{result['ci_lower']:+.1%}, {result['ci_upper']:+.1%}]  "
                  f"balance={result['propensity_balance']:.4f}  {sig}")

    # Vary features
    print("\n  Varying feature sets:")
    feature_sets = [
        ("Minimal (OS+Browser)", ["os_version", "browser"]),
        ("Medium (OS+Browser+Region+Traffic)", ["os_version", "browser", "region", "traffic_type"]),
        ("Full (all covariates)", MATCHING_FEATURES),
        ("Full + PageValues", MATCHING_FEATURES + ["PageValues"]),
    ]
    for label, feats in feature_sets:
        result = propensity_score_match(df, "high_bounce", features=feats)
        if result:
            sig = "✓" if result["is_significant"] else " "
            print(f"    {label:40s}: delta = {result['delta']:+.1%}  "
                  f"CI [{result['ci_lower']:+.1%}, {result['ci_upper']:+.1%}]  {sig}")

    # Vary caliper
    print("\n  Varying caliper (max propensity distance):")
    for cal in [0.01, 0.05, 0.10, 0.20, None]:
        result = propensity_score_match(df, "high_bounce", caliper=cal)
        if result:
            sig = "✓" if result["is_significant"] else " "
            cal_str = f"{cal:.2f}" if cal else "None"
            print(f"    caliper={cal_str:5s}: n_treated={result['n_treated']:>5}  "
                  f"delta = {result['delta']:+.1%}  "
                  f"CI [{result['ci_lower']:+.1%}, {result['ci_upper']:+.1%}]  {sig}")

    # ══════════════════════════════════════════════
    # TEST F: A/A Test — Split Control, Expect Zero Effect
    # ══════════════════════════════════════════════
    print(f"\n{'═'*70}")
    print("  TEST F: A/A Test — Split Control Group, Expect Zero Effect")
    print(f"{'═'*70}")

    control_sessions = df[df["high_bounce"] == 0].copy()
    rng = np.random.RandomState(123)
    control_sessions["fake_split"] = rng.binomial(1, 0.5, len(control_sessions))

    aa_results = []
    for trial in range(5):
        control_copy = control_sessions.copy()
        control_copy["fake_split"] = rng.binomial(1, 0.5, len(control_copy))
        result = propensity_score_match(control_copy, "fake_split")
        if result:
            aa_results.append(result)
            sig = "SIG ✗" if result["is_significant"] else "n.s. ✓"
            print(f"    A/A Trial {trial+1}: delta = {result['delta']:+.1%}  "
                  f"CI [{result['ci_lower']:+.1%}, {result['ci_upper']:+.1%}]  {sig}")

    aa_sig = sum(1 for r in aa_results if r["is_significant"])
    print(f"\n  Result: {aa_sig}/{len(aa_results)} A/A trials significant (expect 0)")
    if aa_sig == 0:
        print("  ✓ A/A TEST PASSED — no spurious effects in homogeneous groups")
    elif aa_sig <= 1:
        print("  ~ A/A TEST MARGINAL — within expected false positive rate")
    else:
        print("  ✗ A/A TEST FAILED — method may have bias")

    return all_results


# ═══════════════════════════════════════════════════════════════════
# Frustration Score Analysis
# ═══════════════════════════════════════════════════════════════════

def frustration_analysis(df: pd.DataFrame):
    """Analyze composite frustration score vs conversion."""
    print(f"\n{'═'*70}")
    print("  COMPOSITE FRUSTRATION SCORE ANALYSIS")
    print(f"{'═'*70}")

    scores = df["frustration_score"]
    print(f"\n  Distribution (0=calm, 100=max frustration):")
    print(f"    Min: {scores.min():.0f}  |  P25: {scores.quantile(0.25):.0f}  |  "
          f"Median: {scores.median():.0f}  |  P75: {scores.quantile(0.75):.0f}  |  Max: {scores.max():.0f}")

    buckets = [
        ("Low (0-25)", (scores >= 0) & (scores < 25)),
        ("Medium (25-50)", (scores >= 25) & (scores < 50)),
        ("High (50-75)", (scores >= 50) & (scores < 75)),
        ("Critical (75-100)", (scores >= 75)),
    ]
    print(f"\n  {'Bucket':<20s} {'Sessions':>8s} {'Conv%':>8s} {'Avg Bounce':>12s} {'Avg Exit':>10s}")
    print(f"  {'─'*60}")
    for label, mask in buckets:
        subset = df[mask]
        if len(subset) == 0:
            continue
        conv = subset["converted"].mean()
        bounce = subset["BounceRates"].mean()
        exit_r = subset["ExitRates"].mean()
        pct = len(subset) / len(df) * 100
        bar = "█" * int(pct / 2)
        print(f"  {label:<20s} {len(subset):>6} ({pct:4.1f}%)  {conv:>6.1%}  {bounce:>10.4f}  {exit_r:>10.4f}")

    # Correlation
    corr = df[["frustration_score", "converted"]].corr().iloc[0, 1]
    print(f"\n  Frustration-Conversion correlation: r = {corr:.3f}")

    conv_frust = df[df["converted"] == 1]["frustration_score"].mean()
    nonconv_frust = df[df["converted"] == 0]["frustration_score"].mean()
    print(f"  Avg frustration (converting):     {conv_frust:.1f}")
    print(f"  Avg frustration (non-converting): {nonconv_frust:.1f}")
    print(f"  → Non-converting sessions are {nonconv_frust - conv_frust:.1f} points more frustrated")


# ═══════════════════════════════════════════════════════════════════
# Main
# ═══════════════════════════════════════════════════════════════════

def main():
    print("=" * 70)
    print("  PULSE — Hypothesis Validation")
    print("  Dataset: UCI Online Shoppers Purchasing Intention (12,330 sessions)")
    print("  Method: Propensity Score Matching + 6 Validation Tests")
    print("=" * 70)

    df = load_dataset()
    print(f"\n  Loaded {len(df)} sessions")
    print(f"  Conversion rate: {df['converted'].mean():.1%} ({df['converted'].sum()} purchases)")
    print(f"  OS types: {df['os_version'].nunique()}  |  Browsers: {df['browser'].nunique()}  |  Regions: {df['region'].nunique()}")

    # Frustration analysis
    frustration_analysis(df)

    # Funnel analysis
    funnel_analysis(df)

    # Run validation suite
    results = run_validation_suite(df)

    # ── Final Verdict ──
    print(f"\n{'═'*70}")
    print("  FINAL VALIDATION SUMMARY")
    print(f"{'═'*70}")
    print("""
  Test A (Core Hypothesis):    Does friction reduce conversion?
  Test B (Placebo):            Does our method avoid false positives?
  Test C (Dose-Response):      Does more friction = bigger drop?
  Test D (Stratified):         Is the effect consistent across subgroups?
  Test E (Sensitivity):        Is the result robust to parameter changes?
  Test F (A/A Test):           Does our method find zero effect when there IS none?

  If Tests A, B, C, D, F all pass → STRONG validation that our causal
  analysis approach works and can be trusted in production.
""")
    print("=" * 70)
    print("  Validation complete!")
    print("=" * 70)


if __name__ == "__main__":
    main()

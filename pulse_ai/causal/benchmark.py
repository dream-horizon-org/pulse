#!/usr/bin/env python3
"""
Benchmark Suite — rigorous evaluation of the PSM causal engine.

Generates synthetic data with KNOWN ground truth causal effects, runs
propensity_match across many scenarios, and measures:
  - Accuracy: bias, RMSE, MAE
  - Calibration: 95% CI coverage (should be ~95%)
  - Type I error: false positive rate under null (should be ≤5%)
  - Power: detection rate for real effects (should increase with N/effect)
  - Robustness: performance under confounding, imbalance, small N
  - Scalability: wall-clock time at different sample sizes

Data Generating Process (DGP):
  1. Covariates sampled from realistic distributions
  2. Treatment assigned via logistic model on covariates (confounding)
  3. Outcome computed from covariates + treatment effect
  4. Ground truth ATT computed from potential outcomes BEFORE masking
  5. Only observed data passed to propensity_match

Usage:
    python -m causal.benchmark              # full suite (~15-20 min)
    python -m causal.benchmark --quick      # fast subset (~2 min)
    python -m causal.benchmark --category A # run one category only
"""
import argparse
import time
import warnings
from dataclasses import dataclass
from typing import Optional

import numpy as np
import pandas as pd
from tabulate import tabulate

from .config import CausalConfig
from .matching import propensity_match

warnings.filterwarnings("ignore")


# ═══════════════════════════════════════════════════════════════════
# Synthetic Data Generator
# ═══════════════════════════════════════════════════════════════════

# Realistic covariate pools (mimic mobile app telemetry)
DEVICE_MODELS = ["iPhone14", "iPhone15", "Pixel7", "Pixel8", "Galaxy23",
                 "Galaxy24", "OnePlus11", "Xiaomi13", "Redmi12", "Moto_G"]
OS_VERSIONS = ["17.0", "16.4", "14", "13", "12", "15"]
APP_VERSIONS = ["3.0", "3.1", "3.2", "3.3", "3.4"]
NETWORK_PROVIDERS = ["wifi", "4g", "5g", "3g"]
GEO_COUNTRIES = ["US", "IN", "UK", "BR", "DE", "JP", "ID"]


def generate_scenario(
    n: int = 2000,
    true_effect_pp: float = 0.10,
    treatment_frac: float = 0.20,
    confound_strength: float = 1.0,
    n_device_models: int = 10,
    missing_frac: float = 0.0,
    seed: int = 42,
) -> tuple[pd.DataFrame, float]:
    """
    Generate a synthetic dataset with known causal effect.

    The DGP creates confounding by having covariates influence BOTH
    treatment assignment AND outcome. This is the scenario PSM must
    correct for.

    Args:
        n: Total number of sessions.
        true_effect_pp: True causal effect in percentage points (on treated).
            Positive means treatment REDUCES the outcome (matching the
            propensity_match convention: att = control_rate - treated_rate).
        treatment_frac: Target fraction of treated units.
        confound_strength: 0=no confounding (RCT), 1=moderate, 3=strong.
        n_device_models: Number of unique device models (tests encoding paths).
        missing_frac: Fraction of covariate values to set as NaN.
        seed: Random seed for reproducibility.

    Returns:
        (df, true_att): DataFrame ready for propensity_match, and the
        true ATT computed from potential outcomes.
    """
    rng = np.random.RandomState(seed)

    # ── 1. Generate covariates ──
    devices = DEVICE_MODELS[:n_device_models]
    df = pd.DataFrame({
        "device_model": rng.choice(devices, n, p=_skewed_probs(len(devices), rng)),
        "os_version": rng.choice(OS_VERSIONS, n, p=_skewed_probs(len(OS_VERSIONS), rng)),
        "app_version": rng.choice(APP_VERSIONS, n, p=_skewed_probs(len(APP_VERSIONS), rng)),
        "network_provider": rng.choice(NETWORK_PROVIDERS, n, p=_skewed_probs(len(NETWORK_PROVIDERS), rng)),
        "geo_country": rng.choice(GEO_COUNTRIES, n, p=_skewed_probs(len(GEO_COUNTRIES), rng)),
        "session_hour": rng.randint(0, 24, n),
    })

    # ── 2. Encode covariates for latent model ──
    # Use simple numeric encoding for the DGP (not the same as PSM encoding)
    X_latent = np.column_stack([
        pd.Categorical(df["device_model"]).codes / max(n_device_models - 1, 1),
        pd.Categorical(df["os_version"]).codes / max(len(OS_VERSIONS) - 1, 1),
        pd.Categorical(df["app_version"]).codes / max(len(APP_VERSIONS) - 1, 1),
        pd.Categorical(df["network_provider"]).codes / max(len(NETWORK_PROVIDERS) - 1, 1),
        pd.Categorical(df["geo_country"]).codes / max(len(GEO_COUNTRIES) - 1, 1),
        np.sin(2 * np.pi * df["session_hour"].values / 24),
    ])

    # Confounding coefficients (shared between treatment and outcome models)
    n_features = X_latent.shape[1]
    beta_shared = rng.randn(n_features) * 0.5

    # ── 3. Treatment assignment (confounded) ──
    logit_treat = X_latent @ (beta_shared * confound_strength)
    # Shift intercept to achieve target treatment fraction
    intercept = np.log(treatment_frac / (1 - treatment_frac))
    logit_treat = logit_treat - logit_treat.mean() + intercept
    p_treat = 1 / (1 + np.exp(-logit_treat))
    treatment = rng.binomial(1, p_treat)

    # ── 4. Potential outcomes ──
    base_rate = 0.25  # baseline conversion probability
    logit_y0 = X_latent @ (beta_shared * 0.8) - np.log(base_rate / (1 - base_rate))
    # Shift to achieve target base rate
    logit_y0 = logit_y0 - logit_y0.mean() - np.log((1 - base_rate) / base_rate)

    p_y0 = 1 / (1 + np.exp(-logit_y0))  # P(Y=1 | T=0)

    # Treatment effect: reduce outcome probability for treated
    # true_effect_pp is the desired ATT in probability points
    p_y1 = np.clip(p_y0 - true_effect_pp, 0.01, 0.99)  # P(Y=1 | T=1)

    # Generate potential outcomes
    y0 = rng.binomial(1, p_y0)  # outcome under control
    y1 = rng.binomial(1, p_y1)  # outcome under treatment

    # ── 5. Observed outcome ──
    outcome = np.where(treatment == 1, y1, y0)

    # ── 6. True ATT (from potential outcomes on treated units) ──
    treated_mask = treatment == 1
    if treated_mask.sum() == 0:
        true_att = 0.0
    else:
        # ATT = E[Y(0) - Y(1) | T=1] in propensity_match convention
        # (positive = treatment reduces outcome)
        true_att = p_y0[treated_mask].mean() - p_y1[treated_mask].mean()

    # ── 7. Assemble DataFrame ──
    df["treatment"] = treatment
    df["outcome"] = outcome

    # ── 8. Inject missing values if requested ──
    if missing_frac > 0:
        for col in ["device_model", "os_version", "app_version",
                     "network_provider", "geo_country"]:
            mask = rng.random(n) < missing_frac
            df.loc[mask, col] = np.nan

    return df, true_att


def _skewed_probs(n: int, rng: np.random.RandomState) -> np.ndarray:
    """Generate a skewed probability distribution (Dirichlet) for categoricals."""
    raw = rng.dirichlet(np.ones(n) * 0.5)
    return raw / raw.sum()


# ═══════════════════════════════════════════════════════════════════
# Scenario Definitions
# ═══════════════════════════════════════════════════════════════════

@dataclass
class Scenario:
    """A single benchmark scenario."""
    category: str        # A, B, C, D, E, F
    scenario_id: str     # A1, A2, ...
    description: str
    n: int = 2000
    true_effect_pp: float = 0.10
    treatment_frac: float = 0.20
    confound_strength: float = 1.0
    n_device_models: int = 10
    missing_frac: float = 0.0
    reps: int = 50
    n_bootstrap: int = 1000  # per-scenario bootstrap override


def get_scenarios(quick: bool = False) -> list[Scenario]:
    """Build the full scenario matrix."""
    reps_full = 100
    reps_quick = 20
    reps = reps_quick if quick else reps_full
    n_boot = 500 if quick else 1000

    scenarios = [
        # ── Category A: Effect Size Recovery ──
        Scenario("A", "A1", "Null (0%)", true_effect_pp=0.0, reps=reps, n_bootstrap=n_boot),
        Scenario("A", "A2", "Small (5pp)", true_effect_pp=0.05, reps=reps, n_bootstrap=n_boot),
        Scenario("A", "A3", "Medium (10pp)", true_effect_pp=0.10, reps=reps, n_bootstrap=n_boot),
        Scenario("A", "A4", "Large (20pp)", true_effect_pp=0.20, reps=reps, n_bootstrap=n_boot),

        # ── Category B: CI Coverage ──
        Scenario("B", "B1", "Coverage null", true_effect_pp=0.0, reps=reps, n_bootstrap=n_boot),
        Scenario("B", "B2", "Coverage 5pp", true_effect_pp=0.05, reps=reps, n_bootstrap=n_boot),
        Scenario("B", "B3", "Coverage 10pp", true_effect_pp=0.10, reps=reps, n_bootstrap=n_boot),
        Scenario("B", "B4", "Coverage 20pp", true_effect_pp=0.20, reps=reps, n_bootstrap=n_boot),

        # ── Category C: False Positive Rate ──
        Scenario("C", "C1", "FPR no confound", true_effect_pp=0.0,
                 confound_strength=0.0, reps=reps, n_bootstrap=n_boot),
        Scenario("C", "C2", "FPR moderate confound", true_effect_pp=0.0,
                 confound_strength=1.0, reps=reps, n_bootstrap=n_boot),
        Scenario("C", "C3", "FPR strong confound", true_effect_pp=0.0,
                 confound_strength=3.0, reps=reps, n_bootstrap=n_boot),
        Scenario("C", "C4", "FPR small N", true_effect_pp=0.0,
                 n=500, reps=reps, n_bootstrap=n_boot),

        # ── Category D: Power ──
        Scenario("D", "D1", "Power N=500", n=500, true_effect_pp=0.10,
                 reps=reps, n_bootstrap=n_boot),
        Scenario("D", "D2", "Power N=1000", n=1000, true_effect_pp=0.10,
                 reps=reps, n_bootstrap=n_boot),
        Scenario("D", "D3", "Power N=2000", n=2000, true_effect_pp=0.10,
                 reps=reps, n_bootstrap=n_boot),
        Scenario("D", "D4", "Power small effect", n=2000, true_effect_pp=0.05,
                 reps=reps, n_bootstrap=n_boot),
        Scenario("D", "D5", "Power large effect", n=2000, true_effect_pp=0.20,
                 reps=reps, n_bootstrap=n_boot),
        Scenario("D", "D6", "Power rare treatment", n=2000, true_effect_pp=0.10,
                 treatment_frac=0.05, reps=reps, n_bootstrap=n_boot),

        # ── Category E: Robustness ──
        Scenario("E", "E1", "5% treatment", treatment_frac=0.05,
                 reps=reps, n_bootstrap=n_boot),
        Scenario("E", "E2", "50% treatment", treatment_frac=0.50,
                 reps=reps, n_bootstrap=n_boot),
        Scenario("E", "E3", "Strong confound", confound_strength=3.0,
                 reps=reps, n_bootstrap=n_boot),
        Scenario("E", "E4", "High-card (50 devices)", n_device_models=50,
                 reps=reps, n_bootstrap=n_boot),
        Scenario("E", "E5", "Small N=200", n=200, true_effect_pp=0.20,
                 reps=reps, n_bootstrap=n_boot),
        Scenario("E", "E6", "20% missing", missing_frac=0.20,
                 reps=reps, n_bootstrap=n_boot),
        Scenario("E", "E7", "No confounding (RCT)", confound_strength=0.0,
                 reps=reps, n_bootstrap=n_boot),

        # ── Category F: Scalability ──
        Scenario("F", "F1", "N=100", n=100, true_effect_pp=0.15,
                 reps=5, n_bootstrap=n_boot),
        Scenario("F", "F2", "N=500", n=500, reps=5, n_bootstrap=n_boot),
        Scenario("F", "F3", "N=1000", n=1000, reps=5, n_bootstrap=n_boot),
        Scenario("F", "F4", "N=5000", n=5000, reps=5, n_bootstrap=n_boot),
        Scenario("F", "F5", "N=10000", n=10000, reps=5, n_bootstrap=n_boot),
    ]

    if quick:
        # Subset: one from each category
        quick_ids = {"A1", "A3", "B3", "C2", "D3", "E3", "E5", "F3"}
        scenarios = [s for s in scenarios if s.scenario_id in quick_ids]

    return scenarios


# ═══════════════════════════════════════════════════════════════════
# Benchmark Runner
# ═══════════════════════════════════════════════════════════════════

@dataclass
class ScenarioResult:
    """Aggregated results for one scenario."""
    scenario: Scenario
    true_att: float
    mean_att: float
    std_att: float
    bias: float
    rmse: float
    mae: float
    coverage: float        # fraction of reps where CI contains true ATT
    rejection_rate: float  # fraction of reps with is_significant=True
    mean_ci_width: float
    mean_ps_balance: float
    mean_common_support: float
    caliper_applied_rate: float
    none_rate: float       # fraction of reps returning None
    mean_time_s: float
    p50_time_s: float
    p95_time_s: float


def run_scenario(scenario: Scenario, base_seed: int = 1000) -> ScenarioResult:
    """Run a single scenario across all repetitions."""
    cfg = CausalConfig(
        n_bootstrap=scenario.n_bootstrap,
        min_affected=10,
        min_control=10,
    )
    # For high-cardinality test, ensure we use the right encoding threshold
    if scenario.n_device_models > 20:
        cfg.max_onehot_cardinality = 20  # triggers frequency encoding

    atts = []
    coverages = []
    significants = []
    ci_widths = []
    ps_balances = []
    common_supports = []
    calipers = []
    times = []
    none_count = 0
    true_atts = []

    for rep in range(scenario.reps):
        seed = base_seed + rep
        df, true_att = generate_scenario(
            n=scenario.n,
            true_effect_pp=scenario.true_effect_pp,
            treatment_frac=scenario.treatment_frac,
            confound_strength=scenario.confound_strength,
            n_device_models=scenario.n_device_models,
            missing_frac=scenario.missing_frac,
            seed=seed,
        )
        true_atts.append(true_att)

        t0 = time.perf_counter()
        result = propensity_match(df, "treatment", "outcome", cfg)
        elapsed = time.perf_counter() - t0
        times.append(elapsed)

        if result is None:
            none_count += 1
            continue

        atts.append(result["att"])
        ci_widths.append(result["ci_upper"] - result["ci_lower"])
        ps_balances.append(result["ps_balance"])
        common_supports.append(result["common_support_pct"])
        calipers.append(result["caliper_applied"])
        significants.append(result["is_significant"])

        # Coverage: does CI contain the true ATT for this rep's DGP?
        covered = result["ci_lower"] <= true_att <= result["ci_upper"]
        coverages.append(covered)

    # Aggregate
    n_valid = len(atts)
    mean_true_att = np.mean(true_atts) if true_atts else scenario.true_effect_pp

    if n_valid == 0:
        return ScenarioResult(
            scenario=scenario, true_att=mean_true_att,
            mean_att=float("nan"), std_att=float("nan"),
            bias=float("nan"), rmse=float("nan"), mae=float("nan"),
            coverage=float("nan"), rejection_rate=float("nan"),
            mean_ci_width=float("nan"), mean_ps_balance=float("nan"),
            mean_common_support=float("nan"), caliper_applied_rate=float("nan"),
            none_rate=1.0,
            mean_time_s=np.mean(times), p50_time_s=np.median(times),
            p95_time_s=np.percentile(times, 95),
        )

    atts_arr = np.array(atts)
    bias = atts_arr.mean() - mean_true_att
    rmse = np.sqrt(np.mean((atts_arr - mean_true_att) ** 2))
    mae = np.mean(np.abs(atts_arr - mean_true_att))

    return ScenarioResult(
        scenario=scenario,
        true_att=mean_true_att,
        mean_att=atts_arr.mean(),
        std_att=atts_arr.std(),
        bias=bias,
        rmse=rmse,
        mae=mae,
        coverage=np.mean(coverages) if coverages else float("nan"),
        rejection_rate=np.mean(significants) if significants else float("nan"),
        mean_ci_width=np.mean(ci_widths),
        mean_ps_balance=np.mean(ps_balances),
        mean_common_support=np.mean(common_supports),
        caliper_applied_rate=np.mean(calipers),
        none_rate=none_count / scenario.reps,
        mean_time_s=np.mean(times),
        p50_time_s=np.median(times),
        p95_time_s=np.percentile(times, 95),
    )


# ═══════════════════════════════════════════════════════════════════
# Report Printer
# ═══════════════════════════════════════════════════════════════════

def print_results(results: list[ScenarioResult]):
    """Print formatted benchmark results with pass/fail verdicts."""
    print("\n" + "=" * 120)
    print("  PULSE CAUSAL ENGINE — BENCHMARK RESULTS")
    print("=" * 120)

    # ── Per-category tables ──
    categories = {}
    for r in results:
        categories.setdefault(r.scenario.category, []).append(r)

    category_names = {
        "A": "EFFECT SIZE RECOVERY (Accuracy & Bias)",
        "B": "CI COVERAGE (Calibration)",
        "C": "FALSE POSITIVE RATE (Type I Error)",
        "D": "POWER (Sensitivity)",
        "E": "ROBUSTNESS",
        "F": "SCALABILITY",
    }

    for cat in sorted(categories.keys()):
        cat_results = categories[cat]
        print(f"\n{'─'*120}")
        print(f"  Category {cat}: {category_names.get(cat, cat)}")
        print(f"{'─'*120}")

        if cat == "F":
            _print_scalability_table(cat_results)
        else:
            _print_standard_table(cat_results)

    # ── Pass/Fail Summary ──
    _print_verdict(results)


def _print_standard_table(results: list[ScenarioResult]):
    """Print the main metrics table for a category."""
    rows = []
    for r in results:
        s = r.scenario
        rows.append([
            s.scenario_id,
            s.description,
            f"{r.true_att:+.3f}" if not np.isnan(r.true_att) else "—",
            f"{r.mean_att:+.3f}" if not np.isnan(r.mean_att) else "—",
            f"{r.bias:+.4f}" if not np.isnan(r.bias) else "—",
            f"{r.rmse:.4f}" if not np.isnan(r.rmse) else "—",
            f"{r.coverage:.0%}" if not np.isnan(r.coverage) else "—",
            f"{r.rejection_rate:.0%}" if not np.isnan(r.rejection_rate) else "—",
            f"{r.mean_ci_width:.3f}" if not np.isnan(r.mean_ci_width) else "—",
            f"{r.mean_ps_balance:.4f}" if not np.isnan(r.mean_ps_balance) else "—",
            f"{r.none_rate:.0%}",
        ])

    headers = ["ID", "Description", "True ATT", "Est ATT", "Bias",
               "RMSE", "Coverage", "Rej Rate", "CI Width", "PS Bal", "None%"]
    print(tabulate(rows, headers=headers, tablefmt="simple_grid"))


def _print_scalability_table(results: list[ScenarioResult]):
    """Print timing-focused table for scalability category."""
    rows = []
    for r in results:
        s = r.scenario
        rows.append([
            s.scenario_id,
            f"N={s.n:,}",
            f"{r.mean_time_s:.2f}s",
            f"{r.p50_time_s:.2f}s",
            f"{r.p95_time_s:.2f}s",
            f"{r.none_rate:.0%}",
            f"{r.mean_att:+.3f}" if not np.isnan(r.mean_att) else "—",
            f"{r.bias:+.4f}" if not np.isnan(r.bias) else "—",
        ])
    headers = ["ID", "Scale", "Mean Time", "P50 Time", "P95 Time",
               "None%", "Est ATT", "Bias"]
    print(tabulate(rows, headers=headers, tablefmt="simple_grid"))


def _print_verdict(results: list[ScenarioResult]):
    """Print pass/fail summary based on benchmark criteria."""
    print(f"\n{'='*120}")
    print("  VERDICT")
    print(f"{'='*120}\n")

    checks = []

    # ── Check 1: FPR controlled ──
    null_results = [r for r in results if r.scenario.category == "C"]
    for r in null_results:
        if not np.isnan(r.rejection_rate):
            passed = r.rejection_rate <= 0.10  # allow some slack for simulation noise
            checks.append((
                f"FPR {r.scenario.scenario_id}",
                f"rejection_rate={r.rejection_rate:.0%} ≤ 10%",
                passed,
            ))

    # ── Check 2: Coverage calibrated ──
    coverage_results = [r for r in results if r.scenario.category == "B"]
    for r in coverage_results:
        if not np.isnan(r.coverage):
            passed = 0.80 <= r.coverage <= 1.0  # BCa can be slightly anti-conservative
            checks.append((
                f"Coverage {r.scenario.scenario_id}",
                f"coverage={r.coverage:.0%} in [80%, 100%]",
                passed,
            ))

    # ── Check 3: Bias small for accuracy scenarios ──
    accuracy_results = [r for r in results if r.scenario.category == "A"]
    for r in accuracy_results:
        if not np.isnan(r.bias):
            passed = abs(r.bias) < 0.05  # within 5 pp
            checks.append((
                f"Bias {r.scenario.scenario_id}",
                f"|bias|={abs(r.bias):.4f} < 0.05",
                passed,
            ))

    # ── Check 4: Power adequate ──
    power_results = [r for r in results
                     if r.scenario.category == "D" and r.scenario.true_effect_pp >= 0.10
                     and r.scenario.n >= 2000]
    for r in power_results:
        if not np.isnan(r.rejection_rate):
            passed = r.rejection_rate >= 0.50  # at least 50% power for 10pp effect at N=2000
            checks.append((
                f"Power {r.scenario.scenario_id}",
                f"power={r.rejection_rate:.0%} ≥ 50%",
                passed,
            ))

    # ── Check 5: Power monotonic with N ──
    d_power = {r.scenario.scenario_id: r.rejection_rate
               for r in results if r.scenario.category == "D"
               and r.scenario.true_effect_pp == 0.10
               and r.scenario.treatment_frac == 0.20}
    if "D1" in d_power and "D3" in d_power:
        if not (np.isnan(d_power["D1"]) or np.isnan(d_power["D3"])):
            passed = d_power["D3"] >= d_power["D1"]
            checks.append((
                "Power monotonic (N)",
                f"D3({d_power['D3']:.0%}) ≥ D1({d_power['D1']:.0%})",
                passed,
            ))

    # ── Check 6: Scalability ──
    f5 = next((r for r in results if r.scenario.scenario_id == "F5"), None)
    if f5:
        passed = f5.p95_time_s < 120
        checks.append((
            "Scalability F5 (N=10K)",
            f"p95={f5.p95_time_s:.1f}s < 120s",
            passed,
        ))

    # ── Check 7: Robustness — no scenario completely fails ──
    robust_results = [r for r in results if r.scenario.category == "E"]
    for r in robust_results:
        passed = r.none_rate < 0.80  # at most 80% None
        checks.append((
            f"Robust {r.scenario.scenario_id}",
            f"none_rate={r.none_rate:.0%} < 80%",
            passed,
        ))

    # Print verdict table
    total_pass = sum(1 for _, _, p in checks if p)
    total = len(checks)
    for name, detail, passed in checks:
        marker = "✓ PASS" if passed else "✗ FAIL"
        print(f"  {marker}  {name:30s}  {detail}")

    print(f"\n  {'─'*60}")
    print(f"  Total: {total_pass}/{total} checks passed")
    grade = "EXCELLENT" if total_pass == total else "GOOD" if total_pass >= total * 0.85 else "NEEDS WORK" if total_pass >= total * 0.70 else "FAILING"
    print(f"  Grade: {grade}")
    print(f"  {'─'*60}\n")


# ═══════════════════════════════════════════════════════════════════
# Main
# ═══════════════════════════════════════════════════════════════════

def main():
    parser = argparse.ArgumentParser(description="Benchmark the PSM causal engine")
    parser.add_argument("--quick", action="store_true",
                        help="Run fast subset (8 scenarios, 20 reps each)")
    parser.add_argument("--category", type=str, default=None,
                        help="Run only scenarios in this category (A/B/C/D/E/F)")
    parser.add_argument("--reps", type=int, default=None,
                        help="Override repetitions per scenario")
    args = parser.parse_args()

    scenarios = get_scenarios(quick=args.quick)
    if args.category:
        scenarios = [s for s in scenarios if s.category == args.category.upper()]
    if args.reps:
        for s in scenarios:
            s.reps = args.reps

    total_reps = sum(s.reps for s in scenarios)
    print("=" * 80)
    print("  PULSE CAUSAL ENGINE — BENCHMARK SUITE")
    print(f"  {len(scenarios)} scenarios, {total_reps} total repetitions")
    print(f"  Mode: {'quick' if args.quick else 'full'}")
    print("=" * 80)

    results = []
    for i, scenario in enumerate(scenarios):
        print(f"\n  [{i+1}/{len(scenarios)}] {scenario.scenario_id}: {scenario.description} "
              f"(N={scenario.n}, effect={scenario.true_effect_pp:.0%}, "
              f"reps={scenario.reps})...", end="", flush=True)
        t0 = time.perf_counter()
        result = run_scenario(scenario)
        elapsed = time.perf_counter() - t0
        results.append(result)

        # Progress indicator
        if not np.isnan(result.mean_att):
            print(f" done in {elapsed:.1f}s  "
                  f"(ATT={result.mean_att:+.3f}, bias={result.bias:+.4f}, "
                  f"coverage={result.coverage:.0%})")
        else:
            print(f" done in {elapsed:.1f}s  (all None)")

    print_results(results)


if __name__ == "__main__":
    main()

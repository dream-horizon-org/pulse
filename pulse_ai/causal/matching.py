"""
Matching Engine — PSM with proper encoding, caliper, common support.

Fixes from v1:
  - One-hot encoding for nominal categoricals (not LabelEncoder)
  - Sine/cosine for cyclical features (session_hour)
  - Caliper with progressive relaxation (not silent drop)
  - Common support trimming (positivity check)
  - Paired ATT estimation (not .unique() on controls)
  - BCa bootstrap confidence intervals
"""
import numpy as np
import pandas as pd
from scipy.stats import norm
from sklearn.linear_model import LogisticRegression
from sklearn.neighbors import NearestNeighbors

from .config import CausalConfig


def encode_features(df: pd.DataFrame, cfg: CausalConfig) -> np.ndarray:
    """
    Encode matching features for propensity score model.

    - Nominal categoricals (≤ max_onehot_cardinality): one-hot encoding
    - High-cardinality categoricals: frequency encoding
    - Cyclical features (session_hour): sine/cosine transform
    """
    encoded = pd.DataFrame(index=df.index)

    # Cyclical features first
    for feat, period in cfg.cyclical_features.items():
        if feat in df.columns:
            vals = df[feat].astype(float)
            encoded[f"{feat}_sin"] = np.sin(2 * np.pi * vals / period)
            encoded[f"{feat}_cos"] = np.cos(2 * np.pi * vals / period)

    # Matching features
    for feat in cfg.matching_features:
        if feat not in df.columns:
            continue
        if feat in cfg.cyclical_features:
            continue  # already handled

        col = df[feat].fillna("unknown").astype(str)
        n_unique = col.nunique()

        if n_unique <= 1:
            continue
        elif n_unique <= cfg.max_onehot_cardinality:
            # One-hot encode (proper for nominal categoricals)
            dummies = pd.get_dummies(col, prefix=feat, drop_first=True)
            encoded = pd.concat([encoded, dummies], axis=1)
        else:
            # High cardinality → frequency encoding
            freq = col.value_counts(normalize=True)
            encoded[f"{feat}_freq"] = col.map(freq).values

    if encoded.empty:
        return np.zeros((len(df), 1))
    return encoded.values.astype(float)


def check_common_support(
    ps_treated: np.ndarray,
    ps_control: np.ndarray,
    trim_pct: float = 0.05,
) -> tuple:
    """
    Verify positivity: trim units outside the common support region.

    Returns:
        treated_mask, control_mask, (lower_bound, upper_bound)
    """
    lower = max(
        np.percentile(ps_treated, trim_pct * 100),
        np.percentile(ps_control, trim_pct * 100),
    )
    upper = min(
        np.percentile(ps_treated, (1 - trim_pct) * 100),
        np.percentile(ps_control, (1 - trim_pct) * 100),
    )
    treated_mask = (ps_treated >= lower) & (ps_treated <= upper)
    control_mask = (ps_control >= lower) & (ps_control <= upper)
    return treated_mask, control_mask, (lower, upper)


def bootstrap_ci_paired(
    treated_y: np.ndarray,
    matched_control_y: np.ndarray,
    n_boot: int = 2000,
    alpha: float = 0.05,
    seed: int = 42,
) -> tuple:
    """
    BCa (bias-corrected accelerated) bootstrap CI for paired ATT.

    Args:
        treated_y: outcome for each treated unit
        matched_control_y: mean outcome of matched controls for each treated unit
                           (same length as treated_y — paired structure preserved)
    Returns:
        (ci_lower, ci_upper, is_significant, p_value)
    """
    rng = np.random.RandomState(seed)
    n = len(treated_y)
    pair_diffs = matched_control_y - treated_y
    observed_att = pair_diffs.mean()

    # Bootstrap distribution — VECTORIZED (no Python loop)
    # Generate all bootstrap indices at once: shape (n_boot, n)
    boot_indices = rng.randint(0, n, size=(n_boot, n))
    boot_deltas = pair_diffs[boot_indices].mean(axis=1)

    # P-value: proportion of bootstraps on wrong side of zero
    if observed_att > 0:
        p_value = np.mean(boot_deltas <= 0)
    elif observed_att < 0:
        p_value = np.mean(boot_deltas >= 0)
    else:
        p_value = 1.0
    p_value = max(p_value, 1 / n_boot)  # can't be exactly 0

    # BCa adjustment
    try:
        # Bias correction: z0
        z0 = norm.ppf(np.mean(boot_deltas < observed_att))
        if not np.isfinite(z0):
            z0 = 0.0

        # Acceleration: jackknife — VECTORIZED (no Python loop)
        # Leave-one-out means: total_sum minus each element, divided by (n-1)
        total_sum = pair_diffs.sum()
        jack_stats = (total_sum - pair_diffs) / (n - 1)
        jack_mean = jack_stats.mean()
        num = np.sum((jack_mean - jack_stats) ** 3)
        denom = 6 * (np.sum((jack_mean - jack_stats) ** 2) ** 1.5)
        a_hat = num / denom if denom != 0 else 0.0

        # Adjusted percentiles
        z_lo = norm.ppf(alpha / 2)
        z_hi = norm.ppf(1 - alpha / 2)

        def bca_percentile(z_alpha):
            numer = z0 + z_alpha
            denom_val = 1 - a_hat * numer
            if abs(denom_val) < 1e-10:
                return norm.cdf(z_alpha)
            return norm.cdf(z0 + numer / denom_val)

        p_lo = bca_percentile(z_lo)
        p_hi = bca_percentile(z_hi)

        # Clamp to valid range
        p_lo = np.clip(p_lo, 0.001, 0.999)
        p_hi = np.clip(p_hi, 0.001, 0.999)

        ci_lower = np.percentile(boot_deltas, 100 * p_lo)
        ci_upper = np.percentile(boot_deltas, 100 * p_hi)

    except Exception:
        # Fallback to simple percentile if BCa fails
        ci_lower = np.percentile(boot_deltas, 100 * alpha / 2)
        ci_upper = np.percentile(boot_deltas, 100 * (1 - alpha / 2))

    is_significant = (ci_lower > 0 and ci_upper > 0) or (ci_lower < 0 and ci_upper < 0)
    return ci_lower, ci_upper, is_significant, p_value


def propensity_match(
    df: pd.DataFrame,
    treatment_col: str,
    outcome_col: str,
    cfg: CausalConfig,
) -> dict | None:
    """
    Core PSM engine with all fixes applied.

    Returns dict with:
        att, ci_lower, ci_upper, is_significant, p_value,
        n_treated, n_control, treated_rate, control_rate,
        ps_balance, caliper_applied, common_support_pct
    """
    # Reset index so numpy arrays and DataFrame indices are aligned.
    # The caller may pass a filtered DataFrame with non-contiguous indices
    # (e.g., [0, 5, 12, 71]), but ps is a 0-indexed numpy array of len(df).
    # Without reset, ps[df.index[mask]] would use original indices as offsets.
    df = df.reset_index(drop=True)

    treated_mask = df[treatment_col] == 1
    control_mask = df[treatment_col] == 0

    if treated_mask.sum() < cfg.min_affected or control_mask.sum() < cfg.min_control:
        return None

    # ── 1. Encode features ──
    X = encode_features(df, cfg)
    y = df[treatment_col].values

    if X.shape[1] == 0:
        return None

    # ── 2. Fit propensity model ──
    try:
        model = LogisticRegression(max_iter=1000, random_state=42, solver="lbfgs")
        model.fit(X, y)
        ps = model.predict_proba(X)[:, 1]
    except Exception:
        return None

    ps = np.clip(ps, 0.01, 0.99)  # positivity enforcement

    # ── 3. Common support check ──
    ps_treated = ps[treated_mask]
    ps_control = ps[control_mask]
    t_support, c_support, bounds = check_common_support(
        ps_treated, ps_control, cfg.common_support_trim,
    )

    treated_idx = df.index[treated_mask][t_support]
    control_idx = df.index[control_mask][c_support]
    common_support_pct = t_support.mean()

    if len(treated_idx) < cfg.min_affected or len(control_idx) < cfg.min_control:
        return None

    # ── 4. KNN matching with caliper ──
    k = min(cfg.k_neighbors, len(control_idx))
    nn = NearestNeighbors(n_neighbors=k, metric="euclidean")
    nn.fit(ps[control_idx].reshape(-1, 1))
    distances, indices = nn.kneighbors(ps[treated_idx].reshape(-1, 1))

    # Progressive caliper relaxation
    ps_std = np.std(ps)
    caliper = cfg.caliper_sd * ps_std if ps_std > 0 else 1.0
    caliper_applied = True

    mask = distances[:, 0] <= caliper
    if mask.sum() < cfg.min_affected:
        for mult in cfg.caliper_relax_multipliers:
            relaxed = distances[:, 0] <= caliper * mult
            if relaxed.sum() >= cfg.min_affected:
                mask = relaxed
                caliper *= mult
                break
        else:
            mask = np.ones(len(treated_idx), dtype=bool)
            caliper_applied = False

    treated_idx_f = treated_idx[mask]
    indices_f = indices[mask]

    if len(treated_idx_f) < cfg.min_affected:
        return None

    # ── 5. Paired ATT estimation (preserve matched-pair structure) ──
    treated_outcomes = df.loc[treated_idx_f, outcome_col].values
    control_outcomes_per_treated = np.empty(len(treated_idx_f))

    for i in range(len(treated_idx_f)):
        matched_controls = control_idx[indices_f[i]]
        control_outcomes_per_treated[i] = df.loc[matched_controls, outcome_col].mean()

    treated_rate = treated_outcomes.mean()
    control_rate = control_outcomes_per_treated.mean()
    att = control_rate - treated_rate

    # ── 6. BCa bootstrap CI (paired) ──
    ci_lower, ci_upper, is_sig, p_value = bootstrap_ci_paired(
        treated_outcomes, control_outcomes_per_treated,
        n_boot=cfg.n_bootstrap, alpha=cfg.alpha,
    )

    # ── 7. Balance check ──
    ps_balance = abs(
        ps[treated_idx_f].mean() -
        np.mean([ps[control_idx[indices_f[i]]].mean() for i in range(len(treated_idx_f))])
    )

    return {
        "att": att,
        "ci_lower": ci_lower,
        "ci_upper": ci_upper,
        "is_significant": is_sig,
        "p_value": p_value,
        "n_treated": len(treated_idx_f),
        "n_control": len(set(control_idx[indices_f.flatten()])),
        "treated_rate": treated_rate,
        "control_rate": control_rate,
        "ps_balance": ps_balance,
        "caliper_applied": caliper_applied,
        "common_support_pct": common_support_pct,
    }

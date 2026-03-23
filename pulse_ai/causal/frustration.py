"""
Frustration Scoring — composite session quality metric, calibratable from data.

The frustration score is a 0-100 composite metric that summarizes overall
session quality. It is used for:
  - Reporting and prioritization (which sessions are worst?)
  - Correlation analysis (do frustrated users convert less?)

IMPORTANT: The frustration score is NEVER used as a matching feature in PSM.
It encodes post-treatment information (crashes, jank, errors) and would
introduce post-treatment variable bias if included in propensity models.

Scoring approach:
  1. Each signal (has_crash, jank_count, etc.) is percentile-ranked within
     the population. This makes the score robust to different scales.
  2. Weights determine relative importance of each signal.
  3. Weights can be:
     (a) Default heuristics (reasonable starting point)
     (b) Calibrated from data using logistic regression on non-conversion

Calibration uses the insight that frustration signals that predict
non-conversion are the ones that matter most for revenue impact.
"""
import numpy as np
import pandas as pd
from scipy.stats import rankdata
from sklearn.linear_model import LogisticRegression


# ─── Default Weights ──────────────────────────────────────────────

DEFAULT_FRUSTRATION_WEIGHTS = {
    "has_crash": 30,
    "has_anr": 25,
    "jank_frozen_count": 15,
    "net_error_count": 12,
    "net_timeout_count": 10,
    "jank_slow_count": 8,
    "network_change_count": 5,
    "short_session": 10,
}


# ─── Weight Calibration ──────────────────────────────────────────

def calibrate_frustration_weights(
    sessions_df: pd.DataFrame,
    converted_col: str = "converted",
    min_sessions: int = 200,
) -> dict:
    """
    Learn frustration signal weights from data using logistic regression.

    Fits a model predicting NON-conversion from frustration signals.
    The absolute coefficient magnitudes become the calibrated weights,
    scaled to roughly match the default weight total (~130).

    Args:
        sessions_df: Must contain signal columns and converted_col.
        converted_col: Column name for binary conversion indicator.
        min_sessions: Minimum sessions required for calibration.

    Returns:
        Calibrated weight dict, or DEFAULT_FRUSTRATION_WEIGHTS if
        calibration fails or data is insufficient.
    """
    signal_cols = [c for c in DEFAULT_FRUSTRATION_WEIGHTS.keys() if c in sessions_df.columns]

    if len(signal_cols) < 3 or len(sessions_df) < min_sessions:
        return DEFAULT_FRUSTRATION_WEIGHTS

    if converted_col not in sessions_df.columns:
        return DEFAULT_FRUSTRATION_WEIGHTS

    X = sessions_df[signal_cols].fillna(0).values
    y = (1 - sessions_df[converted_col].values).astype(int)  # predict NON-conversion

    # Need both classes
    if len(np.unique(y)) < 2:
        return DEFAULT_FRUSTRATION_WEIGHTS

    try:
        model = LogisticRegression(max_iter=1000, random_state=42, solver="lbfgs")
        model.fit(X, y)
        raw = np.abs(model.coef_[0])
        if raw.sum() == 0:
            return DEFAULT_FRUSTRATION_WEIGHTS
        # Scale to ~same total as defaults
        default_total = sum(DEFAULT_FRUSTRATION_WEIGHTS.values())
        normalized = (raw / raw.sum() * default_total).round(1)
        return dict(zip(signal_cols, normalized))
    except Exception:
        return DEFAULT_FRUSTRATION_WEIGHTS


# ─── Score Computation ────────────────────────────────────────────

def compute_frustration_scores(
    sessions_df: pd.DataFrame,
    issue_events_df: pd.DataFrame,
    weights: dict = None,
) -> pd.DataFrame:
    """
    Compute composite frustration score (0-100) for each session.

    Each signal is percentile-ranked, then weighted. The final score
    is normalized to 0-100.

    Args:
        sessions_df: Session profiles. Must have session_id.
            Optional columns: jank_slow_count, jank_frozen_count,
            net_error_count, net_timeout_count, network_change_count,
            session_duration_sec.
        issue_events_df: Individual issue events (for crash/ANR flags).
        weights: Signal weights. Defaults to DEFAULT_FRUSTRATION_WEIGHTS.

    Returns:
        DataFrame with columns: session_id, frustration_score
    """
    if weights is None:
        weights = DEFAULT_FRUSTRATION_WEIGHTS

    df = sessions_df[["session_id"]].copy()

    # ── Binary flags from issue events ──
    crash_sessions = set()
    anr_sessions = set()
    if not issue_events_df.empty:
        crash_sessions = set(
            issue_events_df[issue_events_df["pulse_type"] == "device.crash"]["session_id"]
        )
        anr_sessions = set(
            issue_events_df[issue_events_df["pulse_type"] == "device.anr"]["session_id"]
        )
    df["has_crash"] = df["session_id"].isin(crash_sessions).astype(float)
    df["has_anr"] = df["session_id"].isin(anr_sessions).astype(float)

    # ── Numeric signals from session profiles ──
    for col in ["jank_slow_count", "jank_frozen_count", "net_error_count",
                "net_timeout_count", "network_change_count"]:
        if col in sessions_df.columns:
            df[col] = sessions_df[col].fillna(0).astype(float).values
        else:
            df[col] = 0.0

    # ── Short session flag ──
    if "session_duration_sec" in sessions_df.columns:
        threshold = sessions_df["session_duration_sec"].quantile(0.15)
        df["short_session"] = (
            sessions_df["session_duration_sec"].values <= threshold
        ).astype(float)
    else:
        df["short_session"] = 0.0

    # ── Percentile-rank weighted scoring ──
    max_possible = sum(weights.values())
    raw_scores = np.zeros(len(df))

    for signal, weight in weights.items():
        if signal not in df.columns:
            continue
        vals = df[signal].values.astype(float)
        if vals.max() == vals.min():
            # All same value: 1.0 if positive (everyone has it), 0.0 if zero
            pct = np.ones(len(vals)) if vals.max() > 0 else np.zeros(len(vals))
        else:
            pct = (rankdata(vals, method="average") - 1) / (len(vals) - 1)
        raw_scores += pct * weight

    df["frustration_score"] = (raw_scores / max_possible * 100).round(1)
    return df[["session_id", "frustration_score"]]

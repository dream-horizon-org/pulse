"""
Configuration — all tunables in one place, zero magic numbers.
"""
from dataclasses import dataclass, field


@dataclass
class CausalConfig:
    """All parameters for the causal analysis pipeline."""

    # ── PSM matching ──
    k_neighbors: int = 5                # matched controls per treated unit
    caliper_sd: float = 0.2             # max propensity distance (in SD units)
    caliper_relax_multipliers: tuple = (2.0, 4.0, 8.0)  # progressive relaxation

    # ── Statistical thresholds ──
    n_bootstrap: int = 2000             # bootstrap iterations (≥2000 for BCa)
    alpha: float = 0.05                 # significance level
    min_affected: int = 10              # minimum treated sessions for analysis
    min_control: int = 10               # minimum control sessions for analysis
    common_support_trim: float = 0.05   # trim % for positivity check

    # ── Issue detection ──
    net_error_threshold: int = 3        # sessions with ≥N errors flagged
    jank_percentile_threshold: float = 0.90  # top N% jank sessions flagged

    # ── Funnel classification ──
    funnel_early_cutoff: float = 0.10   # screens where <10% sessions convert
    funnel_late_cutoff: float = 0.30    # screens where >30% sessions convert

    # ── Process mining ──
    max_graph_depth: int = 10           # BFS depth limit for conversion paths
    min_edge_weight: int = 10           # minimum transitions for drop-off analysis

    # ── Feature encoding ──
    max_onehot_cardinality: int = 20    # above this → frequency encoding

    # ── FDR correction ──
    apply_fdr: bool = True              # Benjamini-Hochberg correction

    # ── Matching features (device context ONLY — no post-treatment vars) ──
    matching_features: list = field(default_factory=lambda: [
        "device_model", "os_version", "app_version",
        "network_provider", "geo_country",
    ])

    # ── Cyclical features (sine/cosine encoded) ──
    cyclical_features: dict = field(default_factory=lambda: {
        "session_hour": 24,  # period = 24 hours
    })

"""
Data models — typed containers for analysis results.
"""
from dataclasses import dataclass, field
from typing import Optional


@dataclass
class IssueAnalysis:
    """Result of a single journey-conditioned causal analysis."""

    # ── Issue identification ──
    issue_type: str                      # device.crash, device.anr, app.jank.slow, etc.
    issue_label: str                     # human-readable: "device.crash on PaymentListing"
    screen_name: str                     # screen where the issue occurred

    # ── Journey context ──
    funnel_stage: str                    # early / mid / late / unknown
    sessions_reaching_screen: int        # total sessions that visited this screen

    # ── Causal estimate ──
    affected_count: int                  # treated sessions (had issue on this screen)
    control_count: int                   # matched control sessions
    affected_conversion_rate: float      # conversion rate among treated (after issue)
    control_conversion_rate: float       # conversion rate among controls (after screen)
    conversion_delta: float              # control - affected (positive = issue hurts)
    ci_lower: float                      # 95% CI lower bound
    ci_upper: float                      # 95% CI upper bound
    is_significant: bool                 # CI excludes zero (after FDR if enabled)
    bootstrap_p_value: float = 0.0       # proportion of bootstrap deltas crossing zero

    # ── Match quality ──
    propensity_balance: float = 0.0      # mean PS difference (lower = better)
    caliper_applied: bool = True         # was caliper enforced?
    common_support_pct: float = 1.0      # % of treated in common support region

    # ── Prioritization ──
    priority_score: float = 0.0          # |delta| × affected × significance

    # ── Actionability (optional, filled when data available) ──
    affected_user_count: int = 0
    exception_detail: str = ""           # top exception type / stack trace first line


@dataclass
class ConversionProxy:
    """Auto-discovered conversion signal (GraphQL operation, URL, or fallback)."""
    proxy_type: str           # graphql_conversion, graphql_engagement, url_conversion, session_depth
    identifier: str           # e.g., "GET validateEntitlement"
    sessions_reached: int
    total_sessions: int
    conversion_rate: float


@dataclass
class DropoffEdge:
    """A point in the screen graph where users leave the conversion path."""
    from_screen: str
    on_path_count: int
    off_path_count: int
    total: int
    dropoff_rate: float
    top_destinations: list = field(default_factory=list)  # [(screen, count), ...]

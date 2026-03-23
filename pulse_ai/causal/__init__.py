"""
Pulse Causal Revenue Impact Engine
===================================
Detects revenue leakage from technical issues (crashes, ANRs, jank)
using journey-conditioned propensity score matching with temporal ordering.

Modules:
    config      — All configurable parameters (no magic numbers)
    models      — Data models (IssueAnalysis, ConversionProxy, DropoffEdge)
    data        — ClickHouse data extraction (temporal-aware)
    matching    — PSM engine (one-hot encoding, caliper, common support, BCa CI)
    analysis    — Journey-conditioned causal analysis + FDR correction
    frustration — Composite frustration scoring (calibratable from data)
    mining      — Screen-graph process mining
    report      — Reporting and output formatting

Usage:
    from causal.config import CausalConfig
    from causal.data import get_ch_client, get_session_profiles, ...
    from causal.analysis import analyze_all_issues
    from causal.report import print_report
"""
from .config import CausalConfig
from .models import IssueAnalysis, ConversionProxy, DropoffEdge

__all__ = [
    "CausalConfig",
    "IssueAnalysis",
    "ConversionProxy",
    "DropoffEdge",
]

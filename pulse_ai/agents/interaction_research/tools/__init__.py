"""Bounded PulseClient tools for Interaction Research (Agent 1)."""

from .breakdown_interaction_by_dimension import breakdown_interaction_by_dimension
from .fetch_interaction_config import fetch_interaction_config
from .fetch_interaction_latency_percentiles import fetch_interaction_latency_percentiles
from .fetch_interaction_metric_trends import fetch_interaction_metric_trends
from .fetch_interaction_metrics import fetch_interaction_metrics
from .fetch_interaction_root_cause_segments import fetch_interaction_root_cause_segments
from .fetch_problematic_interaction_spans import fetch_problematic_interaction_spans
from .fetch_session_trace_snapshot import fetch_session_trace_snapshot
from .get_funnel import get_funnel
from .get_journey import get_journey
from .list_funnels import list_funnels
from .list_journeys import list_journeys
from .search_event_catalog import search_event_catalog

INTERACTION_RESEARCH_TOOL_NAMES: tuple[str, ...] = (
    "fetch_interaction_config",
    "fetch_interaction_metrics",
    "fetch_interaction_root_cause_segments",
    "list_journeys",
    "get_journey",
    "list_funnels",
    "get_funnel",
    "search_event_catalog",
    "fetch_problematic_interaction_spans",
    "fetch_session_trace_snapshot",
    "fetch_interaction_metric_trends",
    "fetch_interaction_latency_percentiles",
    "breakdown_interaction_by_dimension",
)

__all__ = [
    "INTERACTION_RESEARCH_TOOL_NAMES",
    "fetch_interaction_config",
    "fetch_interaction_metrics",
    "fetch_interaction_root_cause_segments",
    "list_journeys",
    "get_journey",
    "list_funnels",
    "get_funnel",
    "search_event_catalog",
    "fetch_problematic_interaction_spans",
    "fetch_session_trace_snapshot",
    "fetch_interaction_metric_trends",
    "fetch_interaction_latency_percentiles",
    "breakdown_interaction_by_dimension",
]

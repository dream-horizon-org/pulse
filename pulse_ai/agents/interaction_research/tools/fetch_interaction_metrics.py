"""Mandatory tool: Apdex, error rate, categorization, latency for one interaction."""

from google.adk.tools import ToolContext

from pulse_ai.agents.em.tools.analytics.query_interaction_metrics import (
    query_interaction_metrics,
)


async def fetch_interaction_metrics(
    interaction_name: str,
    time_range: str = "last_7d",
    start_time: str | None = None,
    end_time: str | None = None,
    filters: str | None = None,
    tool_context: ToolContext = None,
) -> dict:
    """Load dashboard-aligned metrics for report blocks 2–3 (composite bundle).

    Args:
        interaction_name: Pulse interaction span name.
        time_range: One of last_24h, last_7d, last_30d, custom (same as EM agent).
        start_time: ISO 8601 start when time_range=custom.
        end_time: ISO 8601 end when time_range=custom.
        filters: Optional JSON dimension filters (platform, app_version, etc.).
    """
    return await query_interaction_metrics(
        metric_type="composite",
        interaction_name=interaction_name,
        time_range=time_range,
        start_time=start_time,
        end_time=end_time,
        filters=filters,
        tool_context=tool_context,
    )

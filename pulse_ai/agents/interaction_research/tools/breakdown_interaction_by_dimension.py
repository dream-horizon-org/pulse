"""Slice interaction performance by platform, network, OS, device, etc."""

from google.adk.tools import ToolContext

from pulse_ai.agents.em.tools.analytics.breakdown_interaction import breakdown_interaction


async def breakdown_interaction_by_dimension(
    interaction_name: str,
    dimension: str,
    time_range: str = "last_7d",
    start_time: str | None = None,
    end_time: str | None = None,
    filters: str | None = None,
    tool_context: ToolContext = None,
) -> dict:
    """Break down interaction metrics by one dimension (Block 3/5/6 evidence).

    Prefer dimension=network when carrier reliability matters; platform or os when
    RCA segments are flat. May call twice (e.g. network then platform) — captures merge.

    Args:
        interaction_name: Pulse interaction span name.
        dimension: device, region, release, platform, os, network,
            latency_by_network, latency_by_device, or latency_by_os.
        time_range: Same presets as fetch_interaction_metrics.
        start_time: ISO start when time_range=custom.
        end_time: ISO end when time_range=custom.
        filters: Optional JSON dimension filters to narrow within another slice.
    """
    result = await breakdown_interaction(
        dimension=dimension,
        interaction_name=interaction_name,
        time_range=time_range,
        start_time=start_time,
        end_time=end_time,
        filters=filters,
        tool_context=tool_context,
    )
    if result.get("status") != "success":
        return result
    return {
        **result,
        "dimension": (dimension or "").strip().lower(),
        "count": len(result.get("data") or []),
    }

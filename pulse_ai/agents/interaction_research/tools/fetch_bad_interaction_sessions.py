"""Best-effort tool: poor/error sessions for block 8 proof."""

from google.adk.tools import ToolContext

from pulse_ai.agents.em.tools.analytics.query_interaction_sessions import (
    query_interaction_sessions,
)


async def fetch_bad_interaction_sessions(
    interaction_name: str,
    time_range: str = "last_7d",
    start_time: str | None = None,
    end_time: str | None = None,
    event_type: str = "error",
    limit: int = 10,
    filters: str | None = None,
    tool_context: ToolContext = None,
) -> dict:
    """Sample sessions with errors or poor experience for proof & follow-up (block 8).

    Args:
        interaction_name: Pulse interaction span name.
        time_range: Same presets as fetch_interaction_metrics.
        start_time: ISO start when time_range=custom.
        end_time: ISO end when time_range=custom.
        event_type: crash, error, completed, or omit for all.
        limit: Max session rows (default 10).
        filters: Optional JSON dimension filters.
    """
    return await query_interaction_sessions(
        scope="sessions",
        interaction_name=interaction_name,
        time_range=time_range,
        start_time=start_time,
        end_time=end_time,
        event_type=event_type,
        filters=filters,
        limit=limit,
        tool_context=tool_context,
    )

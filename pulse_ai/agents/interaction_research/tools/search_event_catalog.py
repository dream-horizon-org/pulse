"""Best-effort tool: event catalog search to match marker events to journey/funnel steps."""

from google.adk.tools import ToolContext

from pulse_ai.agents.interaction_research.tools._http import pulse_get


async def search_event_catalog(
    search: str = "",
    category: str | None = None,
    limit: int = 20,
    offset: int = 0,
    tool_context: ToolContext = None,
) -> dict:
    """Search event definitions to align interaction marker events with journey/funnel steps.

    Args:
        search: Event name substring (empty returns first page).
        category: Optional category filter.
        limit: Max definitions (default 20).
        offset: Pagination offset.
    """
    params: dict = {"limit": limit, "offset": offset}
    if search:
        params["search"] = search.strip()
    if category:
        params["category"] = category.strip()
    return await pulse_get("/v1/event-definitions", params=params, tool_context=tool_context)

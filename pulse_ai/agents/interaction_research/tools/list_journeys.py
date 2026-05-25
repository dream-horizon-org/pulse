"""Best-effort tool: list user journeys for behavior context."""

from google.adk.tools import ToolContext

from pulse_ai.agents.interaction_research.tools._http import pulse_get


async def list_journeys(
    search: str | None = None,
    status: str | None = None,
    page: int = 1,
    page_size: int = 10,
    tool_context: ToolContext = None,
) -> dict:
    """List journeys in the project. Omit enrichment when no confident match to the interaction.

    Args:
        search: Optional name search.
        status: Optional status filter (e.g. ACTIVE).
        page: Page number (1-based).
        page_size: Results per page.
    """
    params: dict = {"page": page, "pageSize": page_size}
    if search:
        params["search"] = search.strip()
    if status:
        params["status"] = status.strip()
    return await pulse_get("/v1/journeys", params=params, tool_context=tool_context)

"""Best-effort tool: funnel detail for funnel_link."""

from google.adk.tools import ToolContext

from pulse_ai.agents.interaction_research.tools._http import pulse_get


async def get_funnel(
    funnel_id: int,
    tool_context: ToolContext = None,
) -> dict:
    """Load funnel steps. Cross-check step events via search_event_catalog before claiming funnel_link.

    Args:
        funnel_id: Numeric funnel id from list_funnels.
    """
    if funnel_id is None or funnel_id < 1:
        return {"status": "error", "message": "funnel_id must be a positive integer"}
    return await pulse_get(f"/v1/funnels/{funnel_id}", tool_context=tool_context)

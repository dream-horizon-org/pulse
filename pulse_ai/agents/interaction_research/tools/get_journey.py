"""Best-effort tool: journey detail for behavior block."""

from google.adk.tools import ToolContext

from pulse_ai.agents.interaction_research.tools._http import pulse_get


async def get_journey(
    journey_id: int,
    tool_context: ToolContext = None,
) -> dict:
    """Load one journey definition (steps, events). Use with event catalog to match marker events.

    Args:
        journey_id: Numeric journey id from list_journeys.
    """
    if journey_id is None or journey_id < 1:
        return {"status": "error", "message": "journey_id must be a positive integer"}
    return await pulse_get(f"/v1/journeys/{journey_id}", tool_context=tool_context)

"""Tool 5: query_interaction_health — Health summary for top interactions."""

import json

from google.adk.tools import ToolContext

from pulse_ai.client.pulse_client import PulseClient
from pulse_ai.agents.em.templates.base import TIME_RANGE_DOC, compute_time_range

HEALTH_PATH = "/v1/interactions/health"


async def query_interaction_health(
    top_n: int = 10,
    interaction_names: list[str] = None,
    time_range: str = "last_24h",
    start_time: str = None,
    end_time: str = None,
    filters: str = None,
    tool_context: ToolContext = None,
) -> dict:
    """Get health summary for top interactions — Apdex, errors, P50 latency, user categories.

    Args:
        top_n: Number of top interactions to show (default 10)
        interaction_names: Optional list of specific interaction names to query
        time_range: One of: """ + TIME_RANGE_DOC + """
        start_time: ISO 8601 start (only when time_range="custom")
        end_time: ISO 8601 end (only when time_range="custom")
        filters: Optional dimension filters as JSON string, e.g. '{"platform": "Android"}'
    """
    parsed_filters = None
    if filters:
        try:
            parsed_filters = json.loads(filters)
        except (json.JSONDecodeError, TypeError):
            return {"status": "error", "message": f"Invalid JSON in filters: {filters}"}

    try:
        start, end = compute_time_range(time_range, start_time, end_time)
    except ValueError as e:
        return {"status": "error", "message": str(e)}

    body: dict = {
        "topN": top_n,
        "timeRange": {"start": start, "end": end},
    }
    if interaction_names:
        body["interactionNames"] = interaction_names
    if parsed_filters:
        body["filters"] = parsed_filters

    bearer_token = tool_context.state.get("bearer_token") if tool_context else None
    project_id = tool_context.state.get("project_id") if tool_context else None
    client = PulseClient(authorization_header=bearer_token, project_id=project_id)
    response = await client.request("POST", HEALTH_PATH, json=body)

    if isinstance(response, dict):
        return response

    if response.status_code >= 400:
        return PulseClient.parse_error(response)

    return {"status": "success", "data": response.json().get("data", {})}

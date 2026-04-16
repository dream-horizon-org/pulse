"""Tool 7: query_interaction_sessions — Session-level data for an interaction."""

import json

from google.adk.tools import ToolContext

from pulse_ai.client.pulse_client import PulseClient
from pulse_ai.agents.em.templates.base import TIME_RANGE_DOC, compute_time_range

SESSIONS_PATH = "/v1/interactions/sessions"

VALID_SCOPES = frozenset(["sessions", "stats"])
VALID_EVENT_TYPES = frozenset(
    ["crash", "anr", "error", "non_fatal", "frozen_frame", "network_error"]
)


async def query_interaction_sessions(
    scope: str,
    interaction_name: str,
    time_range: str = "last_24h",
    start_time: str = None,
    end_time: str = None,
    event_type: str = None,
    filters: str = None,
    limit: int = 10,
    tool_context: ToolContext = None,
) -> dict:
    """Get session-level data for an interaction.

    Args:
        scope: What to query. "sessions" for session list, "stats" for summary statistics
        interaction_name: The interaction name
        time_range: One of: """ + TIME_RANGE_DOC + """
        start_time: ISO 8601 start (only when time_range="custom")
        end_time: ISO 8601 end (only when time_range="custom")
        event_type: Filter sessions by event type. One of: crash, anr, error, non_fatal,
            frozen_frame, network_error. Omit to return all sessions.
        filters: Optional dimension filters as JSON string, e.g. '{"platform": "Android"}'
        limit: Max sessions to return (scope="sessions", default 10)
    """
    if scope not in VALID_SCOPES:
        return {
            "status": "error",
            "message": f"Invalid scope '{scope}'. Valid values: {', '.join(sorted(VALID_SCOPES))}",
        }

    if event_type is not None and event_type not in VALID_EVENT_TYPES:
        return {
            "status": "error",
            "message": f"Invalid event_type '{event_type}'. Valid values: {', '.join(sorted(VALID_EVENT_TYPES))}",
        }

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
        "scope": scope,
        "interactionName": interaction_name,
        "timeRange": {"start": start, "end": end},
        "limit": limit,
    }
    if event_type is not None:
        body["eventType"] = event_type
    if parsed_filters:
        body["filters"] = parsed_filters

    bearer_token = tool_context.state.get("bearer_token") if tool_context else None
    project_id = tool_context.state.get("project_id") if tool_context else None
    client = PulseClient(authorization_header=bearer_token, project_id=project_id)
    response = await client.request("POST", SESSIONS_PATH, json=body)

    if isinstance(response, dict):
        return response

    if response.status_code >= 400:
        return PulseClient.parse_error(response)

    return {"status": "success", "data": response.json().get("data", {})}

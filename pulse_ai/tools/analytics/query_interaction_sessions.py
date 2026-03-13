"""Tool 7: query_interaction_sessions — Session-level data for an interaction.

Uses build_sessions_query template → PulseClient POST → transform response.
"""

import json

from pulse_ai.client.pulse_client import PulseClient
from pulse_ai.templates.interaction_templates import build_sessions_query
from pulse_ai.transformers.response_transformer import (
    parse_error_response,
    transform_columnar,
)

DATA_QUERY_PATH = "/v1/interactions/performance-metric/distribution"


async def query_interaction_sessions(
    scope: str,
    interaction_name: str,
    time_range: str = "last_24h",
    start_time: str = None,
    end_time: str = None,
    event_type: str = None,
    filters: str = None,
    limit: int = 10,
) -> dict:
    """Get session-level data for an interaction.

    Args:
        scope: What to query. "sessions" for session list, "stats" for summary statistics
        interaction_name: The interaction name
        time_range: One of: last_5m, last_15m, last_30m, last_1h, last_3h, last_6h, last_12h, last_24h, last_2d, last_7d, last_30d, last_90d, yesterday, previous_week, previous_month, today_so_far, this_week, this_month_so_far, custom
        start_time: ISO 8601 start (only when time_range="custom")
        end_time: ISO 8601 end (only when time_range="custom")
        event_type: Filter sessions by type: crash, error, completed, or omit for all
        filters: Optional dimension filters as JSON string, e.g. '{"platform": "Android"}'
        limit: Max sessions to return (scope="sessions", default 10)
    """
    # Parse filters JSON string → dict
    parsed_filters = None
    if filters:
        try:
            parsed_filters = json.loads(filters)
        except (json.JSONDecodeError, TypeError):
            return {"status": "error", "message": f"Invalid JSON in filters: {filters}"}

    try:
        query_request = build_sessions_query(
            scope=scope,
            interaction_name=interaction_name,
            time_range=time_range,
            start_time=start_time,
            end_time=end_time,
            event_type=event_type,
            user_filters=parsed_filters,
            limit=limit,
        )
    except ValueError as e:
        return {"status": "error", "message": str(e)}

    client = PulseClient()
    response = await client.request("POST", DATA_QUERY_PATH, json=query_request)

    # Handle network errors
    if isinstance(response, dict):
        return response

    # Handle HTTP errors
    if response.status_code >= 400:
        return parse_error_response(response)

    # Transform columnar response
    body = response.json()
    data = body.get("data", {})
    return {"status": "success", "data": transform_columnar(data)}

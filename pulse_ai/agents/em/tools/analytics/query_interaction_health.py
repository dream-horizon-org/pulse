"""Tool 5: query_interaction_health — Health summary for top interactions.

Uses build_health_query template → PulseClient POST → transform response.
"""

import json

from google.adk.tools import ToolContext

from ....client.pulse_client import PulseClient
from ....tool_session_auth import pulse_tool_session_auth_error
from ...templates.base import TIME_RANGE_DOC
from ...templates.interaction_templates import build_health_query
from ...transformers.response_transformer import (
    parse_error_response,
    transform_columnar,
)

DATA_QUERY_PATH = "/v1/interactions/performance-metric/distribution"


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
        filters: Optional dimension filters as a JSON string. Valid keys:
            "platform"    → e.g. '{"platform": "Android"}' or '{"platform": "iOS"}'
            "app_version" → e.g. '{"app_version": "5.29.1"}' or '{"app_version": ["5.29.0", "5.29.1"]}'
            "device"      → e.g. '{"device": "Samsung Galaxy S21"}'
            "os_version"  → e.g. '{"os_version": "14.0"}'
            "network"     → e.g. '{"network": "WiFi"}' or '{"network": "4G"}'
            "region"      → currently state names e.g. '"region": "Maharashtra"' or '"region": "Karnataka"';
                            in future may also accept country names e.g. '"region": "India"' or '"region": "Canada"'
            Multiple:       '{"platform": "Android", "app_version": "5.29.1"}'
            Values can be a single string or a list of strings for multi-value filtering.
    """
    # Parse filters JSON string → dict
    parsed_filters = None
    if filters:
        try:
            parsed_filters = json.loads(filters)
        except (json.JSONDecodeError, TypeError):
            return {"status": "error", "message": f"Invalid JSON in filters: {filters}"}

    try:
        query_request = build_health_query(
            top_n=top_n,
            interaction_names=interaction_names,
            time_range=time_range,
            start_time=start_time,
            end_time=end_time,
            user_filters=parsed_filters,
        )
    except ValueError as e:
        return {"status": "error", "message": str(e)}

    session_error = pulse_tool_session_auth_error(tool_context)
    if session_error is not None:
        return session_error

    bearer_token = tool_context.state.get("bearer_token")
    project_id = tool_context.state.get("project_id")
    async with PulseClient(
        authorization_header=bearer_token,
        project_id=project_id,
    ) as client:
        response = await client.request("POST", DATA_QUERY_PATH, json=query_request)

        # Handle network errors (PulseClient returns dict on connection/timeout)
        if isinstance(response, dict):
            return response

        # Handle HTTP errors
        if response.status_code >= 400:
            return parse_error_response(response)

        # Transform columnar response
        body = response.json()
        data = body.get("data", {})
        return {"status": "success", "data": transform_columnar(data)}

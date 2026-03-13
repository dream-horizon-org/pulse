"""Tool 5: query_interaction_health — Health summary for top interactions.

Uses build_health_query template → PulseClient POST → transform response.
"""

import json

from pulse_ai.client.pulse_client import PulseClient
from pulse_ai.templates.interaction_templates import build_health_query
from pulse_ai.transformers.response_transformer import (
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
) -> dict:
    """Get health summary for top interactions — Apdex, errors, P50 latency, user categories.

    Args:
        top_n: Number of top interactions to show (default 10)
        interaction_names: Optional list of specific interaction names to query
        time_range: One of: last_5m, last_15m, last_30m, last_1h, last_3h, last_6h, last_12h, last_24h, last_2d, last_7d, last_30d, last_90d, yesterday, previous_week, previous_month, today_so_far, this_week, this_month_so_far, custom
        start_time: ISO 8601 start (only when time_range="custom")
        end_time: ISO 8601 end (only when time_range="custom")
        filters: Optional dimension filters as JSON string, e.g. '{"platform": "Android"}'
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

    client = PulseClient()
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

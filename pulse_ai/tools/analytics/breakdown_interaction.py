"""Tool 8: breakdown_interaction — Performance by a dimension.

Uses build_breakdown_query template → PulseClient POST → transform response.
"""

import json

from pulse_ai.client.pulse_client import PulseClient
from pulse_ai.templates.interaction_templates import build_breakdown_query
from pulse_ai.transformers.response_transformer import (
    parse_error_response,
    transform_columnar,
)

DATA_QUERY_PATH = "/v1/interactions/performance-metric/distribution"


async def breakdown_interaction(
    dimension: str,
    interaction_name: str,
    time_range: str = "last_24h",
    start_time: str = None,
    end_time: str = None,
    filters: str = None,
) -> dict:
    """Break down interaction performance by a dimension.

    The dimension parameter controls how data is grouped. For example,
    dimension="platform" returns one row per platform (Android, iOS)
    in a single call — do NOT make separate calls with platform filters.
    Use filters only to narrow results WITHIN a different dimension
    (e.g. dimension="device" + filters='{"platform":"Android"}' shows
    only Android devices).

    Args:
        dimension: Breakdown dimension. One of: device, region, release, platform, os, network, latency_by_network, latency_by_device, latency_by_os
        interaction_name: The interaction name
        time_range: One of: last_5m, last_15m, last_30m, last_1h, last_3h, last_6h, last_12h, last_24h, last_2d, last_7d, last_30d, last_90d, yesterday, previous_week, previous_month, today_so_far, this_week, this_month_so_far, custom
        start_time: ISO 8601 start (only when time_range="custom")
        end_time: ISO 8601 end (only when time_range="custom")
        filters: Optional dimension filters as JSON string to narrow within another dimension, e.g. '{"platform": "Android"}' when dimension is "device"
    """
    # Parse filters JSON string → dict
    parsed_filters = None
    if filters:
        try:
            parsed_filters = json.loads(filters)
        except (json.JSONDecodeError, TypeError):
            return {"status": "error", "message": f"Invalid JSON in filters: {filters}"}

    try:
        query_request = build_breakdown_query(
            dimension=dimension,
            interaction_name=interaction_name,
            time_range=time_range,
            start_time=start_time,
            end_time=end_time,
            user_filters=parsed_filters,
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

"""Tool 6: query_interaction_metrics — Specific metric for one interaction.

Uses build_metrics_query template → PulseClient POST → transform response.
"""

import json

from pulse_ai.client.pulse_client import PulseClient
from pulse_ai.templates.interaction_templates import build_metrics_query
from pulse_ai.transformers.response_transformer import (
    parse_error_response,
    transform_columnar,
)

DATA_QUERY_PATH = "/v1/interactions/performance-metric/distribution"


async def query_interaction_metrics(
    metric_type: str,
    interaction_name: str,
    time_range: str = "last_24h",
    start_time: str = None,
    end_time: str = None,
    timeseries: bool = False,
    filters: str = None,
) -> dict:
    """Get a specific performance metric for one interaction.

    Use metric_type="composite" when the user asks for "all metrics",
    "key metrics", "full picture", or an overview of a SINGLE interaction.
    Composite returns: Apdex, success/error counts, P50, P95, crash, ANR,
    frozen frames, network status codes, and user categories — all in one call.

    Args:
        metric_type: Which metric. One of: apdex, latency, error_rate, user_categories, composite.
            - apdex: Single Apdex score
            - latency: P50 and P95 latency
            - error_rate: Success and error counts
            - user_categories: Excellent/Good/Average/Poor user counts
            - composite: ALL of the above plus crash, ANR, frozen frames, network codes (most comprehensive)
        interaction_name: The interaction name (e.g., "ContestJoinSuccess")
        time_range: One of: last_5m, last_15m, last_30m, last_1h, last_3h, last_6h, last_12h, last_24h, last_2d, last_7d, last_30d, last_90d, yesterday, previous_week, previous_month, today_so_far, this_week, this_month_so_far, custom
        start_time: ISO 8601 start (only when time_range="custom")
        end_time: ISO 8601 end (only when time_range="custom")
        timeseries: If true, return time-bucketed trend data instead of aggregates
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
        query_request = build_metrics_query(
            metric_type=metric_type,
            interaction_name=interaction_name,
            time_range=time_range,
            start_time=start_time,
            end_time=end_time,
            timeseries=timeseries,
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

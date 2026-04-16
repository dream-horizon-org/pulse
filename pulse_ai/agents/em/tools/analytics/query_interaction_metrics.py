"""Tool 6: query_interaction_metrics — Specific metric for one interaction."""

import json

from google.adk.tools import ToolContext

from pulse_ai.client.pulse_client import PulseClient
from pulse_ai.agents.em.templates.base import TIME_RANGE_DOC, compute_time_range

METRICS_PATH = "/v1/interactions/metrics"

VALID_METRIC_TYPES = frozenset(
    ["apdex", "latency", "error_rate", "user_categories", "composite", "frames", "network"]
)


async def query_interaction_metrics(
    metric_type: str,
    interaction_name: str,
    time_range: str = "last_24h",
    start_time: str = None,
    end_time: str = None,
    timeseries: bool = False,
    filters: str = None,
    tool_context: ToolContext = None,
) -> dict:
    """Get a specific performance metric for one interaction.

    Use metric_type="composite" when the user asks for "all metrics",
    "key metrics", "full picture", or an overview of a SINGLE interaction.
    Composite returns: Apdex, success/error counts, P50, P95, crash, ANR,
    frozen frames, network status codes, and user categories — all in one call.

    Args:
        metric_type: Which metric. One of: apdex, latency, error_rate, user_categories,
            composite, frames, network.
            - apdex: Single Apdex score
            - latency: P50 and P95 latency
            - error_rate: Success and error counts
            - user_categories: Excellent/Good/Average/Poor user counts
            - frames: Frozen frame metrics
            - network: Network status code breakdown
            - composite: ALL of the above (most comprehensive)
        interaction_name: The interaction name (e.g., "ContestJoinSuccess")
        time_range: One of: """ + TIME_RANGE_DOC + """
        start_time: ISO 8601 start (only when time_range="custom")
        end_time: ISO 8601 end (only when time_range="custom")
        timeseries: If true, return time-bucketed trend data instead of aggregates
        filters: Optional dimension filters as JSON string, e.g. '{"platform": "Android"}'
    """
    if metric_type not in VALID_METRIC_TYPES:
        return {
            "status": "error",
            "message": f"Invalid metric_type '{metric_type}'. Valid values: {', '.join(sorted(VALID_METRIC_TYPES))}",
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
        "interactionName": interaction_name,
        "metricType": metric_type,
        "timeseries": timeseries,
        "timeRange": {"start": start, "end": end},
    }
    if parsed_filters:
        body["filters"] = parsed_filters

    bearer_token = tool_context.state.get("bearer_token") if tool_context else None
    project_id = tool_context.state.get("project_id") if tool_context else None
    client = PulseClient(authorization_header=bearer_token, project_id=project_id)
    response = await client.request("POST", METRICS_PATH, json=body)

    if isinstance(response, dict):
        return response

    if response.status_code >= 400:
        return PulseClient.parse_error(response)

    return {"status": "success", "data": response.json().get("data", {})}

"""Tool 8: breakdown_interaction — Performance by a dimension."""

import json

from google.adk.tools import ToolContext

from pulse_ai.client.pulse_client import PulseClient
from pulse_ai.agents.em.templates.base import TIME_RANGE_DOC, compute_time_range

BREAKDOWN_PATH = "/v1/interactions/breakdown"

VALID_DIMENSIONS = frozenset(
    [
        "device",
        "region",
        "release",
        "platform",
        "os",
        "network",
        "latency_by_network",
        "latency_by_device",
        "latency_by_os",
    ]
)


async def breakdown_interaction(
    dimension: str,
    interaction_name: str,
    time_range: str = "last_24h",
    start_time: str = None,
    end_time: str = None,
    limit: int = 10,
    filters: str = None,
    tool_context: ToolContext = None,
) -> dict:
    """Break down interaction performance by a dimension.

    The dimension parameter controls how data is grouped. For example,
    dimension="platform" returns one row per platform (Android, iOS)
    in a single call — do NOT make separate calls with platform filters.
    Use filters only to narrow results WITHIN a different dimension
    (e.g. dimension="device" + filters='{"platform":"Android"}' shows
    only Android devices).

    Args:
        dimension: Breakdown dimension. One of: device, region, release, platform, os,
            network, latency_by_network, latency_by_device, latency_by_os
        interaction_name: The interaction name
        time_range: One of: """ + TIME_RANGE_DOC + """
        start_time: ISO 8601 start (only when time_range="custom")
        end_time: ISO 8601 end (only when time_range="custom")
        limit: Max rows to return (default 10)
        filters: Optional dimension filters as JSON string to narrow within another
            dimension, e.g. '{"platform": "Android"}' when dimension is "device"
    """
    if dimension not in VALID_DIMENSIONS:
        return {
            "status": "error",
            "message": f"Invalid dimension '{dimension}'. Valid values: {', '.join(sorted(VALID_DIMENSIONS))}",
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
        "dimension": dimension,
        "interactionName": interaction_name,
        "timeRange": {"start": start, "end": end},
        "limit": limit,
    }
    if parsed_filters:
        body["filters"] = parsed_filters

    bearer_token = tool_context.state.get("bearer_token") if tool_context else None
    project_id = tool_context.state.get("project_id") if tool_context else None
    client = PulseClient(authorization_header=bearer_token, project_id=project_id)
    response = await client.request("POST", BREAKDOWN_PATH, json=body)

    if isinstance(response, dict):
        return response

    if response.status_code >= 400:
        return PulseClient.parse_error(response)

    return {"status": "success", "data": response.json().get("data", {})}

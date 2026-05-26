"""Aggregate P50 / P95 / P99 latency for Block 5 diagnosis."""

from __future__ import annotations

import json

from google.adk.tools import ToolContext

from pulse_ai.agents.em.templates.base import TIME_RANGE_DOC
from pulse_ai.agents.em.templates.interaction_templates import (
    build_interaction_latency_percentiles_query,
)
from pulse_ai.agents.em.transformers.response_transformer import (
    parse_error_response,
    transform_columnar,
)
from pulse_ai.agents.em.tools.analytics.query_interaction_metrics import DATA_QUERY_PATH
from pulse_ai.client.pulse_client import PulseClient
from pulse_ai.tool_session_auth import pulse_tool_session_auth_error


async def fetch_interaction_latency_percentiles(
    interaction_name: str,
    time_range: str = "last_7d",
    start_time: str | None = None,
    end_time: str | None = None,
    filters: str | None = None,
    tool_context: ToolContext = None,
) -> dict:
    """Load P50, P95, and P99 duration for the reporting window (Block 5 latency lens).

    Args:
        interaction_name: Pulse interaction span name.
        time_range: One of: """ + TIME_RANGE_DOC + """
        start_time: ISO start when time_range=custom.
        end_time: ISO end when time_range=custom.
        filters: Optional JSON dimension filters (platform, app_version, etc.).
    """
    parsed_filters = None
    if filters:
        try:
            parsed_filters = json.loads(filters)
        except (json.JSONDecodeError, TypeError):
            return {"status": "error", "message": f"Invalid JSON in filters: {filters}"}

    try:
        query_request = build_interaction_latency_percentiles_query(
            interaction_name=interaction_name,
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

        if isinstance(response, dict):
            return response

        if response.status_code >= 400:
            return parse_error_response(response)

        body = response.json()
        data = body.get("data", {})
        rows = transform_columnar(data)

        return {
            "status": "success",
            "data": rows[0] if rows else {},
        }

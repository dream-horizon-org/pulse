"""Sample problematic interaction spans (error, poor, crash, …) for blocks 4, 6, 8."""

from __future__ import annotations

import json

from google.adk.tools import ToolContext

from pulse_ai.agents.em.templates.base import TIME_RANGE_DOC
from pulse_ai.agents.em.templates.interaction_templates import build_problematic_spans_query
from pulse_ai.agents.em.transformers.response_transformer import (
    parse_error_response,
    transform_columnar,
)
from pulse_ai.agents.em.tools.analytics.query_interaction_metrics import DATA_QUERY_PATH
from pulse_ai.agents.interaction_research.tools._span_rows import normalize_problematic_span_row
from pulse_ai.client.pulse_client import PulseClient
from pulse_ai.tool_session_auth import pulse_tool_session_auth_error

_MAX_SPANS = 10
_DEFAULT_SPANS = 5


async def fetch_problematic_interaction_spans(
    interaction_name: str,
    span_kind: str = "error",
    time_range: str = "last_7d",
    start_time: str | None = None,
    end_time: str | None = None,
    filters: str | None = None,
    limit: int = _DEFAULT_SPANS,
    tool_context: ToolContext = None,
) -> dict:
    """List individual bad interaction spans with session/trace IDs (UI-aligned).

    Use span_kind=error when error rate is elevated; span_kind=poor when poor-user
    share is elevated (slow UX without StatusCode=Error). May call twice (error then poor).

    Args:
        interaction_name: Pulse interaction span name.
        span_kind: error, poor, crash, anr, frozen_frame, or non_fatal.
        time_range: One of: """ + TIME_RANGE_DOC + """
        start_time: ISO start when time_range=custom.
        end_time: ISO end when time_range=custom.
        filters: Optional JSON dimension filters (platform, app_version, etc.).
        limit: Max spans (default 5, max 10).
    """
    parsed_filters = None
    if filters:
        try:
            parsed_filters = json.loads(filters)
        except (json.JSONDecodeError, TypeError):
            return {"status": "error", "message": f"Invalid JSON in filters: {filters}"}

    capped_limit = max(1, min(int(limit), _MAX_SPANS))

    try:
        query_request = build_problematic_spans_query(
            interaction_name=interaction_name,
            span_kind=span_kind,
            time_range=time_range,
            start_time=start_time,
            end_time=end_time,
            user_filters=parsed_filters,
            limit=capped_limit,
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
        rows = [normalize_problematic_span_row(r) for r in transform_columnar(data)]

        return {
            "status": "success",
            "span_kind": span_kind.strip().lower(),
            "count": len(rows),
            "data": rows,
        }

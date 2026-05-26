"""Compact session trace or log timeline for forensics after span sampling."""

from __future__ import annotations

from google.adk.tools import ToolContext

from pulse_ai.agents.em.templates.base import TIME_RANGE_DOC
from pulse_ai.agents.em.templates.interaction_templates import build_session_trace_snapshot_query
from pulse_ai.agents.em.transformers.response_transformer import (
    parse_error_response,
    transform_columnar,
)
from pulse_ai.agents.em.tools.analytics.query_interaction_metrics import DATA_QUERY_PATH
from pulse_ai.client.pulse_client import PulseClient
from pulse_ai.tool_session_auth import (
    pulse_client_kwargs_from_tool_context,
    pulse_tool_session_auth_error,
)

_MAX_ROWS = 200
_DEFAULT_ROWS = 100


async def fetch_session_trace_snapshot(
    session_id: str,
    time_range: str = "last_7d",
    start_time: str | None = None,
    end_time: str | None = None,
    trace_id: str | None = None,
    data_type: str = "logs",
    limit: int = _DEFAULT_ROWS,
    tool_context: ToolContext = None,
) -> dict:
    """Fetch a bounded session timeline sample for one bad span (blocks 4, 6, 8).

    Call only after fetch_problematic_interaction_spans — pick 1–2 session/trace pairs.
    Prefer data_type=logs for custom events; use traces for span/network tree.

    Args:
        session_id: Session id from problematic span rows.
        time_range: One of: """ + TIME_RANGE_DOC + """
        start_time: ISO start when time_range=custom.
        end_time: ISO end when time_range=custom.
        trace_id: Optional trace id to narrow the window.
        data_type: logs (default) or traces.
        limit: Max rows (default 100, max 200).
    """
    capped_limit = max(1, min(int(limit), _MAX_ROWS))
    kind = (data_type or "logs").strip().lower()

    try:
        query_request = build_session_trace_snapshot_query(
            session_id=session_id,
            data_type=kind,
            trace_id=trace_id,
            time_range=time_range,
            start_time=start_time,
            end_time=end_time,
            limit=capped_limit,
        )
    except ValueError as e:
        return {"status": "error", "message": str(e)}

    session_error = pulse_tool_session_auth_error(tool_context)
    if session_error is not None:
        return session_error

    async with PulseClient(**pulse_client_kwargs_from_tool_context(tool_context)) as client:
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
            "session_id": session_id.strip(),
            "trace_id": trace_id.strip() if trace_id else None,
            "data_type": kind,
            "count": len(rows),
            "data": rows,
        }

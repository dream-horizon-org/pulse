"""Mandatory tool: tabular RCA via GET /v1/interactions/{name}/root-cause only."""

from google.adk.tools import ToolContext

from pulse_ai.interaction_root_cause_fetch import (
    fetch_interaction_root_cause_segments as _fetch_root_cause_get,
)
from pulse_ai.root_cause_payload_fetch import RootCauseFetchError
from pulse_ai.schemas import RootCausePayloadSchema
from pulse_ai.schemas.interaction_report_helpers import map_segment_highlights
from pulse_ai.tool_session_auth import (
    pulse_tool_session_auth_error,
    pulse_tool_session_tenant_id,
)


async def fetch_interaction_root_cause_segments(
    interaction_name: str,
    date: str | None = None,
    tool_context: ToolContext = None,
) -> dict:
    """Load tabular RCA segments (dashboard shape) for blocks 3 and 6 evidence.

    Uses GET ``/v1/interactions/{name}/root-cause`` — not the legacy async RCA LLM job.

    Args:
        interaction_name: Pulse interaction span name.
        date: Optional anchor calendar day (YYYY-MM-DD, UTC); defaults to today UTC.
    """
    session_error = pulse_tool_session_auth_error(tool_context)
    if session_error is not None:
        return session_error

    name = (interaction_name or "").strip()
    if not name:
        return {"status": "error", "message": "interaction_name is required"}

    bearer_token = tool_context.state.get("bearer_token")
    project_id = tool_context.state.get("project_id")

    try:
        payload: RootCausePayloadSchema = await _fetch_root_cause_get(
            name,
            date,
            bearer_token,
            project_id,
            pulse_tool_session_tenant_id(tool_context),
        )
    except RootCauseFetchError as err:
        return {
            "status": "error",
            "message": err.message,
            "code": err.status_code,
        }

    highlights = map_segment_highlights(payload)
    return {
        "status": "success",
        "data": payload.model_dump(mode="json"),
        "segment_highlights": (
            [h.model_dump(mode="json") for h in highlights] if highlights else None
        ),
    }

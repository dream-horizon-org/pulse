"""Tool: query_interaction_root_cause — Tabular root-cause payload for one interaction.

GET /v1/interactions/{name}/root-cause → same validated payload as the RCA pipeline.
"""

from google.adk.tools import ToolContext

from pulse_ai.root_cause_payload_fetch import RootCauseFetchError, fetch_root_cause_payload
from pulse_ai.tool_session_auth import pulse_tool_session_auth_error


async def query_interaction_root_cause(
    interaction_name: str,
    date: str | None = None,
    tool_context: ToolContext = None,
) -> dict:
    """Load segment-level root-cause tabular data for a named interaction.

    This is the same JSON shape used to build full narrative RCA reports; it is
    not the long-form report text. Use an explicit calendar date (YYYY-MM-DD) when
    the user asks for a specific day; otherwise defaults to today (UTC).

    Args:
        interaction_name: Pulse interaction name (must match configuration).
        date: Optional ISO date (YYYY-MM-DD) for the root-cause window anchor.
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
        payload = await fetch_root_cause_payload(
            name,
            date,
            bearer_token,
            project_id,
        )
    except RootCauseFetchError as err:
        return {
            "status": "error",
            "message": err.message,
            "code": err.status_code,
        }

    return {
        "status": "success",
        "data": payload.model_dump(mode="json"),
    }

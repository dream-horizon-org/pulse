"""Tool: query_interaction_root_cause — Tabular root-cause payload for one interaction.

Uses the same async RCA pipeline as the Pulse Interaction RCA tab: peek ``/v1/ai-rca/report``,
``POST /v1/ai/rca/report`` when needed, poll ``/v1/ai-rca/job/{jobId}`` until ``COMPLETED``,
then reads ``rootCausePayload`` from the completed report JSON.
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

    Same tabular JSON shape as ``rootCausePayload`` on the dashboard RCA report. Pass an
    explicit calendar date (YYYY-MM-DD) when the user names a day; otherwise defaults to
    today (UTC). Data comes from the async RCA job completion payload, not a separate
    tabular GET.

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

"""Tool 3: query_alerts — Read alert configuration and evaluation data.

Calls REST endpoints directly (GET requests, no QueryRequest needed).
Supports: list, detail, evaluation_history, available_scopes.
"""

from google.adk.tools import ToolContext

from pulse_ai.client.pulse_client import PulseClient

VALID_SCOPES = ("list", "detail", "evaluation_history", "available_scopes")


async def query_alerts(
    scope: str,
    alert_id: str = None,
    name: str = None,
    alert_scope: str = None,
    state: str = None,
    limit: int = 10,
    offset: int = 0,
    tool_context: ToolContext = None,
) -> dict:
    """Read alert configuration and evaluation data.

    Args:
        scope: What to read. One of: list, detail, evaluation_history, available_scopes
        alert_id: The alert ID (required for scope="detail" and "evaluation_history")
        name: Search alerts by name (scope="list")
        alert_scope: Filter by scope: interaction, screen, app_vitals, network_api (scope="list")
        state: Filter by state: FIRING, NO_DATA, NORMAL, SNOOZED (scope="list")
        limit: Max results per page (scope="list", default 10)
        offset: Pagination offset (scope="list", default 0)
    """
    # Validate scope
    if scope not in VALID_SCOPES:
        return {
            "status": "error",
            "message": f"Invalid scope '{scope}'. Valid scopes: {', '.join(VALID_SCOPES)}",
        }

    # Validate required params
    if scope in ("detail", "evaluation_history") and not alert_id:
        return {
            "status": "error",
            "message": f"alert_id is required when scope='{scope}'",
        }

    bearer_token = tool_context.state.get("bearer_token") if tool_context else None
    project_id = tool_context.state.get("project_id") if tool_context else None
    client = PulseClient(authorization_header=bearer_token, project_id=project_id)

    if scope == "list":
        params = {"limit": limit, "offset": offset}
        if name:
            params["name"] = name
        if alert_scope:
            params["scope"] = alert_scope
        if state:
            params["state"] = state
        response = await client.request("GET", "/v1/alert", params=params)

    elif scope == "detail":
        response = await client.request("GET", f"/v1/alert/{alert_id}")

    elif scope == "evaluation_history":
        response = await client.request("GET", f"/v1/alert/{alert_id}/evaluationHistory")

    elif scope == "available_scopes":
        response = await client.request("GET", "/v1/alert/scopes")

    # Handle network errors (PulseClient returns dict on connection/timeout)
    if isinstance(response, dict):
        return response

    # Handle HTTP errors
    if response.status_code >= 400:
        return PulseClient.parse_error(response)

    # Success
    body = response.json()
    return {"status": "success", "data": body.get("data", body)}

"""Tool 3: query_alerts — Read alert configuration and evaluation data.

Calls REST endpoints directly (GET requests, no QueryRequest needed).
Supports: list, detail, evaluation_history, available_scopes.
"""

from pulse_ai.client.pulse_client import PulseClient
from pulse_ai.transformers.response_transformer import parse_error_response

VALID_SCOPES = ("list", "detail", "evaluation_history", "available_scopes")


async def query_alerts(
    scope: str,
    alert_id: str = None,
    name: str = None,
    alert_scope: str = None,
    state: str = None,
    limit: int = 10,
    offset: int = 0,
    time_range: str = "last_24h",
    start_time: str = None,
    end_time: str = None,
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
        time_range: Time range for data queries (scope="available_scopes"). One of: last_5m, last_15m, last_30m, last_1h, last_3h, last_6h, last_12h, last_24h, last_2d, last_7d, last_30d, last_90d, yesterday, previous_week, previous_month, today_so_far, this_week, this_month_so_far, custom
        start_time: ISO 8601 start (only when time_range="custom")
        end_time: ISO 8601 end (only when time_range="custom")
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

    client = PulseClient()

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
        return parse_error_response(response)

    # Success
    body = response.json()
    return {"status": "success", "data": body.get("data", body)}

"""Tool 1: query_interactions — Read interaction configuration data.

Calls REST endpoints directly (GET requests, no QueryRequest needed).
Supports: list, detail, filters, telemetry_filters.
"""

from pulse_ai.client.pulse_client import PulseClient
from pulse_ai.transformers.response_transformer import parse_error_response

VALID_SCOPES = ("list", "detail", "filters", "telemetry_filters")


async def query_interactions(
    scope: str,
    interaction_name: str = None,
    page: int = 0,
    size: int = 10,
    name: str = None,
    status: str = "RUNNING",
) -> dict:
    """Read interaction configuration data.

    Args:
        scope: What to read. One of: list, detail, filters, telemetry_filters
        interaction_name: The interaction name (required for scope="detail")
        page: Page number for pagination (scope="list", default 0)
        size: Number of results per page (scope="list", default 10)
        name: Search interactions by name (scope="list")
        status: Filter by status: RUNNING or STOPPED (scope="list", default RUNNING)
    """
    # Validate scope
    if scope not in VALID_SCOPES:
        return {
            "status": "error",
            "message": f"Invalid scope '{scope}'. Valid scopes: {', '.join(VALID_SCOPES)}",
        }

    # Validate required params
    if scope == "detail" and not interaction_name:
        return {
            "status": "error",
            "message": "interaction_name is required when scope='detail'",
        }

    client = PulseClient()

    if scope == "list":
        params = {"page": page, "size": size, "status": status}
        if name:
            params["name"] = name
        response = await client.request("GET", "/v1/interactions", params=params)

    elif scope == "detail":
        response = await client.request("GET", f"/v1/interactions/{interaction_name}")

    elif scope == "filters":
        response = await client.request("GET", "/v1/interactions/filter-options")

    elif scope == "telemetry_filters":
        response = await client.request("GET", "/v1/interactions/telemetry-filters")

    # Handle network errors (PulseClient returns dict on connection/timeout)
    if isinstance(response, dict):
        return response

    # Handle HTTP errors
    if response.status_code >= 400:
        return parse_error_response(response)

    # Success
    body = response.json()
    return {"status": "success", "data": body.get("data", body)}

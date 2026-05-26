"""Tests for PulseClient — forwarded JWT + project to pulse-server."""

import httpx
import pytest
import respx

from pulse_ai.constants import (
    PULSE_TOOL_SESSION_MISSING_BEARER,
    PULSE_TOOL_SESSION_MISSING_PROJECT,
)

_AUTH = "Bearer test-access-token"
_PROJECT = "test-project-id"


# ---------------------------------------------------------------------------
# Request shape
# ---------------------------------------------------------------------------


_TENANT = "tenant-abc-123"


@respx.mock
@pytest.mark.asyncio
async def test_client_sends_tenant_header_when_configured():
    """GET includes X-Tenant-ID when tenant_id is set on the client."""
    from pulse_ai.client.pulse_client import PulseClient

    route = respx.get("http://localhost:8080/v1/event-definitions").mock(
        return_value=httpx.Response(200, json={"data": [], "error": None})
    )

    async with PulseClient(
        authorization_header=_AUTH,
        project_id=_PROJECT,
        tenant_id=_TENANT,
    ) as client:
        await client.request("GET", "/v1/event-definitions")

    assert route.called
    sent_headers = route.calls[0].request.headers
    assert sent_headers.get("x-tenant-id") == _TENANT


@respx.mock
@pytest.mark.asyncio
async def test_client_omits_tenant_header_when_not_configured():
    """GET does not send X-Tenant-ID when tenant_id is omitted."""
    from pulse_ai.client.pulse_client import PulseClient

    route = respx.get("http://localhost:8080/v1/interactions").mock(
        return_value=httpx.Response(200, json={"data": [], "error": None})
    )

    async with PulseClient(authorization_header=_AUTH, project_id=_PROJECT) as client:
        await client.request("GET", "/v1/interactions")

    assert route.called
    sent_headers = route.calls[0].request.headers
    assert "x-tenant-id" not in sent_headers


@respx.mock
@pytest.mark.asyncio
async def test_client_sends_auth_and_project_headers():
    """GET includes Authorization and X-Project-ID."""
    from pulse_ai.client.pulse_client import PulseClient

    route = respx.get("http://localhost:8080/v1/interactions").mock(
        return_value=httpx.Response(200, json={"data": [], "error": None})
    )

    async with PulseClient(authorization_header=_AUTH, project_id=_PROJECT) as client:
        await client.request("GET", "/v1/interactions")

    assert route.called
    sent_headers = route.calls[0].request.headers
    assert sent_headers["authorization"] == _AUTH
    assert sent_headers.get("x-project-id") == _PROJECT


@respx.mock
@pytest.mark.asyncio
async def test_client_sends_content_type_on_post():
    """POST request includes Content-Type: application/json."""
    from pulse_ai.client.pulse_client import PulseClient

    route = respx.post("http://localhost:8080/v1/interactions/performance-metric/distribution").mock(
        return_value=httpx.Response(200, json={"data": {}, "error": None})
    )

    async with PulseClient(authorization_header=_AUTH, project_id=_PROJECT) as client:
        await client.request("POST", "/v1/interactions/performance-metric/distribution", json={"test": True})

    assert route.called
    sent_headers = route.calls[0].request.headers
    assert "application/json" in sent_headers["content-type"]


# ---------------------------------------------------------------------------
# Validation (defense in depth after pulse_tool_session_auth_error)
# ---------------------------------------------------------------------------


@pytest.mark.asyncio
async def test_returns_error_when_authorization_empty():
    from pulse_ai.client.pulse_client import PulseClient

    async with PulseClient(authorization_header="", project_id=_PROJECT) as client:
        response = await client.request("GET", "/v1/interactions")

    assert response == {"status": "error", "message": PULSE_TOOL_SESSION_MISSING_BEARER}


@pytest.mark.asyncio
async def test_returns_error_when_project_empty():
    from pulse_ai.client.pulse_client import PulseClient

    async with PulseClient(authorization_header=_AUTH, project_id="") as client:
        response = await client.request("GET", "/v1/interactions")

    assert response == {"status": "error", "message": PULSE_TOOL_SESSION_MISSING_PROJECT}


# ---------------------------------------------------------------------------
# Errors
# ---------------------------------------------------------------------------


@respx.mock
@pytest.mark.asyncio
async def test_client_handles_network_error():
    from pulse_ai.client.pulse_client import PulseClient

    respx.get("http://localhost:8080/v1/interactions").mock(
        side_effect=httpx.ConnectError("Connection refused")
    )

    async with PulseClient(authorization_header=_AUTH, project_id=_PROJECT) as client:
        response = await client.request("GET", "/v1/interactions")

    assert isinstance(response, dict)
    assert response["status"] == "error"
    assert "Connection refused" in response["message"] or "connect" in response["message"].lower()


@respx.mock
@pytest.mark.asyncio
async def test_client_handles_timeout():
    from pulse_ai.client.pulse_client import PulseClient

    respx.get("http://localhost:8080/v1/interactions").mock(
        side_effect=httpx.ReadTimeout("Read timed out")
    )

    async with PulseClient(authorization_header=_AUTH, project_id=_PROJECT) as client:
        response = await client.request("GET", "/v1/interactions")

    assert isinstance(response, dict)
    assert response["status"] == "error"
    assert "timeout" in response["message"].lower() or "timed out" in response["message"].lower()


@respx.mock
@pytest.mark.asyncio
async def test_client_returns_http_response_on_401():
    """401 is returned as-is; no refresh in this client."""
    from pulse_ai.client.pulse_client import PulseClient

    respx.get("http://localhost:8080/v1/interactions").mock(
        return_value=httpx.Response(401, json={"error": {"code": "UNAUTHORIZED"}})
    )

    async with PulseClient(authorization_header=_AUTH, project_id=_PROJECT) as client:
        response = await client.request("GET", "/v1/interactions")

    assert isinstance(response, httpx.Response)
    assert response.status_code == 401

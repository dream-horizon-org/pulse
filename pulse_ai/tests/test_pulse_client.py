"""Tests for PulseClient — HTTP client with auth headers."""

import httpx
import pytest
import respx

_TEST_ACCESS = "test-access-token"


# ---------------------------------------------------------------------------
# Test 1: Client sends Authorization header
# ---------------------------------------------------------------------------

@respx.mock
@pytest.mark.asyncio
async def test_client_sends_auth_header():
    """GET request includes Authorization: Bearer <token>."""
    from pulse_ai.client.pulse_client import PulseClient

    route = respx.get("http://localhost:8080/v1/interactions").mock(
        return_value=httpx.Response(200, json={"data": [], "error": None})
    )

    client = PulseClient(access_token=_TEST_ACCESS)
    await client.request("GET", "/v1/interactions")

    assert route.called
    sent_headers = route.calls[0].request.headers
    assert "authorization" in sent_headers
    assert sent_headers["authorization"] == f"Bearer {_TEST_ACCESS}"


# ---------------------------------------------------------------------------
# Test 2: Client sends Content-Type on POST
# ---------------------------------------------------------------------------

@respx.mock
@pytest.mark.asyncio
async def test_client_sends_content_type_on_post():
    """POST request includes Content-Type: application/json."""
    from pulse_ai.client.pulse_client import PulseClient

    route = respx.post("http://localhost:8080/v1/interactions/performance-metric/distribution").mock(
        return_value=httpx.Response(200, json={"data": {}, "error": None})
    )

    client = PulseClient(access_token=_TEST_ACCESS)
    await client.request("POST", "/v1/interactions/performance-metric/distribution", json={"test": True})

    assert route.called
    sent_headers = route.calls[0].request.headers
    assert "content-type" in sent_headers
    assert "application/json" in sent_headers["content-type"]


# ---------------------------------------------------------------------------
# Test 3: Handles network error gracefully
# ---------------------------------------------------------------------------

@respx.mock
@pytest.mark.asyncio
async def test_client_handles_network_error():
    """Connection error returns structured error, no exception raised."""
    from pulse_ai.client.pulse_client import PulseClient

    respx.get("http://localhost:8080/v1/interactions").mock(
        side_effect=httpx.ConnectError("Connection refused")
    )

    client = PulseClient()
    response = await client.request("GET", "/v1/interactions")

    assert response is not None
    assert response["status"] == "error"
    assert "Connection refused" in response["message"] or "connect" in response["message"].lower()


# ---------------------------------------------------------------------------
# Test 4: Handles timeout gracefully
# ---------------------------------------------------------------------------

@respx.mock
@pytest.mark.asyncio
async def test_client_handles_timeout():
    """Timeout returns structured error, no exception raised."""
    from pulse_ai.client.pulse_client import PulseClient

    respx.get("http://localhost:8080/v1/interactions").mock(
        side_effect=httpx.ReadTimeout("Read timed out")
    )

    client = PulseClient()
    response = await client.request("GET", "/v1/interactions")

    assert response is not None
    assert response["status"] == "error"
    assert "timeout" in response["message"].lower() or "timed out" in response["message"].lower()

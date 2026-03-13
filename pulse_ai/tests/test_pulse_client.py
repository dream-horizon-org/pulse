"""Tests for PulseClient — HTTP client with auth headers and 401-retry.

TDD RED: All tests written before pulse_ai/client/pulse_client.py exists.
"""

import httpx
import pytest
import respx

from tests.conftest import make_response


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

    client = PulseClient()
    await client.request("GET", "/v1/interactions")

    assert route.called
    sent_headers = route.calls[0].request.headers
    assert "authorization" in sent_headers
    assert sent_headers["authorization"] == "Bearer test-access-token"


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

    client = PulseClient()
    await client.request("POST", "/v1/interactions/performance-metric/distribution", json={"test": True})

    assert route.called
    sent_headers = route.calls[0].request.headers
    assert "content-type" in sent_headers
    assert "application/json" in sent_headers["content-type"]


# ---------------------------------------------------------------------------
# Test 3: Client retries on 401
# ---------------------------------------------------------------------------

@respx.mock
@pytest.mark.asyncio
async def test_client_retry_on_401():
    """401 response triggers token refresh and retries the original request."""
    from pulse_ai.client.pulse_client import PulseClient

    # First call: 401, second call (after refresh): 200
    route = respx.get("http://localhost:8080/v1/interactions")
    route.side_effect = [
        httpx.Response(401, json={"error": {"code": "UNAUTHORIZED", "message": "Token expired"}}),
        httpx.Response(200, json={"data": [{"name": "TestInteraction"}], "error": None}),
    ]

    # Refresh endpoint
    refresh_route = respx.post("http://localhost:8080/v1/auth/token/refresh").mock(
        return_value=httpx.Response(200, json={
            "data": {
                "accessToken": "new-access-token",
                "refreshToken": "test-refresh-token",
                "tokenType": "Bearer",
                "expiresIn": 86400,
            },
            "error": None,
        })
    )

    client = PulseClient()
    response = await client.request("GET", "/v1/interactions")

    assert refresh_route.called
    assert response.status_code == 200
    # Original endpoint called twice (initial + retry)
    assert route.call_count == 2


# ---------------------------------------------------------------------------
# Test 4: No retry without refresh token
# ---------------------------------------------------------------------------

@respx.mock
@pytest.mark.asyncio
async def test_client_no_retry_without_refresh_token(monkeypatch):
    """401 without refresh token returns 401, no retry attempt."""
    from pulse_ai.client.pulse_client import PulseClient

    monkeypatch.setenv("PULSE_REFRESH_TOKEN", "")

    route = respx.get("http://localhost:8080/v1/interactions").mock(
        return_value=httpx.Response(401, json={"error": {"code": "UNAUTHORIZED", "message": "Token expired"}})
    )

    client = PulseClient()
    response = await client.request("GET", "/v1/interactions")

    assert response.status_code == 401
    assert route.call_count == 1  # No retry


# ---------------------------------------------------------------------------
# Test 5: Token updated after refresh
# ---------------------------------------------------------------------------

@respx.mock
@pytest.mark.asyncio
async def test_client_updates_token_after_refresh():
    """After refresh, subsequent requests use the new access token."""
    from pulse_ai.client.pulse_client import PulseClient

    # First request: 401 → refresh → retry with new token
    interactions_route = respx.get("http://localhost:8080/v1/interactions")
    interactions_route.side_effect = [
        httpx.Response(401, json={"error": {"code": "UNAUTHORIZED", "message": "expired"}}),
        httpx.Response(200, json={"data": [], "error": None}),
    ]

    respx.post("http://localhost:8080/v1/auth/token/refresh").mock(
        return_value=httpx.Response(200, json={
            "data": {
                "accessToken": "refreshed-token",
                "refreshToken": "test-refresh-token",
                "tokenType": "Bearer",
                "expiresIn": 86400,
            },
            "error": None,
        })
    )

    client = PulseClient()
    await client.request("GET", "/v1/interactions")

    # The retry request should use the refreshed token
    retry_request = interactions_route.calls[1].request
    assert retry_request.headers["authorization"] == "Bearer refreshed-token"


# ---------------------------------------------------------------------------
# Test 6: Refresh returns same refresh token (no rotation)
# ---------------------------------------------------------------------------

@respx.mock
@pytest.mark.asyncio
async def test_client_refresh_preserves_refresh_token():
    """Refresh response returns the same refresh token — stored correctly."""
    from pulse_ai.client.pulse_client import PulseClient

    respx.get("http://localhost:8080/v1/interactions").side_effect = [
        httpx.Response(401, json={"error": {"code": "UNAUTHORIZED", "message": "expired"}}),
        httpx.Response(200, json={"data": [], "error": None}),
    ]

    respx.post("http://localhost:8080/v1/auth/token/refresh").mock(
        return_value=httpx.Response(200, json={
            "data": {
                "accessToken": "new-token",
                "refreshToken": "same-refresh-token",
                "tokenType": "Bearer",
                "expiresIn": 86400,
            },
            "error": None,
        })
    )

    client = PulseClient()
    await client.request("GET", "/v1/interactions")

    assert client.refresh_token == "same-refresh-token"


# ---------------------------------------------------------------------------
# Test 7: No infinite retry loop
# ---------------------------------------------------------------------------

@respx.mock
@pytest.mark.asyncio
async def test_client_no_infinite_retry():
    """If refresh also gets 401, return error — don't loop."""
    from pulse_ai.client.pulse_client import PulseClient

    respx.get("http://localhost:8080/v1/interactions").mock(
        return_value=httpx.Response(401, json={"error": {"code": "UNAUTHORIZED", "message": "expired"}})
    )

    # Refresh itself fails with 401
    respx.post("http://localhost:8080/v1/auth/token/refresh").mock(
        return_value=httpx.Response(401, json={"error": {"code": "UNAUTHORIZED", "message": "refresh failed"}})
    )

    client = PulseClient()
    response = await client.request("GET", "/v1/interactions")

    # Should return error, not loop
    assert response.status_code == 401


# ---------------------------------------------------------------------------
# Test 8: Handles network error gracefully
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

    # Should return a dict with error info, not raise
    assert response is not None
    assert response["status"] == "error"
    assert "Connection refused" in response["message"] or "connect" in response["message"].lower()


# ---------------------------------------------------------------------------
# Test 9: Handles timeout gracefully
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

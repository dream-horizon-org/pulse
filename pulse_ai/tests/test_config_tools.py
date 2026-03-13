"""Tests for config tools — query_interactions and query_alerts.

TDD RED: All tests written before pulse_ai/tools/config/ exists.
These tools call REST endpoints directly (GET requests, no QueryRequest).
"""

import httpx
import pytest
import respx


# ===================================================================
# query_interactions — scope="list"
# ===================================================================


@respx.mock
@pytest.mark.asyncio
async def test_query_interactions_list_default():
    """scope=list with defaults returns paginated interactions."""
    from pulse_ai.tools.config.query_interactions import query_interactions

    respx.get("http://localhost:8080/v1/interactions").mock(
        return_value=httpx.Response(200, json={
            "data": [
                {"name": "ContestJoin", "status": "RUNNING", "description": "Join contest"},
                {"name": "MatchEntry", "status": "RUNNING", "description": "Enter match"},
            ],
            "error": None,
        })
    )

    result = await query_interactions(scope="list")

    assert result["status"] == "success"
    assert len(result["data"]) == 2
    assert result["data"][0]["name"] == "ContestJoin"


@respx.mock
@pytest.mark.asyncio
async def test_query_interactions_list_sends_query_params():
    """scope=list sends page, size, status as query params."""
    from pulse_ai.tools.config.query_interactions import query_interactions

    route = respx.get("http://localhost:8080/v1/interactions").mock(
        return_value=httpx.Response(200, json={"data": [], "error": None})
    )

    await query_interactions(scope="list", page=2, size=5, status="STOPPED", name="Contest")

    assert route.called
    request = route.calls[0].request
    assert "page=2" in str(request.url)
    assert "size=5" in str(request.url)
    assert "status=STOPPED" in str(request.url)
    assert "name=Contest" in str(request.url)


# ===================================================================
# query_interactions — scope="detail"
# ===================================================================


@respx.mock
@pytest.mark.asyncio
async def test_query_interactions_detail():
    """scope=detail fetches a single interaction by name."""
    from pulse_ai.tools.config.query_interactions import query_interactions

    respx.get("http://localhost:8080/v1/interactions/ContestJoin").mock(
        return_value=httpx.Response(200, json={
            "data": {
                "name": "ContestJoin",
                "status": "RUNNING",
                "description": "Join contest flow",
                "uptimeLowerLimitInMs": 500,
                "uptimeMidLimitInMs": 2000,
                "uptimeUpperLimitInMs": 5000,
                "thresholdInMs": 10000,
            },
            "error": None,
        })
    )

    result = await query_interactions(scope="detail", interaction_name="ContestJoin")

    assert result["status"] == "success"
    assert result["data"]["name"] == "ContestJoin"
    assert result["data"]["thresholdInMs"] == 10000


@respx.mock
@pytest.mark.asyncio
async def test_query_interactions_detail_missing_name():
    """scope=detail without interaction_name returns validation error."""
    from pulse_ai.tools.config.query_interactions import query_interactions

    result = await query_interactions(scope="detail")

    assert result["status"] == "error"
    assert "interaction_name" in result["message"].lower()


# ===================================================================
# query_interactions — scope="filters" and "telemetry_filters"
# ===================================================================


@respx.mock
@pytest.mark.asyncio
async def test_query_interactions_filters():
    """scope=filters hits /v1/interactions/filter-options."""
    from pulse_ai.tools.config.query_interactions import query_interactions

    respx.get("http://localhost:8080/v1/interactions/filter-options").mock(
        return_value=httpx.Response(200, json={
            "data": {"platforms": ["Android", "iOS"]},
            "error": None,
        })
    )

    result = await query_interactions(scope="filters")

    assert result["status"] == "success"
    assert "platforms" in result["data"]


@respx.mock
@pytest.mark.asyncio
async def test_query_interactions_telemetry_filters():
    """scope=telemetry_filters hits /v1/interactions/telemetry-filters."""
    from pulse_ai.tools.config.query_interactions import query_interactions

    respx.get("http://localhost:8080/v1/interactions/telemetry-filters").mock(
        return_value=httpx.Response(200, json={
            "data": {"filters": ["device", "os"]},
            "error": None,
        })
    )

    result = await query_interactions(scope="telemetry_filters")

    assert result["status"] == "success"


# ===================================================================
# query_interactions — error handling
# ===================================================================


@respx.mock
@pytest.mark.asyncio
async def test_query_interactions_invalid_scope():
    """Invalid scope returns error without hitting backend."""
    from pulse_ai.tools.config.query_interactions import query_interactions

    result = await query_interactions(scope="invalid_scope")

    assert result["status"] == "error"
    assert "scope" in result["message"].lower()


@respx.mock
@pytest.mark.asyncio
async def test_query_interactions_backend_error():
    """Backend 500 returns structured error."""
    from pulse_ai.tools.config.query_interactions import query_interactions

    respx.get("http://localhost:8080/v1/interactions").mock(
        return_value=httpx.Response(500, json={
            "data": None,
            "error": {"code": "INTERNAL", "message": "Database unavailable"},
        })
    )

    result = await query_interactions(scope="list")

    assert result["status"] == "error"
    assert "Database unavailable" in result["message"]


# ===================================================================
# query_alerts — scope="list"
# ===================================================================


@respx.mock
@pytest.mark.asyncio
async def test_query_alerts_list_default():
    """scope=list with defaults returns paginated alerts."""
    from pulse_ai.tools.config.query_alerts import query_alerts

    respx.get("http://localhost:8080/v1/alert").mock(
        return_value=httpx.Response(200, json={
            "data": [
                {"id": 1, "name": "High Error Rate", "state": "NORMAL"},
                {"id": 2, "name": "Latency Spike", "state": "FIRING"},
            ],
            "error": None,
        })
    )

    result = await query_alerts(scope="list")

    assert result["status"] == "success"
    assert len(result["data"]) == 2


@respx.mock
@pytest.mark.asyncio
async def test_query_alerts_list_sends_query_params():
    """scope=list sends name, scope, state, limit, offset as query params."""
    from pulse_ai.tools.config.query_alerts import query_alerts

    route = respx.get("http://localhost:8080/v1/alert").mock(
        return_value=httpx.Response(200, json={"data": [], "error": None})
    )

    await query_alerts(scope="list", name="Error", alert_scope="interaction", state="FIRING", limit=5, offset=10)

    assert route.called
    request = route.calls[0].request
    assert "name=Error" in str(request.url)
    assert "scope=interaction" in str(request.url)
    assert "state=FIRING" in str(request.url)
    assert "limit=5" in str(request.url)
    assert "offset=10" in str(request.url)


# ===================================================================
# query_alerts — scope="detail"
# ===================================================================


@respx.mock
@pytest.mark.asyncio
async def test_query_alerts_detail():
    """scope=detail fetches a single alert by ID."""
    from pulse_ai.tools.config.query_alerts import query_alerts

    respx.get("http://localhost:8080/v1/alert/42").mock(
        return_value=httpx.Response(200, json={
            "data": {"id": 42, "name": "High Error Rate", "state": "NORMAL"},
            "error": None,
        })
    )

    result = await query_alerts(scope="detail", alert_id="42")

    assert result["status"] == "success"
    assert result["data"]["id"] == 42


@respx.mock
@pytest.mark.asyncio
async def test_query_alerts_detail_missing_id():
    """scope=detail without alert_id returns validation error."""
    from pulse_ai.tools.config.query_alerts import query_alerts

    result = await query_alerts(scope="detail")

    assert result["status"] == "error"
    assert "alert_id" in result["message"].lower()


# ===================================================================
# query_alerts — scope="evaluation_history"
# ===================================================================


@respx.mock
@pytest.mark.asyncio
async def test_query_alerts_evaluation_history():
    """scope=evaluation_history hits /v1/alert/{id}/evaluationHistory."""
    from pulse_ai.tools.config.query_alerts import query_alerts

    respx.get("http://localhost:8080/v1/alert/42/evaluationHistory").mock(
        return_value=httpx.Response(200, json={
            "data": [{"timestamp": "2026-03-09T10:00:00Z", "result": "NORMAL"}],
            "error": None,
        })
    )

    result = await query_alerts(scope="evaluation_history", alert_id="42")

    assert result["status"] == "success"


@respx.mock
@pytest.mark.asyncio
async def test_query_alerts_evaluation_history_missing_id():
    """scope=evaluation_history without alert_id returns validation error."""
    from pulse_ai.tools.config.query_alerts import query_alerts

    result = await query_alerts(scope="evaluation_history")

    assert result["status"] == "error"
    assert "alert_id" in result["message"].lower()


# ===================================================================
# query_alerts — scope="available_scopes"
# ===================================================================


@respx.mock
@pytest.mark.asyncio
async def test_query_alerts_available_scopes():
    """scope=available_scopes hits /v1/alert/scopes."""
    from pulse_ai.tools.config.query_alerts import query_alerts

    respx.get("http://localhost:8080/v1/alert/scopes").mock(
        return_value=httpx.Response(200, json={
            "data": ["interaction", "screen", "app_vitals", "network_api"],
            "error": None,
        })
    )

    result = await query_alerts(scope="available_scopes")

    assert result["status"] == "success"


# ===================================================================
# query_alerts — error handling
# ===================================================================


@respx.mock
@pytest.mark.asyncio
async def test_query_alerts_invalid_scope():
    """Invalid scope returns error without hitting backend."""
    from pulse_ai.tools.config.query_alerts import query_alerts

    result = await query_alerts(scope="invalid_scope")

    assert result["status"] == "error"
    assert "scope" in result["message"].lower()


@respx.mock
@pytest.mark.asyncio
async def test_query_alerts_backend_error():
    """Backend error returns structured error."""
    from pulse_ai.tools.config.query_alerts import query_alerts

    respx.get("http://localhost:8080/v1/alert").mock(
        return_value=httpx.Response(500, json={
            "data": None,
            "error": {"code": "INTERNAL", "message": "Service down"},
        })
    )

    result = await query_alerts(scope="list")

    assert result["status"] == "error"
    assert "Service down" in result["message"]

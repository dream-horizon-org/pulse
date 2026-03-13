"""Tests for analytics tools — 4 tools using templates from Step 6.

TDD RED: All tests written before pulse_ai/tools/analytics/ exists.
Each tool: validate params → pick template → call PulseClient → transform response.
"""

import json

import httpx
import pytest
import respx
from freezegun import freeze_time

# Reusable mock response matching backend columnar format
MOCK_COLUMNAR_RESPONSE = {
    "data": {
        "fields": ["apdex", "success_count", "error_count"],
        "rows": [["0.85", "100", "15"]],
    },
    "error": None,
}

MOCK_HEALTH_RESPONSE = {
    "data": {
        "fields": ["interaction_name", "spanfreq", "apdex", "success_count", "error_count",
                    "user_excellent", "user_good", "user_avg", "user_poor", "p50"],
        "rows": [
            ["ContestJoin", "500", "0.92", "450", "50", "200", "150", "80", "20", "1200"],
            ["MatchEntry", "300", "0.78", "250", "50", "100", "100", "60", "40", "2500"],
        ],
    },
    "error": None,
}

DATA_QUERY_URL = "http://localhost:8080/v1/interactions/performance-metric/distribution"


# ===================================================================
# query_interaction_health (Tool 5)
# ===================================================================


@respx.mock
@freeze_time("2026-03-09T12:00:00Z")
@pytest.mark.asyncio
async def test_health_returns_transformed_data():
    """Health tool returns list-of-dicts from columnar response."""
    from pulse_ai.tools.analytics.query_interaction_health import query_interaction_health

    respx.post(DATA_QUERY_URL).mock(
        return_value=httpx.Response(200, json=MOCK_HEALTH_RESPONSE)
    )

    result = await query_interaction_health()

    assert result["status"] == "success"
    assert len(result["data"]) == 2
    assert result["data"][0]["interaction_name"] == "ContestJoin"
    assert result["data"][0]["apdex"] == 0.92


@respx.mock
@freeze_time("2026-03-09T12:00:00Z")
@pytest.mark.asyncio
async def test_health_sends_correct_request_body():
    """Health tool sends QueryRequest with correct select/groupBy/orderBy."""
    from pulse_ai.tools.analytics.query_interaction_health import query_interaction_health

    route = respx.post(DATA_QUERY_URL).mock(
        return_value=httpx.Response(200, json=MOCK_HEALTH_RESPONSE)
    )

    await query_interaction_health(top_n=5)

    assert route.called
    body = route.calls[0].request.read()
    import json
    request_body = json.loads(body)
    assert request_body["dataType"] == "TRACES"
    assert request_body["limit"] == 5
    # Must have PulseType filter
    pulse_type = [f for f in request_body["filters"] if f["field"] == "PulseType"]
    assert len(pulse_type) == 1


@respx.mock
@freeze_time("2026-03-09T12:00:00Z")
@pytest.mark.asyncio
async def test_health_with_specific_interactions():
    """Health tool filters by specific interaction names."""
    from pulse_ai.tools.analytics.query_interaction_health import query_interaction_health

    route = respx.post(DATA_QUERY_URL).mock(
        return_value=httpx.Response(200, json=MOCK_HEALTH_RESPONSE)
    )

    await query_interaction_health(interaction_names=["ContestJoin"])

    import json
    body = json.loads(route.calls[0].request.read())
    span_filters = [f for f in body["filters"] if f["field"] == "SpanName"]
    assert len(span_filters) == 1
    assert span_filters[0]["operator"] == "IN"


@respx.mock
@freeze_time("2026-03-09T12:00:00Z")
@pytest.mark.asyncio
async def test_health_backend_error():
    """Health tool returns structured error on backend failure."""
    from pulse_ai.tools.analytics.query_interaction_health import query_interaction_health

    respx.post(DATA_QUERY_URL).mock(
        return_value=httpx.Response(500, json={
            "data": None,
            "error": {"code": "INTERNAL", "message": "DB timeout"},
        })
    )

    result = await query_interaction_health()

    assert result["status"] == "error"
    assert "DB timeout" in result["message"]


# ===================================================================
# query_interaction_metrics (Tool 6)
# ===================================================================


@respx.mock
@freeze_time("2026-03-09T12:00:00Z")
@pytest.mark.asyncio
async def test_metrics_apdex_returns_data():
    """Metrics tool with apdex metric returns transformed data."""
    from pulse_ai.tools.analytics.query_interaction_metrics import query_interaction_metrics

    respx.post(DATA_QUERY_URL).mock(
        return_value=httpx.Response(200, json=MOCK_COLUMNAR_RESPONSE)
    )

    result = await query_interaction_metrics(
        metric_type="apdex", interaction_name="ContestJoin"
    )

    assert result["status"] == "success"
    assert len(result["data"]) == 1


@respx.mock
@freeze_time("2026-03-09T12:00:00Z")
@pytest.mark.asyncio
async def test_metrics_timeseries_includes_time_bucket():
    """Metrics tool with timeseries=True sends TIME_BUCKET in select."""
    from pulse_ai.tools.analytics.query_interaction_metrics import query_interaction_metrics

    route = respx.post(DATA_QUERY_URL).mock(
        return_value=httpx.Response(200, json={
            "data": {"fields": ["t1", "apdex"], "rows": [["2026-03-09T12:00:00Z", "0.85"]]},
            "error": None,
        })
    )

    await query_interaction_metrics(
        metric_type="apdex", interaction_name="ContestJoin", timeseries=True
    )

    import json
    body = json.loads(route.calls[0].request.read())
    assert body["select"][0]["function"] == "TIME_BUCKET"


@respx.mock
@freeze_time("2026-03-09T12:00:00Z")
@pytest.mark.asyncio
async def test_metrics_invalid_type_returns_error():
    """Metrics tool with invalid metric_type returns error without hitting backend."""
    from pulse_ai.tools.analytics.query_interaction_metrics import query_interaction_metrics

    result = await query_interaction_metrics(
        metric_type="invalid_metric", interaction_name="ContestJoin"
    )

    assert result["status"] == "error"
    assert "metric_type" in result["message"].lower()


@respx.mock
@freeze_time("2026-03-09T12:00:00Z")
@pytest.mark.asyncio
async def test_metrics_with_filters():
    """Metrics tool passes user filters to QueryRequest."""
    from pulse_ai.tools.analytics.query_interaction_metrics import query_interaction_metrics

    route = respx.post(DATA_QUERY_URL).mock(
        return_value=httpx.Response(200, json=MOCK_COLUMNAR_RESPONSE)
    )

    await query_interaction_metrics(
        metric_type="latency",
        interaction_name="ContestJoin",
        filters='{"platform": "Android"}',
    )

    import json
    body = json.loads(route.calls[0].request.read())
    platform_filters = [f for f in body["filters"] if f["field"] == "Platform"]
    assert len(platform_filters) == 1


@respx.mock
@freeze_time("2026-03-09T12:00:00Z")
@pytest.mark.asyncio
async def test_metrics_with_invalid_json_filters():
    """Metrics tool with malformed JSON filters returns error."""
    from pulse_ai.tools.analytics.query_interaction_metrics import query_interaction_metrics

    result = await query_interaction_metrics(
        metric_type="latency",
        interaction_name="ContestJoin",
        filters="not valid json",
    )

    assert result["status"] == "error"
    assert "filter" in result["message"].lower() or "json" in result["message"].lower()


# ===================================================================
# query_interaction_sessions (Tool 7)
# ===================================================================


@respx.mock
@freeze_time("2026-03-09T12:00:00Z")
@pytest.mark.asyncio
async def test_sessions_returns_session_list():
    """Sessions tool scope=sessions returns session rows."""
    from pulse_ai.tools.analytics.query_interaction_sessions import query_interaction_sessions

    respx.post(DATA_QUERY_URL).mock(
        return_value=httpx.Response(200, json={
            "data": {
                "fields": ["timestamp", "duration", "trace_id"],
                "rows": [["2026-03-09T10:00:00Z", "1500", "abc123"]],
            },
            "error": None,
        })
    )

    result = await query_interaction_sessions(scope="sessions", interaction_name="ContestJoin")

    assert result["status"] == "success"
    assert len(result["data"]) == 1
    assert result["data"][0]["trace_id"] == "abc123"


@respx.mock
@freeze_time("2026-03-09T12:00:00Z")
@pytest.mark.asyncio
async def test_sessions_stats_returns_aggregates():
    """Sessions tool scope=stats returns aggregate counts."""
    from pulse_ai.tools.analytics.query_interaction_sessions import query_interaction_sessions

    respx.post(DATA_QUERY_URL).mock(
        return_value=httpx.Response(200, json={
            "data": {
                "fields": ["total_sessions", "success_count", "error_count"],
                "rows": [["500", "450", "50"]],
            },
            "error": None,
        })
    )

    result = await query_interaction_sessions(scope="stats", interaction_name="ContestJoin")

    assert result["status"] == "success"
    assert result["data"][0]["total_sessions"] == 500


@respx.mock
@freeze_time("2026-03-09T12:00:00Z")
@pytest.mark.asyncio
async def test_sessions_invalid_scope_returns_error():
    """Sessions tool with invalid scope returns error."""
    from pulse_ai.tools.analytics.query_interaction_sessions import query_interaction_sessions

    result = await query_interaction_sessions(scope="invalid", interaction_name="ContestJoin")

    assert result["status"] == "error"
    assert "scope" in result["message"].lower()


# ===================================================================
# breakdown_interaction (Tool 8)
# ===================================================================


@respx.mock
@freeze_time("2026-03-09T12:00:00Z")
@pytest.mark.asyncio
async def test_breakdown_device_returns_data():
    """Breakdown tool with device dimension returns grouped data."""
    from pulse_ai.tools.analytics.breakdown_interaction import breakdown_interaction

    respx.post(DATA_QUERY_URL).mock(
        return_value=httpx.Response(200, json={
            "data": {
                "fields": ["frozen_frame", "anr", "crash", "deviceModel"],
                "rows": [
                    ["10", "2", "1", "Pixel 7"],
                    ["5", "0", "0", "Samsung S24"],
                ],
            },
            "error": None,
        })
    )

    result = await breakdown_interaction(dimension="device", interaction_name="ContestJoin")

    assert result["status"] == "success"
    assert len(result["data"]) == 2
    assert result["data"][0]["deviceModel"] == "Pixel 7"


@respx.mock
@freeze_time("2026-03-09T12:00:00Z")
@pytest.mark.asyncio
async def test_breakdown_invalid_dimension_returns_error():
    """Breakdown tool with invalid dimension returns error."""
    from pulse_ai.tools.analytics.breakdown_interaction import breakdown_interaction

    result = await breakdown_interaction(dimension="invalid", interaction_name="ContestJoin")

    assert result["status"] == "error"
    assert "dimension" in result["message"].lower()


@respx.mock
@freeze_time("2026-03-09T12:00:00Z")
@pytest.mark.asyncio
async def test_breakdown_sends_correct_groupby():
    """Breakdown tool sends correct groupBy for the dimension."""
    from pulse_ai.tools.analytics.breakdown_interaction import breakdown_interaction

    route = respx.post(DATA_QUERY_URL).mock(
        return_value=httpx.Response(200, json={
            "data": {"fields": ["region"], "rows": [["Maharashtra"]]},
            "error": None,
        })
    )

    await breakdown_interaction(dimension="region", interaction_name="ContestJoin")

    import json
    body = json.loads(route.calls[0].request.read())
    assert "region" in body["groupBy"]


@respx.mock
@freeze_time("2026-03-09T12:00:00Z")
@pytest.mark.asyncio
async def test_breakdown_cross_dimensional_filter():
    """Breakdown with cross-dimensional filter (e.g. device + platform filter)."""
    from pulse_ai.tools.analytics.breakdown_interaction import breakdown_interaction

    route = respx.post(DATA_QUERY_URL).mock(
        return_value=httpx.Response(200, json={
            "data": {
                "fields": ["frozen_frame", "anr", "crash", "deviceModel"],
                "rows": [["3", "0", "0", "Pixel 7"]],
            },
            "error": None,
        })
    )

    result = await breakdown_interaction(
        dimension="device",
        interaction_name="ContestJoin",
        time_range="last_7d",
        filters='{"platform": "Android"}',
    )

    assert result["status"] == "success"
    body = json.loads(route.calls[0].request.read())
    # groupBy should be device alias, not platform
    assert "deviceModel" in body["groupBy"]
    # Filters should include Platform=Android
    platform_filters = [f for f in body["filters"] if f.get("field") == "Platform"]
    assert len(platform_filters) == 1
    assert platform_filters[0]["value"] == ["Android"]


@respx.mock
@freeze_time("2026-03-09T12:00:00Z")
@pytest.mark.asyncio
async def test_breakdown_same_dimension_as_filter_warns():
    """Breakdown with filter matching the same dimension produces valid but redundant query.

    dimension="platform" + filters='{"platform":"Android"}' is technically valid SQL
    but semantically wrong — the user likely wanted comparison, not filtering.
    This test documents the current (permissive) behavior.
    """
    from pulse_ai.tools.analytics.breakdown_interaction import breakdown_interaction

    route = respx.post(DATA_QUERY_URL).mock(
        return_value=httpx.Response(200, json={
            "data": {
                "fields": ["error_count", "poor", "platform"],
                "rows": [["5", "2", "Android"]],  # Only 1 row instead of 2
            },
            "error": None,
        })
    )

    result = await breakdown_interaction(
        dimension="platform",
        interaction_name="PaymentCheckout",
        time_range="last_7d",
        filters='{"platform": "Android"}',
    )

    # Currently this succeeds but returns only 1 platform (redundant filter)
    assert result["status"] == "success"
    body = json.loads(route.calls[0].request.read())
    # Both groupBy and filter target Platform — redundant but valid
    assert "platform" in body["groupBy"]
    platform_filters = [f for f in body["filters"] if f.get("field") == "Platform"]
    assert len(platform_filters) == 1


@respx.mock
@freeze_time("2026-03-09T12:00:00Z")
@pytest.mark.asyncio
async def test_breakdown_platform_no_filters_returns_all_platforms():
    """Breakdown with dimension=platform and NO filters returns all platforms."""
    from pulse_ai.tools.analytics.breakdown_interaction import breakdown_interaction

    route = respx.post(DATA_QUERY_URL).mock(
        return_value=httpx.Response(200, json={
            "data": {
                "fields": ["error_count", "poor", "platform"],
                "rows": [
                    ["30", "8", "Android"],
                    ["15", "4", "iOS"],
                ],
            },
            "error": None,
        })
    )

    result = await breakdown_interaction(
        dimension="platform",
        interaction_name="PaymentCheckout",
        time_range="last_7d",
    )

    assert result["status"] == "success"
    assert len(result["data"]) == 2
    body = json.loads(route.calls[0].request.read())
    assert "platform" in body["groupBy"]
    # No user-level platform filter should be present
    platform_filters = [f for f in body["filters"] if f.get("field") == "Platform"]
    assert len(platform_filters) == 0

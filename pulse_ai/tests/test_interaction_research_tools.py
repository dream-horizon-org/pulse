"""Tests for Interaction Research agent tools (issue 03)."""

import json

import httpx
import pytest
import respx
from freezegun import freeze_time

from pulse_ai.agents.interaction_research.tools import INTERACTION_RESEARCH_TOOL_NAMES
from pulse_ai.schemas.root_cause import RootCausePayloadSchema

BASE = "http://localhost:8080"
MOCK_TABULAR = {
    "baseline": {"users": 100, "poor_user_pct": 5.0, "error_rate": 2.0},
    "segments": [
        {
            "label": "Android · 4G",
            "metrics": {"volume": 40, "poor_user_pct": 18.0, "error_rate": 8.0},
            "deltas": {"poor_user_pct": 13.0, "error_rate": 6.0},
            "serverRank": 1,
        },
    ],
    "everythingGood": False,
}

DATA_QUERY_URL = f"{BASE}/v1/interactions/performance-metric/distribution"
INTERACTION_DETAIL_URL = f"{BASE}/v1/interactions/PaymentGateway"
ROOT_CAUSE_URL = f"{BASE}/v1/interactions/PaymentGateway/root-cause?date=2026-05-09"


@respx.mock
@freeze_time("2026-05-09T12:00:00Z")
@pytest.mark.asyncio
async def test_root_cause_tool_uses_get_only_not_async_rca_job(pulse_tool_context):
    """Tabular RCA must hit GET /root-cause — no ai-rca peek/post."""
    from pulse_ai.agents.interaction_research.tools.fetch_interaction_root_cause_segments import (
        fetch_interaction_root_cause_segments,
    )

    peek = respx.get(f"{BASE}/v1/ai-rca/report").mock(
        return_value=httpx.Response(404)
    )
    post = respx.post(f"{BASE}/v1/ai/rca/report").mock(
        return_value=httpx.Response(500)
    )
    root = respx.get(ROOT_CAUSE_URL).mock(
        return_value=httpx.Response(200, json={"data": MOCK_TABULAR})
    )

    result = await fetch_interaction_root_cause_segments(
        interaction_name="PaymentGateway",
        date="2026-05-09",
        tool_context=pulse_tool_context,
    )

    assert result["status"] == "success"
    assert result["data"]["baseline"]["users"] == 100
    assert root.called
    assert not peek.called
    assert not post.called
    assert result.get("segment_highlights") is not None


@respx.mock
@freeze_time("2026-05-09T12:00:00Z")
@pytest.mark.asyncio
async def test_root_cause_tool_forwards_auth_headers(pulse_tool_context):
    from pulse_ai.agents.interaction_research.tools.fetch_interaction_root_cause_segments import (
        fetch_interaction_root_cause_segments,
    )

    route = respx.get(ROOT_CAUSE_URL).mock(
        return_value=httpx.Response(200, json={"data": MOCK_TABULAR})
    )

    await fetch_interaction_root_cause_segments(
        interaction_name="PaymentGateway",
        tool_context=pulse_tool_context,
    )

    assert route.called
    req = route.calls[0].request
    assert req.headers["Authorization"] == "Bearer test-access-token"
    assert req.headers["X-Project-ID"] == "test-project-id"


@pytest.mark.asyncio
async def test_root_cause_tool_requires_interaction_name(pulse_tool_context):
    from pulse_ai.agents.interaction_research.tools.fetch_interaction_root_cause_segments import (
        fetch_interaction_root_cause_segments,
    )

    result = await fetch_interaction_root_cause_segments(
        interaction_name="  ",
        tool_context=pulse_tool_context,
    )
    assert result["status"] == "error"


@respx.mock
@freeze_time("2026-05-09T12:00:00Z")
@pytest.mark.asyncio
async def test_fetch_interaction_config_detail_scope(pulse_tool_context):
    from pulse_ai.agents.interaction_research.tools.fetch_interaction_config import (
        fetch_interaction_config,
    )

    route = respx.get(INTERACTION_DETAIL_URL).mock(
        return_value=httpx.Response(
            200,
            json={"data": {"name": "PaymentGateway", "markerEvents": ["pay_start"]}},
        )
    )

    result = await fetch_interaction_config(
        interaction_name="PaymentGateway",
        tool_context=pulse_tool_context,
    )

    assert result["status"] == "success"
    assert route.called
    assert result["data"]["name"] == "PaymentGateway"


@respx.mock
@freeze_time("2026-05-09T12:00:00Z")
@pytest.mark.asyncio
async def test_fetch_interaction_metrics_composite(pulse_tool_context):
    from pulse_ai.agents.interaction_research.tools.fetch_interaction_metrics import (
        fetch_interaction_metrics,
    )

    route = respx.post(DATA_QUERY_URL).mock(
        return_value=httpx.Response(
            200,
            json={
                "data": {
                    "fields": ["apdex", "success_count", "error_count"],
                    "rows": [["0.82", "90", "10"]],
                }
            },
        )
    )

    result = await fetch_interaction_metrics(
        interaction_name="PaymentGateway",
        tool_context=pulse_tool_context,
    )

    assert result["status"] == "success"
    assert route.called
    body = json.loads(route.calls[0].request.content)
    assert any("apdex" in str(s).lower() for s in body.get("select", []))


@respx.mock
@pytest.mark.asyncio
async def test_list_journeys_forwards_project_header(pulse_tool_context):
    from pulse_ai.agents.interaction_research.tools.list_journeys import list_journeys

    route = respx.get(f"{BASE}/v1/journeys").mock(
        return_value=httpx.Response(200, json={"data": {"journeys": []}})
    )

    await list_journeys(tool_context=pulse_tool_context)

    assert route.called
    assert route.calls[0].request.headers["X-Project-ID"] == "test-project-id"


@respx.mock
@pytest.mark.asyncio
async def test_search_event_catalog_params(pulse_tool_context):
    from pulse_ai.agents.interaction_research.tools.search_event_catalog import (
        search_event_catalog,
    )

    route = respx.get(f"{BASE}/v1/event-definitions").mock(
        return_value=httpx.Response(200, json={"data": {"items": []}})
    )

    await search_event_catalog(search="pay_", limit=5, tool_context=pulse_tool_context)

    assert route.called
    assert "search=pay_" in str(route.calls[0].request.url)
    assert "limit=5" in str(route.calls[0].request.url)


@respx.mock
@freeze_time("2026-05-09T12:00:00Z")
@pytest.mark.asyncio
async def test_fetch_problematic_interaction_spans_error_filter(pulse_tool_context):
    from pulse_ai.agents.interaction_research.tools.fetch_problematic_interaction_spans import (
        fetch_problematic_interaction_spans,
    )

    route = respx.post(DATA_QUERY_URL).mock(
        return_value=httpx.Response(
            200,
            json={
                "data": {
                    "fields": ["sessionid", "traceid", "duration", "status_code"],
                    "rows": [["ess_001", "trace-1", "500000000", "Error"]],
                }
            },
        )
    )

    result = await fetch_problematic_interaction_spans(
        interaction_name="add_to_cart",
        span_kind="error",
        tool_context=pulse_tool_context,
    )

    assert result["status"] == "success"
    assert result["count"] == 1
    assert result["data"][0]["session_id"] == "ess_001"
    assert route.called
    body = json.loads(route.calls[0].request.content)
    additional = [
        f for f in body.get("filters", []) if f.get("operator") == "ADDITIONAL"
    ]
    assert any("StatusCode = 'Error'" in str(f.get("value")) for f in additional)


@respx.mock
@freeze_time("2026-05-09T12:00:00Z")
@pytest.mark.asyncio
async def test_fetch_problematic_interaction_spans_poor_filter(pulse_tool_context):
    from pulse_ai.agents.interaction_research.tools.fetch_problematic_interaction_spans import (
        fetch_problematic_interaction_spans,
    )

    route = respx.post(DATA_QUERY_URL).mock(
        return_value=httpx.Response(
            200,
            json={
                "data": {
                    "fields": ["sessionid", "traceid", "duration", "user_category"],
                    "rows": [["ess_002", "trace-2", "900000000", "Poor"]],
                }
            },
        )
    )

    result = await fetch_problematic_interaction_spans(
        interaction_name="add_to_cart",
        span_kind="poor",
        tool_context=pulse_tool_context,
    )

    assert result["status"] == "success"
    assert result["data"][0]["user_category"] == "Poor"
    body = json.loads(route.calls[0].request.content)
    additional = [
        f for f in body.get("filters", []) if f.get("operator") == "ADDITIONAL"
    ]
    assert any("user_category" in str(f.get("value")) for f in additional)
    assert route.called


@respx.mock
@freeze_time("2026-05-09T12:00:00Z")
@pytest.mark.asyncio
async def test_fetch_session_trace_snapshot_logs(pulse_tool_context):
    from pulse_ai.agents.interaction_research.tools.fetch_session_trace_snapshot import (
        fetch_session_trace_snapshot,
    )

    route = respx.post(DATA_QUERY_URL).mock(
        return_value=httpx.Response(
            200,
            json={
                "data": {
                    "fields": ["trace_id", "timestamp", "body", "pulse_type"],
                    "rows": [["t1", "2026-05-09T12:00:00Z", "view_home", "custom_event"]],
                }
            },
        )
    )

    result = await fetch_session_trace_snapshot(
        session_id="ess_001",
        data_type="logs",
        tool_context=pulse_tool_context,
    )

    assert result["status"] == "success"
    assert result["session_id"] == "ess_001"
    assert result["data"][0]["body"] == "view_home"
    body = json.loads(route.calls[0].request.content)
    assert body["dataType"] == "LOGS"
    session_filters = [f for f in body.get("filters", []) if f.get("field") == "SessionId"]
    assert session_filters[0]["value"] == ["ess_001"]


@respx.mock
@freeze_time("2026-05-09T12:00:00Z")
@pytest.mark.asyncio
async def test_fetch_interaction_metric_trends_time_bucket(pulse_tool_context):
    from pulse_ai.agents.interaction_research.tools.fetch_interaction_metric_trends import (
        fetch_interaction_metric_trends,
    )

    route = respx.post(DATA_QUERY_URL).mock(
        return_value=httpx.Response(
            200,
            json={
                "data": {
                    "fields": ["t1", "apdex", "error_count"],
                    "rows": [["2026-05-09T08:00Z", "0.78", "9"]],
                }
            },
        )
    )

    result = await fetch_interaction_metric_trends(
        interaction_name="add_to_cart",
        tool_context=pulse_tool_context,
    )

    assert result["status"] == "success"
    assert result["count"] == 1
    body = json.loads(route.calls[0].request.content)
    assert any(s.get("function") == "TIME_BUCKET" for s in body.get("select", []))
    assert body.get("groupBy") == ["t1"]


@respx.mock
@freeze_time("2026-05-09T12:00:00Z")
@pytest.mark.asyncio
async def test_fetch_interaction_latency_percentiles_includes_p99(pulse_tool_context):
    from pulse_ai.agents.interaction_research.tools.fetch_interaction_latency_percentiles import (
        fetch_interaction_latency_percentiles,
    )

    route = respx.post(DATA_QUERY_URL).mock(
        return_value=httpx.Response(
            200,
            json={
                "data": {
                    "fields": ["p50", "p95", "p99"],
                    "rows": [["460.29", "1500.0", "1603.62"]],
                }
            },
        )
    )

    result = await fetch_interaction_latency_percentiles(
        interaction_name="add_to_cart",
        tool_context=pulse_tool_context,
    )

    assert result["status"] == "success"
    assert result["data"]["p99"] == 1603.62
    body = json.loads(route.calls[0].request.content)
    functions = [s.get("function") for s in body.get("select", [])]
    assert "DURATION_P99" in functions


@respx.mock
@freeze_time("2026-05-09T12:00:00Z")
@pytest.mark.asyncio
async def test_breakdown_interaction_by_dimension_network(pulse_tool_context):
    from pulse_ai.agents.interaction_research.tools.breakdown_interaction_by_dimension import (
        breakdown_interaction_by_dimension,
    )

    route = respx.post(DATA_QUERY_URL).mock(
        return_value=httpx.Response(
            200,
            json={
                "data": {
                    "fields": ["success_count", "error_count", "network"],
                    "rows": [["448", "7", "Vi"]],
                }
            },
        )
    )

    result = await breakdown_interaction_by_dimension(
        interaction_name="add_to_cart",
        dimension="network",
        tool_context=pulse_tool_context,
    )

    assert result["status"] == "success"
    assert result["dimension"] == "network"
    assert result["data"][0]["network"] == "Vi"
    body = json.loads(route.calls[0].request.content)
    assert body.get("groupBy") == ["network"]


def test_documented_tool_names_match_exports():
    assert len(INTERACTION_RESEARCH_TOOL_NAMES) == 13
    assert "fetch_interaction_metric_trends" in INTERACTION_RESEARCH_TOOL_NAMES
    assert "fetch_interaction_latency_percentiles" in INTERACTION_RESEARCH_TOOL_NAMES
    assert "breakdown_interaction_by_dimension" in INTERACTION_RESEARCH_TOOL_NAMES


class TestEnrichInteractionResearch:
    def test_enrich_applies_mapper_from_rca_payload(self):
        from pulse_ai.agents.interaction_research.enrich import enrich_interaction_research
        from pulse_ai.schemas.interaction_research_v1 import InteractionResearchV1
        from pulse_ai.schemas.interaction_report_v1 import ReportingPeriod
        from datetime import date

        rca = RootCausePayloadSchema.model_validate(MOCK_TABULAR)
        research = InteractionResearchV1(
            project_id="p1",
            interaction_name="PaymentGateway",
            reporting_period=ReportingPeriod(start=date(2026, 5, 1), end=date(2026, 5, 7)),
            rca_payload=rca.model_dump(mode="json"),
            metrics_payload={
                "data": [
                    {
                        "apdex": 0.82,
                        "success_count": 90,
                        "error_count": 10,
                        "user_poor": 20,
                        "user_excellent": 50,
                        "user_good": 20,
                        "user_avg": 10,
                    }
                ]
            },
        )
        enriched = enrich_interaction_research(research)
        assert enriched.segment_highlights is not None
        assert enriched.health_rating in ("red", "amber", "green")
        assert enriched.paradox_kpi_hint is not None

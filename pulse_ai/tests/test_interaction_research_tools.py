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


def test_documented_tool_names_match_exports():
    assert len(INTERACTION_RESEARCH_TOOL_NAMES) == 9
    assert "fetch_interaction_root_cause_segments" in INTERACTION_RESEARCH_TOOL_NAMES


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

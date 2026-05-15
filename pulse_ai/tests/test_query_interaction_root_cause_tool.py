"""Tests for query_interaction_root_cause EM tool."""

from unittest.mock import MagicMock

import httpx
import pytest
import respx
from freezegun import freeze_time

MOCK_TABULAR = {
    "baseline": {"users": 100},
    "segments": [
        {
            "label": "Android",
            "metrics": {"risk_ratio": 0.42},
            "deltas": {"risk_ratio": 0.05},
        },
    ],
}


def _completed_peek_json(tabular: dict) -> dict:
    return {
        "data": {
            "status": "COMPLETED",
            "report": {
                "structured": {
                    "version": 1,
                    "executive_summary": "x",
                    "segments": [],
                    "recommendations": [],
                },
                "rootCausePayload": tabular,
            },
        },
    }


BASE = "http://localhost:8080"
PEEK_URL = (
    f"{BASE}/v1/ai-rca/report?"
    "rcaType=INTERACTION&entityKey=ContestJoin&date=2026-03-09"
)
POST_RCA_URL = f"{BASE}/v1/ai/rca/report"
JOB_URL = f"{BASE}/v1/ai-rca/job/rca-job-unit"


@respx.mock
@freeze_time("2026-03-09T12:00:00Z")
@pytest.mark.asyncio
async def test_root_cause_peek_completed_then_tabular(pulse_tool_context, monkeypatch):
    monkeypatch.setattr(
        "pulse_ai.root_cause_payload_fetch.RCA_JOB_POLL_INTERVAL_SEC",
        0.01,
    )
    from pulse_ai.agents.em.tools.analytics.query_interaction_root_cause import (
        query_interaction_root_cause,
    )

    respx.get(PEEK_URL).mock(
        return_value=httpx.Response(
            200,
            json=_completed_peek_json(MOCK_TABULAR),
        ),
    )

    result = await query_interaction_root_cause(
        interaction_name="ContestJoin",
        tool_context=pulse_tool_context,
    )

    assert result["status"] == "success"
    assert result["data"]["baseline"]["users"] == 100
    assert len(result["data"]["segments"]) == 1
    assert result["data"]["segments"][0]["label"] == "Android"


@respx.mock
@freeze_time("2026-03-09T12:00:00Z")
@pytest.mark.asyncio
async def test_root_cause_post_202_poll_then_tabular(pulse_tool_context, monkeypatch):
    monkeypatch.setattr(
        "pulse_ai.root_cause_payload_fetch.RCA_JOB_POLL_INTERVAL_SEC",
        0.01,
    )
    from pulse_ai.agents.em.tools.analytics.query_interaction_root_cause import (
        query_interaction_root_cause,
    )

    respx.get(PEEK_URL).mock(return_value=httpx.Response(404))
    respx.post(POST_RCA_URL).mock(
        return_value=httpx.Response(
            202,
            json={
                "data": {
                    "jobId": "rca-job-unit",
                    "status": "PENDING",
                    "pollUrl": "/v1/ai-rca/job/rca-job-unit",
                },
            },
        ),
    )
    respx.get(JOB_URL).mock(
        return_value=httpx.Response(
            200,
            json=_completed_peek_json(MOCK_TABULAR),
        ),
    )

    result = await query_interaction_root_cause(
        interaction_name="ContestJoin",
        tool_context=pulse_tool_context,
    )

    assert result["status"] == "success"
    assert result["data"]["segments"][0]["label"] == "Android"


@respx.mock
@freeze_time("2026-03-09T12:00:00Z")
@pytest.mark.asyncio
async def test_root_cause_sends_date_param(pulse_tool_context, monkeypatch):
    monkeypatch.setattr(
        "pulse_ai.root_cause_payload_fetch.RCA_JOB_POLL_INTERVAL_SEC",
        0.01,
    )
    from pulse_ai.agents.em.tools.analytics.query_interaction_root_cause import (
        query_interaction_root_cause,
    )

    peek = (
        "http://localhost:8080/v1/ai-rca/report?"
        "rcaType=INTERACTION&entityKey=ContestJoin&date=2026-01-15"
    )
    route = respx.get(peek).mock(
        return_value=httpx.Response(
            200,
            json=_completed_peek_json(MOCK_TABULAR),
        ),
    )

    await query_interaction_root_cause(
        interaction_name="ContestJoin",
        date="2026-01-15",
        tool_context=pulse_tool_context,
    )

    assert route.called


@respx.mock
@freeze_time("2026-03-09T12:00:00Z")
@pytest.mark.asyncio
async def test_root_cause_backend_error_on_peek(pulse_tool_context):
    from pulse_ai.agents.em.tools.analytics.query_interaction_root_cause import (
        query_interaction_root_cause,
    )

    respx.get(PEEK_URL).mock(
        return_value=httpx.Response(
            500,
            json={"data": None, "error": {"code": "BE5001", "message": "upstream failed"}},
        ),
    )

    result = await query_interaction_root_cause(
        interaction_name="ContestJoin",
        tool_context=pulse_tool_context,
    )

    assert result["status"] == "error"
    assert result["code"] == 500
    assert "upstream failed" in result["message"]


@pytest.mark.asyncio
async def test_root_cause_empty_interaction_name(pulse_tool_context):
    from pulse_ai.agents.em.tools.analytics.query_interaction_root_cause import (
        query_interaction_root_cause,
    )

    result = await query_interaction_root_cause(
        interaction_name="   ",
        tool_context=pulse_tool_context,
    )

    assert result["status"] == "error"
    assert "required" in result["message"].lower()


@pytest.mark.asyncio
async def test_root_cause_missing_session_returns_auth_error():
    from pulse_ai.agents.em.tools.analytics.query_interaction_root_cause import (
        query_interaction_root_cause,
    )

    ctx = MagicMock()
    ctx.state = {}

    result = await query_interaction_root_cause(
        interaction_name="ContestJoin",
        tool_context=ctx,
    )

    assert result["status"] == "error"

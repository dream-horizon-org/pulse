"""Tests for query_interaction_root_cause EM tool."""

from unittest.mock import MagicMock

import httpx
import pytest
import respx
from freezegun import freeze_time

MOCK_ROOT_CAUSE_WRAPPED = {
    "data": {
        "baseline": {"users": 100},
        "segments": [
            {
                "label": "Android",
                "metrics": {"risk_ratio": 0.42},
                "deltas": {"risk_ratio": 0.05},
            },
        ],
    },
}

ROOT_CAUSE_URL = (
    "http://localhost:8080/v1/interactions/ContestJoin/root-cause?date=2026-03-09"
)


@respx.mock
@freeze_time("2026-03-09T12:00:00Z")
@pytest.mark.asyncio
async def test_root_cause_returns_validated_data(pulse_tool_context):
    from pulse_ai.agents.em.tools.analytics.query_interaction_root_cause import (
        query_interaction_root_cause,
    )

    respx.get(ROOT_CAUSE_URL).mock(
        return_value=httpx.Response(200, json=MOCK_ROOT_CAUSE_WRAPPED),
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
async def test_root_cause_sends_date_param(pulse_tool_context):
    from pulse_ai.agents.em.tools.analytics.query_interaction_root_cause import (
        query_interaction_root_cause,
    )

    url = "http://localhost:8080/v1/interactions/ContestJoin/root-cause?date=2026-01-15"
    route = respx.get(url).mock(
        return_value=httpx.Response(200, json=MOCK_ROOT_CAUSE_WRAPPED),
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
async def test_root_cause_backend_error(pulse_tool_context):
    from pulse_ai.agents.em.tools.analytics.query_interaction_root_cause import (
        query_interaction_root_cause,
    )

    respx.get(ROOT_CAUSE_URL).mock(
        return_value=httpx.Response(500, json={"message": "upstream failed"}),
    )

    result = await query_interaction_root_cause(
        interaction_name="ContestJoin",
        tool_context=pulse_tool_context,
    )

    assert result["status"] == "error"
    assert result["code"] == 500
    assert "Failed to fetch root-cause data" in result["message"]


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

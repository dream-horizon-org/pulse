from __future__ import annotations

import json
from unittest.mock import MagicMock

import pytest
from pydantic import ValidationError

from pulse_ai.agents.rca.report_event_parts import extract_structured_rca_report_from_event_parts
from pulse_ai.agents.rca.tools.submit_rca_structured_report import submit_rca_structured_report
from pulse_ai.schemas.rca_structured_v1 import RcaStructuredReportV1


def _minimal_valid_structured_dict() -> dict:
    return {
        "version": 1,
        "executive_summary": "Errors concentrate on Android 4.0.0.",
        "segments": [
            {
                "rank": 1,
                "title": "Android · v4.0.0",
                "metrics": [
                    {
                        "metric_id": "volume",
                        "metric_label": "Volume",
                        "value_display": "1,200",
                        "baseline_display": "10,000",
                        "delta_display": "12% of total",
                        "value_number": 1200.0,
                        "baseline_number": 10000.0,
                    },
                    {
                        "metric_id": "error_rate",
                        "metric_label": "Error Rate",
                        "value_display": "15.13%",
                        "baseline_display": "2.10%",
                        "delta_display": "+620%",
                        "value_number": 0.1513,
                        "baseline_number": 0.021,
                    },
                ],
                "impact": "Primary driver of poor experience in this window.",
            }
        ],
        "recommendations": ["Investigate Android 4.0.0 release: rollback or hotfix."],
    }


def test_rca_structured_report_v1_model_validates() -> None:
    parsed = RcaStructuredReportV1.model_validate(_minimal_valid_structured_dict())
    assert parsed.version == 1
    assert len(parsed.segments) == 1
    assert parsed.segments[0].metrics[0].metric_id == "volume"


def test_rca_structured_report_v1_rejects_bad_metric_id() -> None:
    bad = _minimal_valid_structured_dict()
    bad["segments"][0]["metrics"][0]["metric_id"] = "not_a_metric"
    with pytest.raises(ValidationError):
        RcaStructuredReportV1.model_validate(bad)


@pytest.mark.asyncio
async def test_submit_rca_structured_report_success() -> None:
    payload = json.dumps(_minimal_valid_structured_dict())
    result = await submit_rca_structured_report(payload, tool_context=None)
    assert result["success"] is True
    assert result["structured"]["version"] == 1
    assert result["structured"]["segments"][0]["title"] == "Android · v4.0.0"


@pytest.mark.asyncio
async def test_submit_rca_structured_report_invalid_json() -> None:
    result = await submit_rca_structured_report("{not json", tool_context=None)
    assert result["success"] is False
    assert "error" in result


@pytest.mark.asyncio
async def test_submit_rca_structured_report_validation_errors() -> None:
    bad = _minimal_valid_structured_dict()
    bad["segments"][0]["metrics"][0]["metric_id"] = "typo_rate"
    result = await submit_rca_structured_report(json.dumps(bad), tool_context=None)
    assert result["success"] is False
    assert "errors" in result


def test_extract_from_event_parts_parses_structured() -> None:
    structured = _minimal_valid_structured_dict()
    part = MagicMock()
    part.function_response = MagicMock()
    part.function_response.response = {"success": True, "structured": structured}

    parsed = extract_structured_rca_report_from_event_parts([part])
    assert parsed is not None
    assert parsed.executive_summary == structured["executive_summary"]


def test_extract_from_event_parts_ignores_invalid_structured() -> None:
    bad = _minimal_valid_structured_dict()
    bad["segments"][0]["metrics"][0]["metric_id"] = "bad"
    part = MagicMock()
    part.function_response = MagicMock()
    part.function_response.response = {"success": True, "structured": bad}

    parsed = extract_structured_rca_report_from_event_parts([part])
    assert parsed is None

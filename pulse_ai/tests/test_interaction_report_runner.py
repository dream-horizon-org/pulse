"""Unit tests for interaction_report_runner post-processing and helpers."""

from __future__ import annotations

from datetime import date

import pytest

from pulse_ai.schemas.interaction_report_helpers import ParadoxKpiHint
from pulse_ai.schemas.interaction_report_v1 import (
    HealthVerdict,
    KpiSnapshot,
    ReportingPeriod,
)
from pulse_ai.schemas.interaction_research_v1 import InteractionResearchV1
from pulse_ai.server.interaction_report_runner import (
    MAX_SCHEMA_RETRIES,
    enforce_paradox_primary_kpi,
    enforce_verdict_rating,
    extract_metric_triple,
    postprocess_interaction_report,
    sanitize_interaction_report,
)
from pulse_ai.tests.test_interaction_report_schema import _minimal_report


def _research(
    *,
    metrics_payload: dict | None = None,
    paradox: bool = False,
) -> InteractionResearchV1:
    hint = ParadoxKpiHint() if paradox else None
    payload = metrics_payload
    if payload is None and paradox:
        payload = {
            "data": [
                {
                    "apdex": 0.75,
                    "success_count": 950,
                    "error_count": 50,
                    "user_excellent": 100,
                    "user_good": 100,
                    "user_avg": 100,
                    "user_poor": 100,
                },
            ],
        }
    return InteractionResearchV1(
        project_id="proj-1",
        interaction_name="PayFlow",
        reporting_period=ReportingPeriod(start=date(2026, 5, 1), end=date(2026, 5, 7)),
        metrics_payload=payload,
        paradox_kpi_hint=hint,
        health_rating="amber",
    )


def test_extract_metric_triple_from_composite_payload():
    payload = {
        "data": [
            {
                "apdex": 0.8,
                "success_count": 900,
                "error_count": 100,
                "user_poor": 50,
                "user_excellent": 200,
                "user_good": 200,
                "user_avg": 200,
            },
        ],
    }
    apdex, err, poor = extract_metric_triple(payload)
    assert apdex == 0.8
    assert err == pytest.approx(10.0)
    assert poor == pytest.approx(50 / 650 * 100)


def test_enforce_verdict_rating_overwrites_llm_green_when_metrics_red():
    report = _minimal_report(
        verdict=HealthVerdict(
            primary_kpi=KpiSnapshot(metric="apdex", value=0.4, display="0.40"),
            secondary_kpi=KpiSnapshot(metric="error_rate", value=12.0, display="12%"),
            rating="green",
            summary="Looks fine.",
            poor_user_pct=20.0,
        ),
    )
    research = _research(
        metrics_payload={
            "data": [{"apdex": 0.4, "error_count": 120, "success_count": 880}],
        },
    )
    out = enforce_verdict_rating(report, research)
    assert out.verdict.rating == "red"


def test_enforce_paradox_sets_primary_error_rate():
    report = _minimal_report()
    research = _research(paradox=True)
    out = enforce_paradox_primary_kpi(report, research)
    assert out.verdict.primary_kpi.metric == "error_rate"
    assert out.verdict.secondary_kpi.metric == "apdex"


def test_sanitize_interaction_report_redacts_email_in_summary():
    report = _minimal_report(
        verdict=HealthVerdict(
            primary_kpi=KpiSnapshot(metric="apdex", value=0.72, display="0.72"),
            secondary_kpi=KpiSnapshot(metric="error_rate", value=5.2, display="5.2%"),
            rating="amber",
            summary="Contact pm@corp.com for escalation.",
            poor_user_pct=12.0,
        ),
    )
    out = sanitize_interaction_report(report)
    assert "pm@corp.com" not in out.verdict.summary
    assert "[REDACTED:EMAIL]" in out.verdict.summary


def test_postprocess_applies_paradox_then_rating():
    report = _minimal_report()
    research = _research(paradox=True)
    out = postprocess_interaction_report(report, research)
    assert out.verdict.primary_kpi.metric == "error_rate"
    assert out.verdict.rating in ("red", "amber", "green")


def test_max_schema_retries_is_two():
    assert MAX_SCHEMA_RETRIES == 2

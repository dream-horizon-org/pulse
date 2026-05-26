"""Unit tests for interaction_report_runner post-processing and helpers."""

from __future__ import annotations

from datetime import date
from types import SimpleNamespace
from typing import Any
from unittest.mock import MagicMock

import pytest

from pulse_ai.schemas.interaction_report_helpers import ParadoxKpiHint
from pulse_ai.schemas.interaction_report_v1 import (
    HealthVerdict,
    KpiSnapshot,
    ReportingPeriod,
)
from pulse_ai.schemas.interaction_research_v1 import InteractionResearchV1
from pulse_ai.server.interaction_report_runner import (
    RESEARCH_STATE_KEY,
    REPORT_STATE_KEY,
    InteractionReportRunnerError,
    enforce_paradox_primary_kpi,
    enforce_verdict_rating,
    extract_metric_triple,
    generate_interaction_report,
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


class _MockSessionStore:
    def __init__(self) -> None:
        self.sessions: dict[str, dict[str, Any]] = {}

    async def get_session(
        self,
        *,
        app_name: str,
        user_id: str,
        session_id: str,
    ) -> SimpleNamespace | None:
        state = self.sessions.get(session_id)
        if state is None:
            return None
        return SimpleNamespace(state=state)

    async def delete_session(self, **kwargs: Any) -> None:
        session_id = kwargs.get("session_id")
        if session_id:
            self.sessions.pop(session_id, None)


def _make_pipeline_runner(
    store: _MockSessionStore,
    *,
    research: InteractionResearchV1 | None = None,
    report: dict[str, Any] | None = None,
) -> MagicMock:
    runner = MagicMock()
    runner.app_name = "interaction-report-test"
    runner.session_service = store
    run_call_count = {"n": 0}
    last_pipeline_state: dict[str, Any] = {}

    async def run_async(
        *,
        user_id: str,
        session_id: str,
        new_message: Any,
        state_delta: dict[str, Any] | None = None,
    ):
        run_call_count["n"] += 1
        state = store.sessions.setdefault(session_id, {})
        if state_delta:
            state.update(state_delta)
        if research is not None:
            state[RESEARCH_STATE_KEY] = research.model_dump(mode="json")
        if report is not None:
            state[REPORT_STATE_KEY] = report
        last_pipeline_state.clear()
        last_pipeline_state.update(state)
        if False:  # pragma: no cover — async generator marker
            yield

    runner.run_async = run_async
    runner._run_call_count = run_call_count
    runner._last_pipeline_state = last_pipeline_state
    return runner


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


@pytest.mark.asyncio
async def test_generate_calls_run_async_once() -> None:
    store = _MockSessionStore()
    research = _research()
    report = _minimal_report()
    runner = _make_pipeline_runner(
        store,
        research=research,
        report=report.model_dump(mode="json"),
    )

    await generate_interaction_report(
        runner,
        project_id="proj-1",
        interaction_name="PayFlow",
        period_start=date(2026, 5, 1),
        period_end=date(2026, 5, 7),
    )

    assert runner._run_call_count["n"] == 1


@pytest.mark.asyncio
async def test_generate_session_has_research_and_report_keys() -> None:
    store = _MockSessionStore()
    research = _research()
    report = _minimal_report()
    runner = _make_pipeline_runner(
        store,
        research=research,
        report=report.model_dump(mode="json"),
    )

    await generate_interaction_report(
        runner,
        project_id="proj-1",
        interaction_name="PayFlow",
    )

    state = runner._last_pipeline_state
    assert RESEARCH_STATE_KEY in state
    assert REPORT_STATE_KEY in state


@pytest.mark.asyncio
async def test_generate_fails_on_invalid_research() -> None:
    store = _MockSessionStore()
    runner = _make_pipeline_runner(
        store,
        research=None,
        report=_minimal_report().model_dump(mode="json"),
    )

    with pytest.raises(InteractionReportRunnerError, match="research output missing or invalid"):
        await generate_interaction_report(
            runner,
            project_id="proj-1",
            interaction_name="PayFlow",
        )


@pytest.mark.asyncio
async def test_generate_fails_on_invalid_schema() -> None:
    store = _MockSessionStore()
    runner = _make_pipeline_runner(
        store,
        research=_research(),
        report=None,
    )

    with pytest.raises(InteractionReportRunnerError, match="schema output missing or invalid"):
        await generate_interaction_report(
            runner,
            project_id="proj-1",
            interaction_name="PayFlow",
        )

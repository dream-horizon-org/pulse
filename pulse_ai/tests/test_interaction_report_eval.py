"""Observable-behavior evals for interaction report pipeline (issue 07, phase 1).

Uses recorded tool-response fixtures and mock ADK runners — no live Gemini required.
Asserts schema validity, rating derivation, paradox KPI, segment highlights — not prose.
"""

from __future__ import annotations

import json
from datetime import date
from pathlib import Path
from types import SimpleNamespace
from typing import Any, Callable
from unittest.mock import AsyncMock, MagicMock

import httpx
import pytest
import respx
from freezegun import freeze_time

from pulse_ai.agents.interaction_research.enrich import enrich_interaction_research
from pulse_ai.agents.interaction_research.tools import INTERACTION_RESEARCH_TOOL_NAMES
from pulse_ai.schemas.interaction_report_v1 import (
    InteractionReportV1,
    ReportingPeriod,
    derive_health_rating,
)
from pulse_ai.schemas.interaction_research_v1 import InteractionResearchV1
from pulse_ai.server.interaction_report_runner import (
    RESEARCH_STATE_KEY,
    REPORT_STATE_KEY,
    generate_interaction_report,
    postprocess_interaction_report,
)
from pulse_ai.tests.test_interaction_report_schema import _minimal_report

FIXTURES_DIR = Path(__file__).parent / "fixtures" / "interaction_report"
MANDATORY_TOOLS = frozenset(
    {
        "fetch_interaction_config",
        "fetch_interaction_metrics",
        "fetch_interaction_root_cause_segments",
    },
)
BASE = "http://localhost:8080"


def load_eval_case(name: str) -> dict[str, Any]:
    with open(FIXTURES_DIR / f"{name}.json") as handle:
        return json.load(handle)


def _research_from_recorded_case(case: dict[str, Any]) -> InteractionResearchV1:
    """Simulate Agent 1 output from recorded tool responses (no LLM)."""
    tools = case["tool_responses"]
    config = tools["fetch_interaction_config"]["data"]
    metrics = tools["fetch_interaction_metrics"]["data"]
    rca = tools["fetch_interaction_root_cause_segments"]["data"]
    research = InteractionResearchV1(
        project_id=case["project_id"],
        interaction_name=case["interaction_name"],
        reporting_period=ReportingPeriod(start=date(2026, 5, 1), end=date(2026, 5, 7)),
        interaction_config=config,
        metrics_payload=metrics,
        rca_payload=rca,
        journey_summary="Recorded fixture — no live agent.",
    )
    return enrich_interaction_research(research)


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


def _make_mock_runner(
    store: _MockSessionStore,
    on_run: Callable[..., None],
) -> MagicMock:
    runner = MagicMock()
    runner.app_name = "interaction-report-eval"
    runner.session_service = store

    async def run_async(
        *,
        user_id: str,
        session_id: str,
        new_message: Any,
        state_delta: dict[str, Any] | None = None,
    ):
        state = store.sessions.setdefault(session_id, {})
        if state_delta:
            state.update(state_delta)
        on_run(
            user_id=user_id,
            session_id=session_id,
            new_message=new_message,
            state=state,
        )
        if False:  # pragma: no cover — async generator marker
            yield

    runner.run_async = run_async
    return runner


def _schema_report_from_research(research: InteractionResearchV1) -> InteractionReportV1:
    """Minimal valid report shaped from research metrics (mock Agent 2)."""
    row = (research.metrics_payload or {}).get("data", [{}])[0]
    apdex = float(row.get("apdex", 0.72))
    success = float(row.get("success_count", 90))
    errors = float(row.get("error_count", 10))
    error_rate = 100.0 * errors / (success + errors) if (success + errors) > 0 else 5.0
    poor = float(row.get("user_poor", 0))
    total = sum(float(row.get(k, 0)) for k in ("user_excellent", "user_good", "user_avg", "user_poor"))
    poor_pct = 100.0 * poor / total if total > 0 else 12.0

    primary_metric = "apdex"
    secondary_metric = "error_rate"
    if research.paradox_kpi_hint is not None:
        primary_metric = "error_rate"
        secondary_metric = "apdex"

    report = _minimal_report(
        project_id=research.project_id,
        identity=_minimal_report().identity.model_copy(
            update={"name": research.interaction_name},
        ),
    )
    verdict = report.verdict.model_copy(
        update={
            "primary_kpi": report.verdict.primary_kpi.model_copy(
                update={
                    "metric": primary_metric,
                    "value": error_rate if primary_metric == "error_rate" else apdex,
                },
            ),
            "secondary_kpi": report.verdict.secondary_kpi.model_copy(
                update={
                    "metric": secondary_metric,
                    "value": apdex if secondary_metric == "apdex" else error_rate,
                },
            ),
            "rating": "green",
            "poor_user_pct": poor_pct,
        },
    )
    user_impact = report.user_impact.model_copy(
        update={
            "segment_highlights": research.segment_highlights,
            "error_rate_pct": error_rate,
        },
    )
    return report.model_copy(update={"verdict": verdict, "user_impact": user_impact})


async def _run_recorded_pipeline(case: dict[str, Any]) -> InteractionReportV1:
    research = _research_from_recorded_case(case)
    schema_attempts = {"count": 0}

    def on_research(**kwargs: Any) -> None:
        kwargs["state"][RESEARCH_STATE_KEY] = research.model_dump(mode="json")

    def on_schema(**kwargs: Any) -> None:
        schema_attempts["count"] += 1
        kwargs["state"][REPORT_STATE_KEY] = _schema_report_from_research(research).model_dump(
            mode="json",
        )

    store = _MockSessionStore()
    research_runner = _make_mock_runner(store, on_research)
    schema_runner = _make_mock_runner(store, on_schema)

    report = await generate_interaction_report(
        research_runner,
        schema_runner,
        project_id=case["project_id"],
        interaction_name=case["interaction_name"],
        period_start=date(2026, 5, 1),
        period_end=date(2026, 5, 7),
    )
    assert schema_attempts["count"] == 1, "schema must pass on first attempt for recorded fixture"
    return report


@pytest.mark.parametrize(
    "fixture_name",
    [
        "souled_payment_degraded",
        "souled_payment_everything_good",
        "souled_view_cart_paradox",
    ],
)
@pytest.mark.asyncio
async def test_eval_schema_valid_first_attempt(fixture_name: str) -> None:
    case = load_eval_case(fixture_name)
    report = await _run_recorded_pipeline(case)
    assert isinstance(report, InteractionReportV1)
    assert report.version == 1
    assert report.identity.name == case["interaction_name"]


@pytest.mark.asyncio
async def test_eval_schema_pass_rate_above_ninety_percent() -> None:
    """Ten recorded variants must pass schema on first attempt (>90%)."""
    cases = [load_eval_case("souled_payment_degraded")]
    for i in range(9):
        variant = json.loads(json.dumps(cases[0]))
        variant["case_id"] = f"variant_{i}"
        row = variant["tool_responses"]["fetch_interaction_metrics"]["data"]["data"][0]
        row["apdex"] = round(0.55 + i * 0.03, 2)
        cases.append(variant)

    first_attempt_passes = 0
    for case in cases:
        research = _research_from_recorded_case(case)
        report = _schema_report_from_research(research)
        try:
            InteractionReportV1.model_validate(report.model_dump(mode="json"))
            first_attempt_passes += 1
        except Exception:
            pass

    assert first_attempt_passes >= 9


def test_eval_mandatory_tools_in_recorded_traces() -> None:
    for path in FIXTURES_DIR.glob("*.json"):
        case = json.loads(path.read_text())
        trace = set(case.get("mandatory_tool_trace", []))
        assert MANDATORY_TOOLS.issubset(trace), case["case_id"]
        assert trace.issubset(set(INTERACTION_RESEARCH_TOOL_NAMES))


@respx.mock
@freeze_time("2026-05-09T12:00:00Z")
@pytest.mark.asyncio
async def test_eval_tabular_rca_uses_get_root_cause_not_legacy_job(pulse_tool_context) -> None:
    from pulse_ai.agents.interaction_research.tools.fetch_interaction_root_cause_segments import (
        fetch_interaction_root_cause_segments,
    )

    peek = respx.get(f"{BASE}/v1/ai-rca/report").mock(return_value=httpx.Response(404))
    post = respx.post(f"{BASE}/v1/ai/rca/report").mock(return_value=httpx.Response(500))
    root = respx.get(
        f"{BASE}/v1/interactions/PaymentGatewayHandshakeLatency/root-cause?date=2026-05-09",
    ).mock(
        return_value=httpx.Response(
            200,
            json={"data": load_eval_case("souled_payment_degraded")["tool_responses"]["fetch_interaction_root_cause_segments"]["data"]},
        ),
    )

    result = await fetch_interaction_root_cause_segments(
        interaction_name="PaymentGatewayHandshakeLatency",
        date="2026-05-09",
        tool_context=pulse_tool_context,
    )

    assert result["status"] == "success"
    assert root.called
    assert not peek.called
    assert not post.called


@pytest.mark.asyncio
async def test_eval_everything_good_omits_segment_highlights() -> None:
    case = load_eval_case("souled_payment_everything_good")
    research = _research_from_recorded_case(case)
    assert research.segment_highlights is None
    report = await _run_recorded_pipeline(case)
    assert report.user_impact.segment_highlights is None


@pytest.mark.asyncio
async def test_eval_paradox_sets_primary_kpi_error_rate() -> None:
    case = load_eval_case("souled_view_cart_paradox")
    research = _research_from_recorded_case(case)
    assert research.paradox_kpi_hint is not None
    assert research.paradox_kpi_hint.primary_kpi == "error_rate"

    report = await _run_recorded_pipeline(case)
    assert report.verdict.primary_kpi.metric == "error_rate"
    assert report.verdict.secondary_kpi.metric == "apdex"


@pytest.mark.asyncio
async def test_eval_rating_derived_not_llm_authored() -> None:
    case = load_eval_case("souled_payment_degraded")
    research = _research_from_recorded_case(case)
    llm_report = _schema_report_from_research(research)
    llm_report = llm_report.model_copy(
        update={
            "verdict": llm_report.verdict.model_copy(update={"rating": "green"}),
        },
    )
    processed = postprocess_interaction_report(llm_report, research)
    error_rate_pct = 100.0 * 600 / (11400 + 600)
    poor_user_pct = 100.0 * 3000 / 12000
    expected = derive_health_rating(
        apdex=0.72,
        error_rate_pct=error_rate_pct,
        poor_user_pct=poor_user_pct,
    )
    assert processed.verdict.rating == expected
    assert processed.verdict.rating == "red"
    assert processed.verdict.rating != "green"


@pytest.mark.asyncio
async def test_eval_degraded_case_has_segment_highlights() -> None:
    case = load_eval_case("souled_payment_degraded")
    research = _research_from_recorded_case(case)
    assert research.segment_highlights is not None
    assert len(research.segment_highlights) >= 1
    report = await _run_recorded_pipeline(case)
    assert report.user_impact.segment_highlights is not None

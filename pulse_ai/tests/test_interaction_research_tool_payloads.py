"""Tests for Interaction Research tool payload capture and merge."""

from __future__ import annotations

from datetime import date

from pulse_ai.agents.interaction_research.tool_payload_state import (
    apply_tool_payloads_to_research,
    capture_tool_response,
    format_tool_log_args,
    format_tool_log_response,
)
from pulse_ai.schemas.interaction_research_v1 import (
    InteractionResearchV1,
    research_from_llm_output,
)
from pulse_ai.schemas.interaction_report_v1 import ReportingPeriod


def _minimal_research() -> InteractionResearchV1:
    return InteractionResearchV1(
        project_id="proj-1",
        interaction_name="PayFlow",
        reporting_period=ReportingPeriod(start=date(2026, 5, 1), end=date(2026, 5, 7)),
    )


def test_capture_tool_response_stores_by_tool_name():
    state: dict = {}
    capture_tool_response(
        tool=type("T", (), {"name": "fetch_interaction_metrics"})(),
        tool_response={"status": "success", "data": [{"apdex": 0.9}]},
        state=state,
    )
    assert "fetch_interaction_metrics" in state["interaction_research_tool_payloads"]


def test_capture_skips_error_status():
    state: dict = {}
    capture_tool_response(
        tool=type("T", (), {"name": "fetch_interaction_metrics"})(),
        tool_response={"status": "error", "message": "fail"},
        state=state,
    )
    assert state.get("interaction_research_tool_payloads") is None


def test_apply_tool_payloads_overrides_llm_and_normalizes_rca():
    research = _minimal_research()
    tool_payloads = {
        "fetch_interaction_metrics": {
            "status": "success",
            "data": [{"apdex": 0.5, "success_count": 80, "error_count": 20}],
        },
        "fetch_interaction_root_cause_segments": {
            "status": "success",
            "data": {
                "baseline": {"users": 100, "poor_user_pct": 5.0, "error_rate": 2.0},
                "segments": [],
                "everythingGood": True,
            },
        },
    }
    merged = apply_tool_payloads_to_research(research, tool_payloads)
    assert merged.metrics_payload is not None
    assert merged.rca_payload is not None
    assert "baseline" in merged.rca_payload


def test_apply_tool_payloads_extracts_bad_session_ids_from_problematic_spans():
    research = _minimal_research()
    merged = apply_tool_payloads_to_research(
        research,
        {
            "fetch_problematic_interaction_spans": {
                "status": "success",
                "span_kind": "error",
                "data": [
                    {"session_id": "ess_001", "trace_id": "t1"},
                    {"session_id": "ess_002", "trace_id": "t2"},
                ],
            },
        },
    )
    assert merged.bad_session_ids == ["ess_001", "ess_002"]
    assert merged.problematic_spans_payload is not None


def test_capture_merges_problematic_span_calls():
    state: dict = {}
    tool = type("T", (), {"name": "fetch_problematic_interaction_spans"})()
    capture_tool_response(
        tool=tool,
        tool_response={
            "status": "success",
            "span_kind": "error",
            "data": [{"session_id": "ess_001", "trace_id": "t1"}],
        },
        state=state,
    )
    capture_tool_response(
        tool=tool,
        tool_response={
            "status": "success",
            "span_kind": "poor",
            "data": [{"session_id": "ess_002", "trace_id": "t2"}],
        },
        state=state,
    )
    payload = state["interaction_research_tool_payloads"]["fetch_problematic_interaction_spans"]
    assert payload["count"] == 2
    assert payload["span_kinds"] == ["error", "poor"]


def test_capture_merges_breakdown_dimension_calls():
    state: dict = {}
    tool = type("T", (), {"name": "breakdown_interaction_by_dimension"})()
    capture_tool_response(
        tool=tool,
        tool_response={
            "status": "success",
            "dimension": "network",
            "data": [{"network": "Vi", "error_count": 7}],
        },
        state=state,
    )
    capture_tool_response(
        tool=tool,
        tool_response={
            "status": "success",
            "dimension": "platform",
            "data": [{"platform": "android", "error_count": 25}],
        },
        state=state,
    )
    payload = state["interaction_research_tool_payloads"]["breakdown_interaction_by_dimension"]
    assert len(payload["breakdowns"]) == 2
    dims = {b["dimension"] for b in payload["breakdowns"]}
    assert dims == {"network", "platform"}


def test_research_from_llm_output_uses_tool_payloads_not_broken_strings():
    research = research_from_llm_output(
        {
            "version": 1,
            "project_id": "proj-1",
            "interaction_name": "Pay",
            "period_start": "2026-05-01",
            "period_end": "2026-05-07",
            "interaction_config": "{not valid json at all",
        },
        tool_payloads={
            "fetch_interaction_config": {
                "status": "success",
                "data": {"name": "Pay"},
            },
        },
    )
    assert research.interaction_config == {"status": "success", "data": {"name": "Pay"}}


def test_format_tool_log_args_truncates_long_payloads():
    long_value = "x" * 2000
    text = format_tool_log_args({"interaction_name": long_value})
    assert len(text) <= 803
    assert text.endswith("...")


def test_format_tool_log_response_preserves_small_json():
    payload = {"status": "success", "data": {"apdex": 0.91}}
    assert format_tool_log_response(payload, max_chars=500) == (
        '{"data": {"apdex": 0.91}, "status": "success"}'
    )

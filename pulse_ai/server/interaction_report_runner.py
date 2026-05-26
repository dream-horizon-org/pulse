"""Per-interaction health report runner (issue 04): Research once, Schema with retries."""

from __future__ import annotations

import asyncio
import json
import logging
import uuid
from datetime import date
from typing import Any

from google.genai.types import Content
from pydantic import ValidationError

from pulse_ai.constants import (
    HTTP_TIMEOUT_GATEWAY,
    INTERACTION_REPORT_PIPELINE_NAME,
    RCA_PIPELINE_TIMEOUT_SECONDS,
    USER_ID_INTERACTION_REPORT,
)
from pulse_ai.output_guard import sanitize_pii
from pulse_ai.schemas.interaction_report_helpers import ParadoxKpiHint
from pulse_ai.schemas.interaction_report_v1 import (
    BehavioralSignal,
    BehaviorMetricLink,
    CohortBehaviorNote,
    FlowPattern,
    ImprovementAction,
    InteractionReportV1,
    KpiSnapshot,
    RootCause,
    RootCauseEvidence,
    derive_health_rating,
)
from pulse_ai.agents.interaction_research.tool_payload_state import (
    INTERACTION_RESEARCH_TOOL_PAYLOADS_KEY,
)
from pulse_ai.schemas.interaction_research_v1 import (
    InteractionResearchV1,
    research_from_llm_output,
)

logger = logging.getLogger(__name__)

MAX_SCHEMA_RETRIES = 2
REPORT_STATE_KEY = "interaction_report_v1"
RESEARCH_STATE_KEY = "interaction_research_v1"


class InteractionReportRunnerError(Exception):
    def __init__(self, status_code: int, message: str) -> None:
        super().__init__(message)
        self.status_code = status_code
        self.message = message


def _coerce_float(value: object) -> float | None:
    if value is None:
        return None
    if isinstance(value, (int, float)):
        return float(value)
    try:
        return float(str(value))
    except (TypeError, ValueError):
        return None


def _metric_row(metrics_payload: dict[str, Any] | None) -> dict[str, Any] | None:
    if not metrics_payload:
        return None
    data = metrics_payload.get("data")
    if isinstance(data, list) and data:
        row = data[0]
        return row if isinstance(row, dict) else None
    if isinstance(data, dict):
        return data
    return None


def extract_metric_triple(
    metrics_payload: dict[str, Any] | None,
) -> tuple[float | None, float | None, float | None]:
    """Return (apdex, error_rate_pct, poor_user_pct) from Agent 1 metrics_payload."""
    row = _metric_row(metrics_payload)
    if not row:
        return None, None, None

    apdex = _coerce_float(row.get("apdex"))
    success = _coerce_float(row.get("success_count"))
    errors = _coerce_float(row.get("error_count"))
    error_rate = None
    if success is not None and errors is not None and (success + errors) > 0:
        error_rate = 100.0 * errors / (success + errors)

    poor = _coerce_float(row.get("user_poor"))
    total_users = sum(
        _coerce_float(row.get(k)) or 0.0
        for k in ("user_excellent", "user_good", "user_avg", "user_poor")
    )
    poor_pct = None
    if poor is not None and total_users > 0:
        poor_pct = 100.0 * poor / total_users

    return apdex, error_rate, poor_pct


def _build_research_user_message(
    *,
    interaction_name: str,
    project_id: str,
    period_start: date | None,
    period_end: date | None,
) -> str:
    period_line = "not specified"
    if period_start and period_end:
        period_line = f"{period_start.isoformat()} to {period_end.isoformat()}"
    return (
        "Gather interaction research for a per-interaction health report.\n"
        f"Interaction: {interaction_name}\n"
        f"Project: {project_id}\n"
        f"Reporting period: {period_line}\n"
        "Call all mandatory tools before writing conclusions."
    )


def _build_schema_user_message(research: InteractionResearchV1) -> str:
    payload = research.model_dump(mode="json")
    serialized = json.dumps(payload, ensure_ascii=True)
    hint_block = ""
    if research.paradox_kpi_hint is not None:
        hint_block = (
            "\nparadox_kpi_hint is set — primary_kpi must be error_rate, secondary apdex.\n"
        )
    rating_block = ""
    if research.health_rating is not None:
        rating_block = f"\nhealth_rating from research: {research.health_rating}\n"
    return (
        "Assemble InteractionReportV1 from this InteractionResearchV1 JSON.\n"
        f"{hint_block}{rating_block}\n"
        f"InteractionResearchV1(JSON): {serialized}"
    )


def _kpi_from_verdict(
    report: InteractionReportV1,
    metric: str,
) -> tuple[float | None, str | None]:
    for snap in (report.verdict.primary_kpi, report.verdict.secondary_kpi):
        if snap.metric == metric:
            return snap.value, snap.display
    return None, None


def enforce_verdict_rating(
    report: InteractionReportV1,
    research: InteractionResearchV1,
) -> InteractionReportV1:
    """Overwrite verdict.rating with derive_health_rating() (never LLM-authored)."""
    apdex, error_rate, poor = extract_metric_triple(research.metrics_payload)
    if apdex is None:
        apdex, _ = _kpi_from_verdict(report, "apdex")
    if error_rate is None:
        error_rate, _ = _kpi_from_verdict(report, "error_rate")
    if poor is None:
        poor = report.verdict.poor_user_pct

    rating = derive_health_rating(
        apdex=apdex,
        error_rate_pct=error_rate,
        poor_user_pct=poor,
    )
    if report.verdict.rating == rating:
        return report
    return report.model_copy(
        update={"verdict": report.verdict.model_copy(update={"rating": rating})},
    )


def enforce_paradox_primary_kpi(
    report: InteractionReportV1,
    research: InteractionResearchV1,
) -> InteractionReportV1:
    """When paradox hint is set, primary_kpi must be error_rate."""
    if research.paradox_kpi_hint is None:
        return report

    apdex_val, apdex_display = _kpi_from_verdict(report, "apdex")
    err_val, err_display = _kpi_from_verdict(report, "error_rate")
    if err_val is None or apdex_val is None:
        apdex_m, err_m, _ = extract_metric_triple(research.metrics_payload)
        if err_val is None and err_m is not None:
            err_val = err_m
            err_display = f"{err_m:g}%"
        if apdex_val is None and apdex_m is not None:
            apdex_val = apdex_m
            apdex_display = f"{apdex_m:g}"

    if err_val is None or apdex_val is None:
        return report

    primary = KpiSnapshot(
        metric="error_rate",
        value=err_val,
        display=err_display or f"{err_val:g}%",
    )
    secondary = KpiSnapshot(
        metric="apdex",
        value=apdex_val,
        display=apdex_display or f"{apdex_val:g}",
    )
    return report.model_copy(
        update={
            "verdict": report.verdict.model_copy(
                update={"primary_kpi": primary, "secondary_kpi": secondary},
            ),
        },
    )


def sanitize_interaction_report(report: InteractionReportV1) -> InteractionReportV1:
    """PII-redact free-text fields before API return (RCA output-guard pattern)."""
    verdict = report.verdict.model_copy(
        update={"summary": sanitize_pii(report.verdict.summary) or ""},
    )

    user_impact = report.user_impact.model_copy(
        update={"funnel_link": sanitize_pii(report.user_impact.funnel_link) or ""},
    )
    if user_impact.segment_highlights:
        user_impact = user_impact.model_copy(
            update={
                "segment_highlights": [
                    h.model_copy(
                        update={"impact_summary": sanitize_pii(h.impact_summary) or ""},
                    )
                    for h in user_impact.segment_highlights
                ],
            },
        )

    fp = report.user_behavior.flow_pattern
    flow_pattern = FlowPattern(
        happy_path=sanitize_pii(fp.happy_path) or "",
        deviant_paths=[sanitize_pii(p) or "" for p in fp.deviant_paths],
    )
    signals = [
        BehavioralSignal(
            signal=sanitize_pii(s.signal) or "",
            meaning=sanitize_pii(s.meaning) or "",
            estimated_frequency=sanitize_pii(s.estimated_frequency)
            if s.estimated_frequency
            else None,
            notes=sanitize_pii(s.notes) if s.notes else None,
            example=sanitize_pii(s.example) if s.example else None,
        )
        for s in report.user_behavior.behavioral_signals
    ]
    links = [
        BehaviorMetricLink(
            user_action=sanitize_pii(link.user_action) or "",
            effect_on_metrics=sanitize_pii(link.effect_on_metrics) or "",
        )
        for link in report.user_behavior.behavior_metric_links
    ]
    cohort = None
    if report.user_behavior.cohort_behavior:
        cohort = [
            CohortBehaviorNote(
                cohort=sanitize_pii(n.cohort) or "",
                observation=sanitize_pii(n.observation) or "",
            )
            for n in report.user_behavior.cohort_behavior
        ]
    user_behavior = report.user_behavior.model_copy(
        update={
            "flow_pattern": flow_pattern,
            "behavioral_signals": signals,
            "behavior_metric_links": links,
            "cohort_behavior": cohort,
        },
    )

    diagnosis = report.diagnosis.model_copy(
        update={
            "reliability": [sanitize_pii(s) or "" for s in report.diagnosis.reliability],
            "latency": [sanitize_pii(s) or "" for s in report.diagnosis.latency],
            "measurement": [sanitize_pii(s) or "" for s in report.diagnosis.measurement],
        },
    )

    rc = report.root_cause
    root_cause = RootCause(
        primary_cause=sanitize_pii(rc.primary_cause) or "",
        contributing_factors=[sanitize_pii(f) or "" for f in rc.contributing_factors],
        ruled_out=[sanitize_pii(r) or "" for r in rc.ruled_out] if rc.ruled_out else None,
        evidence=[
            RootCauseEvidence(
                source=e.source,
                detail=sanitize_pii(e.detail) or "",
            )
            for e in rc.evidence
        ],
        confidence=rc.confidence,
    )

    actions = [
        ImprovementAction(
            priority=a.priority,
            action=sanitize_pii(a.action) or "",
            type=a.type,
            owner=sanitize_pii(a.owner) or "",
            effort=a.effort,
            target_metric=a.target_metric,
            expected_lift=sanitize_pii(a.expected_lift) or "",
            behavior_driven=a.behavior_driven,
        )
        for a in report.actions
    ]

    identity = report.identity.model_copy(
        update={"business_moment": sanitize_pii(report.identity.business_moment) or ""},
    )

    return report.model_copy(
        update={
            "identity": identity,
            "verdict": verdict,
            "user_impact": user_impact,
            "user_behavior": user_behavior,
            "diagnosis": diagnosis,
            "root_cause": root_cause,
            "actions": actions,
        },
    )


def postprocess_interaction_report(
    report: InteractionReportV1,
    research: InteractionResearchV1,
) -> InteractionReportV1:
    """Deterministic verdict enforcement, paradox KPI, then PII sanitize."""
    report = enforce_paradox_primary_kpi(report, research)
    report = enforce_verdict_rating(report, research)
    return sanitize_interaction_report(report)


async def _run_agent_once(
    runner: Any,
    *,
    session_id: str,
    message: Content,
    state_delta: dict[str, Any] | None,
) -> None:
    async def _run() -> None:
        async for _ in runner.run_async(
            user_id=USER_ID_INTERACTION_REPORT,
            session_id=session_id,
            new_message=message,
            state_delta=state_delta,
        ):
            pass

    await asyncio.wait_for(_run(), timeout=RCA_PIPELINE_TIMEOUT_SECONDS)


async def _load_session_state(runner: Any, session_id: str) -> dict[str, Any]:
    session = await runner.session_service.get_session(
        app_name=runner.app_name,
        user_id=USER_ID_INTERACTION_REPORT,
        session_id=session_id,
    )
    if not session:
        return {}
    return dict(session.state or {})


def _parse_research(
    raw: object,
    *,
    tool_payloads: dict[str, Any] | None = None,
) -> InteractionResearchV1 | None:
    if raw is None:
        return None
    try:
        return research_from_llm_output(raw, tool_payloads=tool_payloads)
    except (ValidationError, ValueError, TypeError):
        logger.warning("interaction_research_v1 validation failed", exc_info=True)
        return None


def _parse_report(raw: object) -> InteractionReportV1 | None:
    if raw is None:
        return None
    try:
        if isinstance(raw, str):
            return InteractionReportV1.model_validate_json(raw)
        if isinstance(raw, dict):
            return InteractionReportV1.model_validate(raw)
        return InteractionReportV1.model_validate(raw)
    except ValidationError as exc:
        logger.warning(
            "interaction_report_v1 validation failed: %s",
            exc.errors(),
        )
        return None


async def generate_interaction_report(
    research_runner: Any,
    schema_runner: Any,
    *,
    project_id: str,
    interaction_name: str,
    period_start: date | None = None,
    period_end: date | None = None,
    state_delta: dict[str, Any] | None = None,
) -> InteractionReportV1:
    """
    Run Agent 1 once, then Agent 2 up to MAX_SCHEMA_RETRIES times on the same session.

    Raises InteractionReportRunnerError on timeout or exhausted schema retries.
    """
    session_id = str(uuid.uuid4())
    merged_delta: dict[str, Any] = dict(state_delta or {})
    merged_delta["project_id"] = project_id
    merged_delta["interaction_name"] = interaction_name
    if period_start:
        merged_delta["period_start"] = period_start.isoformat()
    if period_end:
        merged_delta["period_end"] = period_end.isoformat()

    research_message = Content.model_validate(
        {
            "role": "user",
            "parts": [
                {
                    "text": _build_research_user_message(
                        interaction_name=interaction_name,
                        project_id=project_id,
                        period_start=period_start,
                        period_end=period_end,
                    ),
                },
            ],
        },
    )

    try:
        await _run_agent_once(
            research_runner,
            session_id=session_id,
            message=research_message,
            state_delta=merged_delta,
        )
    except TimeoutError as error:
        raise InteractionReportRunnerError(
            HTTP_TIMEOUT_GATEWAY,
            "Interaction report research timed out",
        ) from error
    except Exception as error:  # noqa: BLE001
        logger.exception("Interaction research agent failed")
        raise InteractionReportRunnerError(
            500,
            "Interaction report research failed",
        ) from error

    state = await _load_session_state(research_runner, session_id)
    tool_payloads = state.get(INTERACTION_RESEARCH_TOOL_PAYLOADS_KEY)
    research = _parse_research(
        state.get(RESEARCH_STATE_KEY),
        tool_payloads=tool_payloads if isinstance(tool_payloads, dict) else None,
    )
    if research is None:
        raise InteractionReportRunnerError(
            500,
            "Interaction research output missing or invalid",
        )

    report: InteractionReportV1 | None = None
    schema_message = Content.model_validate(
        {
            "role": "user",
            "parts": [{"text": _build_schema_user_message(research)}],
        },
    )

    for attempt in range(MAX_SCHEMA_RETRIES):
        logger.debug(
            "Interaction report schema attempt %d/%d, session_id=%s",
            attempt + 1,
            MAX_SCHEMA_RETRIES,
            session_id,
        )
        try:
            await _run_agent_once(
                schema_runner,
                session_id=session_id,
                message=schema_message,
                state_delta=None,
            )
        except TimeoutError as error:
            raise InteractionReportRunnerError(
                HTTP_TIMEOUT_GATEWAY,
                "Interaction report schema generation timed out",
            ) from error
        except Exception as error:  # noqa: BLE001
            logger.exception("Interaction report schema agent failed on attempt %d", attempt + 1)
            if attempt >= MAX_SCHEMA_RETRIES - 1:
                raise InteractionReportRunnerError(
                    500,
                    "Interaction report schema generation failed",
                ) from error
            continue

        state = await _load_session_state(schema_runner, session_id)
        report = _parse_report(state.get(REPORT_STATE_KEY))
        if report is not None:
            break
        if attempt < MAX_SCHEMA_RETRIES - 1:
            logger.info("Schema validation failed, retrying schema agent only")

    try:
        await schema_runner.session_service.delete_session(
            app_name=schema_runner.app_name,
            user_id=USER_ID_INTERACTION_REPORT,
            session_id=session_id,
        )
    except Exception:
        logger.warning(
            "Failed to delete ephemeral interaction report session %s",
            session_id,
            exc_info=True,
        )

    if report is None:
        raise InteractionReportRunnerError(
            500,
            "Interaction report generation failed after schema retries",
        )

    return postprocess_interaction_report(report, research)


def pipeline_agent_name() -> str:
    """Registered SequentialAgent name for tests."""
    return INTERACTION_REPORT_PIPELINE_NAME

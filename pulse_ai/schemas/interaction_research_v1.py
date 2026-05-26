"""Internal Agent 1 state for per-interaction health report generation.

Not exposed in the v1 client API — consumed by Agent 2 and the report runner.
"""

from __future__ import annotations

import json
from datetime import date
from typing import Any

from pydantic import BaseModel, ConfigDict, Field, ValidationError

from pulse_ai.schemas.interaction_report_helpers import ParadoxKpiHint
from pulse_ai.schemas.interaction_report_v1 import (
    HealthRating,
    ReportingPeriod,
    SegmentHighlight,
)


class InteractionResearchV1Llm(BaseModel):
    """ADK-safe Agent 1 output: flat fields only (no nested Pydantic models).

    Google ADK automatic function calling cannot declare nested models such as
    ReportingPeriod or SegmentHighlight on set_model_response when the agent
    also has tools. Deterministic fields (segment_highlights, paradox_kpi_hint,
    health_rating) are added in enrich_interaction_research after the LLM step.
    """

    model_config = ConfigDict(populate_by_name=True)

    version: int = Field(default=1, ge=1, le=1)
    project_id: str = Field(..., description="Pulse project ID.")
    interaction_name: str = Field(..., description="Interaction span name.")
    period_start: str = Field(
        ...,
        description="Reporting period start (ISO 8601 date YYYY-MM-DD).",
    )
    period_end: str = Field(
        ...,
        description="Reporting period end (ISO 8601 date YYYY-MM-DD).",
    )

    journey_summary: str | None = Field(
        default=None,
        description="Summary of journey paths relevant to this interaction.",
    )
    deviant_paths_observed: list[str] = Field(
        default_factory=list,
        description="Non-happy paths observed from journey analysis.",
    )
    funnel_context: str | None = Field(
        default=None,
        description="One-sentence funnel placement when confidently matched.",
    )
    session_observations: list[str] = Field(
        default_factory=list,
        description="Notable patterns from bad/sample session review.",
    )

    # Tool payloads are captured server-side in session state — not LLM-copied (avoids bad JSON).
    bad_session_ids: list[str] | None = Field(
        default=None,
        description="Optional; usually filled from fetch_bad_interaction_sessions tool capture.",
    )


class InteractionResearchV1(BaseModel):
    """Structured research output from Agent 1 (Interaction Research agent)."""

    model_config = ConfigDict(populate_by_name=True)

    version: int = Field(default=1, ge=1, le=1)
    project_id: str = Field(..., description="Pulse project ID.")
    interaction_name: str = Field(..., description="Interaction span name.")
    reporting_period: ReportingPeriod

    journey_summary: str | None = Field(
        default=None,
        description="Summary of journey paths relevant to this interaction.",
    )
    deviant_paths_observed: list[str] = Field(
        default_factory=list,
        description="Non-happy paths observed from journey analysis.",
    )
    funnel_context: str | None = Field(
        default=None,
        description="One-sentence funnel placement when confidently matched.",
    )
    session_observations: list[str] = Field(
        default_factory=list,
        description="Notable patterns from bad/sample session review.",
    )

    interaction_config: dict[str, Any] | None = Field(
        default=None,
        description="Raw interaction config tool payload for Agent 2.",
    )
    metrics_payload: dict[str, Any] | None = Field(
        default=None,
        description="Raw metrics tool payload (Apdex, error, categorization, latency).",
    )
    rca_payload: dict[str, Any] | None = Field(
        default=None,
        description="Raw tabular RCA tool payload.",
    )
    journey_payload: dict[str, Any] | None = Field(
        default=None,
        description="Optional journey tool payload.",
    )
    funnel_payload: dict[str, Any] | None = Field(
        default=None,
        description="Optional funnel tool payload.",
    )
    bad_session_ids: list[str] | None = Field(
        default=None,
        description="Session IDs from bad-session tool (for Block 8).",
    )

    segment_highlights: list[SegmentHighlight] | None = Field(
        default=None,
        max_length=3,
        description="Deterministic mapper output when RCA shows outliers.",
    )
    paradox_kpi_hint: ParadoxKpiHint | None = Field(
        default=None,
        description="Deterministic hint when error_rate > 3% and apdex > 0.7.",
    )
    health_rating: HealthRating | None = Field(
        default=None,
        description="Deterministic rating from derive_health_rating().",
    )


def _parse_iso_date(value: str) -> date:
    return date.fromisoformat(value.strip()[:10])


def _parse_json_payload(value: Any) -> dict[str, Any] | None:
    """Best-effort parse for legacy LLM-copied JSON strings (prefer tool capture)."""
    if value is None:
        return None
    if isinstance(value, dict):
        return value
    if isinstance(value, str):
        stripped = value.strip()
        if not stripped:
            return None
        try:
            parsed = json.loads(stripped)
        except json.JSONDecodeError:
            return None
        return parsed if isinstance(parsed, dict) else None
    return None


def research_from_llm_output(
    raw: object,
    *,
    tool_payloads: dict[str, Any] | None = None,
) -> InteractionResearchV1:
    """Map Agent 1 LLM output (flat period_start/end) to canonical research."""
    if isinstance(raw, InteractionResearchV1):
        return raw
    if isinstance(raw, InteractionResearchV1Llm):
        llm = raw
    elif isinstance(raw, dict):
        if "reporting_period" in raw:
            research = InteractionResearchV1.model_validate(raw)
            if tool_payloads:
                from pulse_ai.agents.interaction_research.tool_payload_state import (
                    apply_tool_payloads_to_research,
                )

                return apply_tool_payloads_to_research(research, tool_payloads)
            return research
        llm = InteractionResearchV1Llm.model_validate(raw)
    elif isinstance(raw, str):
        try:
            return InteractionResearchV1.model_validate_json(raw)
        except ValidationError:
            llm = InteractionResearchV1Llm.model_validate_json(raw)
    else:
        llm = InteractionResearchV1Llm.model_validate(raw)

    research = InteractionResearchV1(
        version=llm.version,
        project_id=llm.project_id,
        interaction_name=llm.interaction_name,
        reporting_period=ReportingPeriod(
            start=_parse_iso_date(llm.period_start),
            end=_parse_iso_date(llm.period_end),
        ),
        journey_summary=llm.journey_summary,
        deviant_paths_observed=llm.deviant_paths_observed,
        funnel_context=llm.funnel_context,
        session_observations=llm.session_observations,
        bad_session_ids=llm.bad_session_ids,
    )

    if tool_payloads:
        from pulse_ai.agents.interaction_research.tool_payload_state import (
            apply_tool_payloads_to_research,
        )

        return apply_tool_payloads_to_research(research, tool_payloads)

    # Legacy: LLM may still echo stringified payloads on older sessions.
    legacy_updates: dict[str, Any] = {}
    for field, raw_val in (
        ("interaction_config", getattr(llm, "interaction_config", None)),
        ("metrics_payload", getattr(llm, "metrics_payload", None)),
        ("rca_payload", getattr(llm, "rca_payload", None)),
        ("journey_payload", getattr(llm, "journey_payload", None)),
        ("funnel_payload", getattr(llm, "funnel_payload", None)),
    ):
        parsed = _parse_json_payload(raw_val)
        if parsed is not None:
            legacy_updates[field] = parsed
    if legacy_updates:
        research = research.model_copy(update=legacy_updates)

    return research


def interaction_research_json_schema() -> dict:
    """JSON Schema for Agent 1 structured output (ADK/Gemini-safe)."""
    return InteractionResearchV1Llm.model_json_schema()

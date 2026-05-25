"""Internal Agent 1 state for per-interaction health report generation.

Not exposed in the v1 client API — consumed by Agent 2 and the report runner.
"""

from __future__ import annotations

from typing import Any

from pydantic import BaseModel, ConfigDict, Field

from pulse_ai.schemas.interaction_report_helpers import ParadoxKpiHint
from pulse_ai.schemas.interaction_report_v1 import (
    HealthRating,
    ReportingPeriod,
    SegmentHighlight,
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


def interaction_research_json_schema() -> dict:
    """Return JSON Schema for Agent 1 structured output registration."""
    return InteractionResearchV1.model_json_schema()

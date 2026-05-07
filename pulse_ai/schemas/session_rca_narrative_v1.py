"""Session-scoped RCA narrative (executive summary + per-segment insights + recommendations)."""

from __future__ import annotations

from pydantic import BaseModel, Field


class SessionRcaSegmentInsight(BaseModel):
    label: str = Field(..., description="Segment label, e.g. 'platform: Android'.")
    impact: str = Field(..., description="'critical' or 'normal'.")
    z_score: float | None = Field(
        default=None,
        description="Standard deviations below mean quality (negative = degraded).",
    )
    quality_score: float | None = Field(
        default=None,
        description="Segment apdex quality score (0–1, higher is better).",
    )
    volume_pct: float | None = Field(
        default=None,
        description="Segment volume as % of baseline volume.",
    )
    key_finding: str = Field(
        ...,
        description="One sentence: what is notable about this segment.",
    )


class SessionRcaNarrativeV1(BaseModel):
    """LLM output for session-scoped quality RCA."""

    version: int = Field(default=1, ge=1, le=1)
    executive_summary: str = Field(
        ...,
        description=(
            "Up to 4 sentences: overall session quality assessment, worst segment, "
            "scope of impact when clear from volumes."
        ),
    )
    segment_insights: list[SessionRcaSegmentInsight] = Field(
        default_factory=list,
        max_length=8,
        description="One insight per returned segment, in order of severity.",
    )
    recommendations: list[str] = Field(
        ...,
        min_length=3,
        max_length=7,
        description="Short actionable strings grounded in segment data.",
    )

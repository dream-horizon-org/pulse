"""Session-scoped RCA structured report v1."""

from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field

SessionRcaMetricIdV1 = Literal["session_score", "volume"]


class SessionRcaMetricRowV1(BaseModel):
    metric_id: SessionRcaMetricIdV1
    metric_label: str
    value_display: str
    baseline_display: str
    delta_display: str
    value_number: float | None = None
    baseline_number: float | None = None


class SessionRcaStructuredSegmentV1(BaseModel):
    rank: int = Field(ge=1, description="1-based rank among segments.")
    title: str = Field(description="Copy the segment label exactly, e.g. 'platform: Android'.")
    impact: str = Field(description='"critical" or "normal"; echo from payload, do not override.')
    metrics: list[SessionRcaMetricRowV1] = Field(
        description="Exactly 2 rows in order: session_score (Apdex) then volume.",
    )
    insights: str | None = Field(
        default=None,
        description="One sentence explaining what makes this segment notable.",
    )
    affected_sessions: list[str] | None = Field(
        default=None,
        description="Up to 2 session IDs with session_score below the critical threshold.",
    )


class SessionRcaStructuredV1(BaseModel):
    """LLM output for session-scoped quality RCA — structured format."""

    version: int = Field(default=1, ge=1, le=1)
    executive_summary: str = Field(
        ...,
        description=(
            "Up to 4 sentences: overall session quality assessment, worst segment, "
            "scope of impact when clear from volumes."
        ),
    )
    segments: list[SessionRcaStructuredSegmentV1] = Field(
        default_factory=list,
        max_length=8,
        description="One entry per returned segment, ordered by severity (critical first).",
    )
    recommendations: list[str] = Field(
        ...,
        min_length=3,
        max_length=7,
        description="Short actionable strings grounded in segment data.",
    )

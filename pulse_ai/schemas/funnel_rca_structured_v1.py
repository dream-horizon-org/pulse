"""Funnel drop-off RCA structured report v1 (OTel cause lift, not dimension segments)."""

from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field, field_validator

FunnelRcaMetricIdV1 = Literal[
    "lift",
    "dropoff_affected",
    "dropoff_rate_pct",
    "converter_affected",
]


class FunnelRcaMetricRowV1(BaseModel):
    metric_id: FunnelRcaMetricIdV1
    metric_label: str
    value_display: str
    baseline_display: str
    delta_display: str
    value_number: float | None = None
    baseline_number: float | None = None


class FunnelRcaStructuredSegmentV1(BaseModel):
    rank: int = Field(ge=1)
    title: str = Field(description="Cause label from payload, e.g. '503 @ Checkout API'.")
    metrics: list[FunnelRcaMetricRowV1] = Field(
        description="All four funnel metrics for this cause from the payload segment.",
    )
    insights: str | None = Field(
        default=None,
        description="2–4 sentences on why this cause ranks here (lift vs converters).",
    )
    affected_sessions: list[str] | None = Field(
        default=None,
        description="Omit in LLM output — injected by runner from backend exampleSessionIds.",
    )

    @field_validator("affected_sessions", mode="before")
    @classmethod
    def reject_llm_affected_sessions(cls, value: object) -> None:
        return None


class FunnelRcaStructuredV1(BaseModel):
    version: int = Field(default=1, ge=1, le=1)
    executive_summary: str = Field(
        ...,
        description=(
            "Up to 4 sentences: overall drop-off story, top OTel cause, funnel mode "
            "(sessions vs unique users) when stated in baseline."
        ),
    )
    segments: list[FunnelRcaStructuredSegmentV1] = Field(
        default_factory=list,
        max_length=8,
        description="Ranked OTel causes by lift (highest first).",
    )
    recommendations: list[str] = Field(
        ...,
        min_length=3,
        max_length=7,
        description="Actionable strings grounded in ranked causes.",
    )


class FunnelRcaStructuredSegmentResponseV1(FunnelRcaStructuredSegmentV1):
    """Runner may inject example session IDs after LLM generation."""


class FunnelRcaStructuredResponseV1(BaseModel):
    version: int = 1
    executive_summary: str
    segments: list[FunnelRcaStructuredSegmentResponseV1]
    recommendations: list[str]

    @classmethod
    def from_llm_output(cls, structured: FunnelRcaStructuredV1) -> "FunnelRcaStructuredResponseV1":
        return cls(
            version=structured.version,
            executive_summary=structured.executive_summary,
            segments=[
                FunnelRcaStructuredSegmentResponseV1(**seg.model_dump())
                for seg in structured.segments
            ],
            recommendations=structured.recommendations,
        )

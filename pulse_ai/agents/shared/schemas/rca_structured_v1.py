from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field

# Backend root-cause metric keys (RootCauseMetricsRegistry); enforced on structured RCA v1 rows.
RcaStructuredMetricIdV1 = Literal[
    "volume",
    "apdex",
    "error_rate",
    "poor_user_pct",
    "duration_p50",
    "duration_p95",
    "crash_rate",
    "anr_rate",
    "frozen_frame_rate",
    "slow_frame_rate",
]


class RcaStructuredMetricRowV1(BaseModel):
    metric_id: RcaStructuredMetricIdV1
    metric_label: str
    value_display: str
    baseline_display: str
    delta_display: str
    value_number: float | None = None
    baseline_number: float | None = None


class RcaStructuredSegmentV1(BaseModel):
    rank: int = Field(ge=1, description="1-based rank among segments")
    title: str
    metrics: list[RcaStructuredMetricRowV1]
    insights: str | None = None
    affected_sessions: list[str] | None = None


class RcaStructuredReportV1(BaseModel):
    version: int = 1
    executive_summary: str
    segments: list[RcaStructuredSegmentV1]
    recommendations: list[str]

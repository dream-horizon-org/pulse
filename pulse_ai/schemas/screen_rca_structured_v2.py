"""Screen RCA v2: multi-problem structured schema with pass-through problems + evidences and LLM-generated summary."""

from __future__ import annotations

from typing import Literal, Optional

from pydantic import BaseModel, Field

ScreenRcaProblemType = Literal[
    "crashes",
    "anr",
    "frozen_frames",
    "slow_rendering",
    "network_failures",
    "network_latency",
    "screen_load_time",
    "screen_interactive",
    "bad_clicks",
]


class ScreenRcaMetrics(BaseModel):
    """Mirrors backend ScreenRcaMetrics — pass-through, no LLM modification."""

    affected_volume: Optional[int] = None  # distinct affected users/sessions
    rate: Optional[str] = None  # e.g. "31.58%"
    p50_ms: Optional[int] = None  # screen_load, screen_interactive, network_latency only
    p95_ms: Optional[int] = None


class ScreenRcaSpecificIssue(BaseModel):
    """Top-3 crash/ANR issues — pass-through from backend."""

    group_id: str  # GroupId from stack_trace_events
    issue: Optional[str] = None  # ExceptionMessage (crashes)
    thread_name: Optional[str] = None  # Title (ANR)
    count: int


class ScreenRcaProblem(BaseModel):
    """Single ranked problem — backend-computed, LLM must not modify."""

    problem_type: ScreenRcaProblemType
    rank: int = Field(ge=1, description="1 = most critical")
    weightage: float = Field(ge=0.0, le=1.0)
    most_affected_segment: str  # e.g. "AppVersion: 5.1.0"
    metric_id: str
    metrics: ScreenRcaMetrics  # baseline (overall screen)
    segment_metrics: Optional[ScreenRcaMetrics] = None  # value (scoped to most-affected segment)
    specific_issues: Optional[list[ScreenRcaSpecificIssue]] = None  # crashes + ANR only


class ScreenRcaEvidences(BaseModel):
    """Backend-computed evidence references — pass-through, no LLM modification."""

    sessions: list[str] = Field(
        default_factory=list,
        description="Up to 3 session IDs most affected by rank-1 problem.",
    )
    heatmap_available: bool = Field(
        default=False,
        description="True when interaction_heatmaps_daily has data for this screen in the window.",
    )


class ScreenRcaStructuredV2(BaseModel):
    """
    Final V2 response.
    - problems + evidences: passed through from backend, LLM must NOT modify.
    - executive_summary + recommendations: LLM-generated only.
    """

    version: int = Field(default=2, ge=2, le=2)
    executive_summary: str = Field(
        description=(
            "Max 6-7 lines on overall screen health. Lead with rank-1 problem, "
            "name the segment, quantify impact. Do NOT list all problems."
        ),
    )
    problems: list[ScreenRcaProblem] = Field(
        default_factory=list,
        description="Pre-ranked by backend. LLM must not reorder or modify.",
    )
    evidences: ScreenRcaEvidences
    recommendations: list[str] = Field(
        default_factory=list,
        max_length=7,
        description="Short actionable bullets grounded in the provided data.",
    )

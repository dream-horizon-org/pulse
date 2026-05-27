"""Screen RCA v2: multi-problem structured schema — LLM generates summary, recommendations, and picks evidences."""

from __future__ import annotations

from typing import Literal, Optional

from pydantic import BaseModel, ConfigDict, Field, field_serializer

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

    affected_volume: Optional[int] = None
    rate: Optional[str] = None
    p50_ms: Optional[int] = None
    p95_ms: Optional[int] = None
    click_volume: Optional[int] = None
    rage_count: Optional[int] = None
    dead_count: Optional[int] = None


class ScreenRcaSpecificIssue(BaseModel):
    """Top crash/ANR issues — pass-through from backend."""

    group_id: str
    issue: Optional[str] = None
    thread_name: Optional[str] = None
    count: int


class ScreenRcaProblem(BaseModel):
    """Single ranked problem — backend-computed, LLM must not modify."""

    problem_type: ScreenRcaProblemType
    rank: int = Field(ge=1, description="1 = most critical")
    weightage: float = Field(ge=0.0, le=1.0)
    most_affected_segment: str
    metric_id: str
    metrics: ScreenRcaMetrics
    segment_metrics: Optional[ScreenRcaMetrics] = None
    specific_issues: Optional[list[ScreenRcaSpecificIssue]] = None


class ScreenRcaSegmentFilters(BaseModel):
    """Known RCA segment dimensions."""

    model_config = ConfigDict(extra="ignore")

    Platform: Optional[str] = None
    OsVersion: Optional[str] = None
    AppVersion: Optional[str] = None
    DeviceModel: Optional[str] = None
    NetworkProvider: Optional[str] = None
    GeoState: Optional[str] = None


class ScreenRcaIssueSessionEvidence(BaseModel):
    """Session replay evidence for one ranked problem — LLM selects 3 from the input list."""

    rank: int = Field(
        ge=1,
        le=3,
        description="Display relevance rank 1–3 (1 = most relevant for evidence strip).",
    )
    problem_type: ScreenRcaProblemType
    segment: Optional[str] = None
    segment_filters: Optional[ScreenRcaSegmentFilters] = None
    session_id: Optional[str] = None

    @field_serializer("segment_filters")
    def serialize_segment_filters(
        self,
        value: ScreenRcaSegmentFilters | None,
    ) -> dict[str, str] | None:
        if value is None:
            return None
        dumped = value.model_dump(exclude_none=True)
        return dumped or None


class ScreenRcaEvidences(BaseModel):
    """Evidence references — issue_sessions selected by LLM; heatmap fields pass through."""

    issue_sessions: list[ScreenRcaIssueSessionEvidence] = Field(
        default_factory=list,
        max_length=3,
        description=(
            "Up to 3 session-replay cards for the evidence strip — LLM picks the most relevant "
            "from the full input list (one candidate per problem rank)."
        ),
    )
    heatmap_available: bool = Field(
        default=False,
        description="True when interaction_heatmaps_daily has data for this screen on heatmap_date.",
    )
    heatmap_date: Optional[str] = Field(
        default=None,
        description="Report request date (yyyy-MM-dd) — heatmap evidence uses this day.",
    )


class ScreenRcaStructuredV2(BaseModel):
    """
    Final V2 response.
    - problems: passed through from backend unchanged (runner overrides after LLM).
    - evidences.issue_sessions: LLM selects up to 3 from input candidates; heatmap fields pass through.
    - executive_summary + recommendations: LLM-generated only.
    """

    version: int = Field(default=2, ge=2, le=2)
    executive_summary: str = Field(
        description=(
            "Max 6 sentences on overall screen health. Lead with most-impactful problem, "
            "name the segment, quantify impact. Do NOT mention session IDs or evidence."
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

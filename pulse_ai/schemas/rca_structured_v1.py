from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator

from pulse_ai.schemas.error_attribution_rca import CANONICAL_FOR_RCA_SIGNALS

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


RcaErrorAttributionSignalV1 = Literal["anr", "non_fatal", "api"]


class ErrorAttributionInsightV1(BaseModel):
    """NLP layer on top of pre-computed drill data (correlative, not causal)."""

    signal: RcaErrorAttributionSignalV1
    summary: str | None = Field(
        default=None,
        description=(
            "When there is interpretable drill-down narrative for this signal, 2–4 sentences; "
            "otherwise JSON null (do not emit filler placeholder prose)."
        ),
    )
    caveat: str | None = Field(
        default=None,
        description="Optional short reminder that drill correlations are not causal proof.",
    )

    @field_validator("summary", mode="before")
    @classmethod
    def normalize_blank_summary(cls, v: object) -> str | None:
        if v is None:
            return None
        if isinstance(v, str):
            stripped = v.strip()
            return stripped if stripped else None
        return v


class RelatedAttributionEntryStructuredV1(BaseModel):
    """camelCase keys aligned with ErrorAttributionRestResponse / pulse-ui."""

    model_config = ConfigDict(extra="ignore")

    sourceSignal: str
    rowKind: str
    groupId: str | None = None
    title: str | None = None
    exceptionType: str | None = None
    url: str | None = None
    graphqlOperationName: str | None = None
    graphqlOperationType: str | None = None
    httpMethod: str | None = None
    httpStatusCode: str | None = None
    occurrences: int = 0
    nTreated: int | None = None
    nControl: int | None = None
    nTreatedLow: int | None = None
    nControlLow: int | None = None
    p1: float | None = None
    p2: float | None = None
    rr: float | None = None
    rrUndefined: bool | None = None
    rrUndefinedReason: str | None = None


class ErrorAttributionStructuredV1(BaseModel):
    """Faithful copy of ErrorAttributionPayload(JSON) when drill data was supplied to the user message."""

    model_config = ConfigDict(extra="ignore")

    disclaimer: str
    cachedAt: str | None = None
    minRiskRatioForIssueAttribution: float | None = None
    relatedAttributions: list[RelatedAttributionEntryStructuredV1] | None = None


class RcaStructuredSegmentV1(BaseModel):
    rank: int = Field(ge=1, description="1-based rank among segments")
    title: str
    metrics: list[RcaStructuredMetricRowV1]
    insights: str | None = None
    affected_sessions: list[str] | None = None


class RcaStructuredReportV1(BaseModel):
    version: int = 1
    executive_summary: str
    everything_good: bool = False
    no_data_available: bool = False
    segments: list[RcaStructuredSegmentV1]
    recommendations: list[str]
    error_attribution_insights: list[ErrorAttributionInsightV1] | None = Field(
        default=None,
        description=(
            "When the user message includes ErrorAttributionPayload(JSON), output exactly one row "
            f"per signal in order {list(CANONICAL_FOR_RCA_SIGNALS)} with matching `signal` values. "
            "When that JSON block is absent, omit this field or set null."
        ),
    )
    error_attribution: ErrorAttributionStructuredV1 | None = Field(
        default=None,
        description=(
            "When ErrorAttributionPayload(JSON) is in the user message, copy that object faithfully "
            "here (same numbers, `relatedAttributions`, disclaimer, RR floor). When absent, null."
        ),
    )

    @field_validator("error_attribution_insights")
    @classmethod
    def validate_error_attribution_insights(
        cls, v: list[ErrorAttributionInsightV1] | None,
    ) -> list[ErrorAttributionInsightV1] | None:
        if v is None:
            return v
        expected = CANONICAL_FOR_RCA_SIGNALS
        if len(v) != len(expected):
            raise ValueError(
                "error_attribution_insights must be null/omitted or contain exactly "
                f"{len(expected)} entries in order {list(expected)}; got {len(v)}",
            )
        for i, exp in enumerate(expected):
            if v[i].signal != exp:
                raise ValueError(
                    f"error_attribution_insights[{i}].signal must be {exp!r}, got {v[i].signal!r}",
                )
        return v

    @model_validator(mode="after")
    def validate_segments_vs_state_flags(self) -> RcaStructuredReportV1:
        no_findings = self.everything_good or self.no_data_available
        if no_findings and len(self.segments) > 0:
            raise ValueError(
                "segments must be empty when everything_good or no_data_available is true"
            )
        if not no_findings and len(self.segments) == 0:
            raise ValueError(
                "segments must not be empty when neither everything_good nor no_data_available is true"
            )
        if no_findings and len(self.recommendations) > 0:
            raise ValueError(
                "recommendations must be empty when everything_good or no_data_available is true"
            )
        return self

    @model_validator(mode="after")
    def validate_insights_and_drill_together(self) -> RcaStructuredReportV1:
        ins = self.error_attribution_insights
        att = self.error_attribution
        if ins is None:
            if att is not None:
                raise ValueError(
                    "error_attribution must be null when error_attribution_insights is null",
                )
            return self
        if att is None:
            raise ValueError(
                "error_attribution is required when error_attribution_insights is present",
            )
        return self

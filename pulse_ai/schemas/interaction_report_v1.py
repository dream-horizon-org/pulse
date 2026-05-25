"""Pydantic schema for per-interaction health reports (InteractionReportV1).

Mirrors the 8-block structure in ``docs/interaction-report/interaction-report-template.md``.
Runtime source of truth; docs prototype remains spec reference.
"""

from __future__ import annotations

from datetime import date, datetime, timezone
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator

HealthRating = Literal["red", "amber", "green"]
PrimaryKpi = Literal["apdex", "error_rate"]
ConfidenceLevel = Literal["high", "medium", "low"]
ActionPriority = Literal["P0", "P1", "P2", "P3"]
ActionType = Literal["app", "ux", "config", "infra", "instrumentation"]
EffortSize = Literal["S", "M", "L"]
BusinessRisk = Literal[
    "checkout_blocked",
    "payment_friction",
    "browse_friction",
    "na_test",
]
UserExperienceCategory = Literal["excellent", "good", "average", "poor"]
DiagnosisLens = Literal["reliability", "latency", "measurement"]


class InteractionThresholds(BaseModel):
    """Apdex bucket limits in milliseconds plus interaction timeout."""

    excellent_ms: int = Field(..., ge=0, description="Upper bound for Excellent bucket (ms).")
    good_ms: int = Field(..., ge=0, description="Upper bound for Good bucket (ms).")
    average_ms: int = Field(..., ge=0, description="Upper bound for Average bucket (ms).")
    timeout_ms: int = Field(..., ge=0, description="Interaction timeout (ms).")

    @model_validator(mode="after")
    def thresholds_increase(self) -> InteractionThresholds:
        if not (self.excellent_ms <= self.good_ms <= self.average_ms):
            raise ValueError(
                "Thresholds must satisfy excellent_ms <= good_ms <= average_ms"
            )
        return self


class ReportingPeriod(BaseModel):
    start: date = Field(..., description="Reporting period start (ISO 8601 date).")
    end: date = Field(..., description="Reporting period end (ISO 8601 date).")

    @model_validator(mode="after")
    def end_not_before_start(self) -> ReportingPeriod:
        if self.end < self.start:
            raise ValueError("end must be on or after start")
        return self


class InteractionIdentity(BaseModel):
    """Block 1 — what is being measured."""

    name: str = Field(..., description="Interaction span name in Pulse.")
    business_moment: str = Field(
        ...,
        description="Plain-English user action (e.g. User taps Pay → Juspay SDK ready).",
    )
    start_event: str = Field(..., description="Marker start event name.")
    end_event: str = Field(..., description="Marker end event name.")
    thresholds: InteractionThresholds
    reporting_period: ReportingPeriod


class KpiSnapshot(BaseModel):
    """Point-in-time KPI value for the reporting period."""

    metric: PrimaryKpi
    value: float = Field(..., description="Apdex (0–1) or error rate (0–100).")
    display: str | None = Field(
        default=None,
        description="Human-readable value (e.g. '0.42' or '12.3%').",
    )


class HealthVerdict(BaseModel):
    """Block 2 — one-glance status."""

    primary_kpi: KpiSnapshot
    secondary_kpi: KpiSnapshot
    rating: HealthRating = Field(..., description="red / amber / green.")
    summary: str = Field(
        ...,
        min_length=1,
        description="Single sentence a PM can quote.",
    )
    poor_user_pct: float | None = Field(
        default=None,
        ge=0,
        le=100,
        description="Poor-user percentage; used for health rules when present.",
    )

    @model_validator(mode="after")
    def secondary_differs_from_primary(self) -> HealthVerdict:
        if self.primary_kpi.metric == self.secondary_kpi.metric:
            raise ValueError("primary_kpi and secondary_kpi must differ")
        return self


def derive_health_rating(
    *,
    apdex: float | None = None,
    error_rate_pct: float | None = None,
    poor_user_pct: float | None = None,
) -> HealthRating:
    """Apply health rules from interaction-report-template.md."""

    def _red() -> bool:
        if apdex is not None and apdex < 0.50:
            return True
        if error_rate_pct is not None and error_rate_pct > 10:
            return True
        if poor_user_pct is not None and poor_user_pct > 15:
            return True
        return False

    def _green() -> bool:
        return (
            apdex is not None
            and apdex > 0.85
            and (error_rate_pct is None or error_rate_pct < 3)
            and (poor_user_pct is None or poor_user_pct < 5)
        )

    if _red():
        return "red"
    if _green():
        return "green"
    return "amber"


class ExperienceMix(BaseModel):
    excellent_count: int = Field(..., ge=0)
    good_count: int = Field(..., ge=0)
    average_count: int = Field(..., ge=0)
    poor_count: int = Field(..., ge=0)

    @property
    def total(self) -> int:
        return (
            self.excellent_count
            + self.good_count
            + self.average_count
            + self.poor_count
        )

    def pct(self, category: UserExperienceCategory) -> float | None:
        total = self.total
        if total == 0:
            return None
        mapping = {
            "excellent": self.excellent_count,
            "good": self.good_count,
            "average": self.average_count,
            "poor": self.poor_count,
        }
        return round(100.0 * mapping[category] / total, 2)


class SegmentHighlight(BaseModel):
    """Optional Block 3 add-on — top RCA segments in impact language (who hurts most)."""

    label: str = Field(
        ...,
        description="RCA segment label (e.g. 'NetworkProvider: Vi India').",
    )
    volume: int = Field(..., ge=0, description="Interaction volume in this segment.")
    volume_pct_of_total: float | None = Field(
        default=None,
        ge=0,
        le=100,
        description="Segment volume as % of report-period total.",
    )
    poor_user_pct: float | None = Field(
        default=None,
        ge=0,
        le=100,
        description="Poor-user % within this segment.",
    )
    delta_vs_baseline_poor_pct: float | None = Field(
        default=None,
        description="Poor-user % delta vs interaction baseline (RCA deltas.poor_user_pct).",
    )
    error_rate_pct: float | None = Field(
        default=None,
        ge=0,
        le=100,
        description="Error rate within this segment (optional).",
    )
    delta_vs_baseline_error_rate_pct: float | None = Field(
        default=None,
        description="Error-rate delta vs baseline (RCA deltas.error_rate).",
    )
    impact_summary: str = Field(
        ...,
        min_length=1,
        description="One sentence: who is hurt and how disproportionately.",
    )
    rca_rank: int | None = Field(
        default=None,
        ge=1,
        description="RCA serverRank (1 = worst segment); links to Block 6 evidence.",
    )
    dimensions: dict[str, str] | None = Field(
        default=None,
        description="RCA dimension map (e.g. NetworkProvider → Vi India).",
    )


class UserImpact(BaseModel):
    """Block 3 — people and business risk."""

    volume: int = Field(..., ge=0, description="Completed interactions in period.")
    experience_mix: ExperienceMix
    error_rate_pct: float = Field(..., ge=0, le=100)
    failure_count: int = Field(
        ...,
        ge=0,
        description="Incomplete interactions (error rate × volume, rounded).",
    )
    funnel_link: str = Field(
        ...,
        description="Where this sits in browse/checkout journey (one sentence).",
    )
    business_risk: BusinessRisk
    segment_highlights: list[SegmentHighlight] | None = Field(
        default=None,
        max_length=3,
        description=(
            "Optional — top 1–3 RCA segments where impact is concentrated. "
            "Omit when problem is evenly spread or RCA everything_good."
        ),
    )


class FlowPattern(BaseModel):
    """Block 4a — happy path and deviant paths."""

    happy_path: str = Field(
        ...,
        description="Event chain that completes successfully.",
    )
    deviant_paths: list[str] = Field(
        default_factory=list,
        description="Common non-happy paths (back, retry, abandon, etc.).",
    )


class BehavioralSignal(BaseModel):
    """Block 4b — one observed behavioral pattern."""

    signal: str = Field(..., description="Short label (e.g. Back press during span).")
    meaning: str = Field(..., description="What the signal indicates.")
    estimated_frequency: str | None = Field(
        default=None,
        description="Est. frequency from journey volumes or replay sampling.",
    )
    notes: str | None = None
    example: str | None = Field(
        default=None,
        description="Interaction-specific example (optional).",
    )


class BehaviorMetricLink(BaseModel):
    """Block 4c — how user action affects metrics."""

    user_action: str
    effect_on_metrics: str = Field(
        ...,
        description="Effect on Apdex, error rate, or poor UX.",
    )


class CohortBehaviorNote(BaseModel):
    """Block 4d — optional segment-specific behavior."""

    cohort: str = Field(..., description="Segment label (e.g. carrier: Vi India).")
    observation: str


class UserBehavior(BaseModel):
    """Block 4 — what users do during or around the interaction."""

    flow_pattern: FlowPattern
    behavioral_signals: list[BehavioralSignal] = Field(default_factory=list)
    behavior_metric_links: list[BehaviorMetricLink] = Field(default_factory=list)
    cohort_behavior: list[CohortBehaviorNote] | None = Field(
        default=None,
        description="Optional — when segments differ in what users do.",
    )


class Diagnosis(BaseModel):
    """Block 5 — observable facts only; no fixes."""

    reliability: list[str] = Field(
        default_factory=list,
        description="Include when error rate > ~3%.",
    )
    latency: list[str] = Field(
        default_factory=list,
        description="Include when Apdex < 0.7 or poor > 10%.",
    )
    measurement: list[str] = Field(
        default_factory=list,
        description="Include when Apdex vs UX mismatch.",
    )

    @field_validator("reliability", "latency", "measurement", mode="before")
    @classmethod
    def drop_blank_bullets(cls, v: object) -> list[str]:
        if not isinstance(v, list):
            return v
        return [s.strip() for s in v if isinstance(s, str) and s.strip()]


class RootCauseEvidence(BaseModel):
    """One evidence item supporting the root-cause hypothesis."""

    source: Literal["rca_segment", "session_pattern", "journey_path", "other"]
    detail: str


class RootCause(BaseModel):
    """Block 6 — ranked hypotheses with evidence."""

    primary_cause: str = Field(..., min_length=1)
    contributing_factors: list[str] = Field(default_factory=list)
    ruled_out: list[str] | None = Field(
        default=None,
        description="Optional — e.g. crashes, ANRs.",
    )
    evidence: list[RootCauseEvidence] = Field(default_factory=list)
    confidence: ConfidenceLevel


class ImprovementAction(BaseModel):
    """One prioritized action from Block 7."""

    priority: ActionPriority
    action: str = Field(..., min_length=1)
    type: ActionType
    owner: str = Field(..., min_length=1)
    effort: EffortSize
    target_metric: str = Field(
        ...,
        description="Metric to move (e.g. apdex, error_rate, poor_user_pct).",
    )
    expected_lift: str = Field(
        ...,
        description="Expected improvement (qualitative or quantitative).",
    )
    behavior_driven: bool = Field(
        default=False,
        description="True when action addresses a Block 4 behavioral signal.",
    )


class PeriodTargets(BaseModel):
    apdex_min: float | None = Field(default=None, ge=0, le=1)
    error_rate_max_pct: float | None = Field(default=None, ge=0, le=100)
    poor_user_max_pct: float | None = Field(default=None, ge=0, le=100)


class ProofAndFollowUp(BaseModel):
    """Block 8 — sessions, filters, targets, review."""

    sample_bad_session_ids: list[str] = Field(
        ...,
        min_length=1,
        max_length=5,
        description="2–3 session IDs (quality ≈ 0 or interaction error).",
    )
    pulse_drill_down_filters: list[str] = Field(
        default_factory=list,
        description="e.g. NetworkProvider, OsVersion, interaction name.",
    )
    next_period_targets: PeriodTargets
    review_date: date = Field(..., description="Next report or post-release checkpoint.")


class InteractionReportV1(BaseModel):
    """Full per-interaction report — all 8 blocks in order."""

    model_config = ConfigDict(
        json_schema_extra={
            "description": (
                "Per-interaction health report for Pulse. "
                "See docs/interaction-report/interaction-report-template.md."
            ),
        }
    )

    version: int = Field(default=1, ge=1, le=1)
    project_id: str = Field(..., description="Pulse project ID.")
    generated_at: datetime = Field(
        default_factory=lambda: datetime.now(timezone.utc),
        description="Report generation timestamp (UTC).",
    )

    identity: InteractionIdentity
    verdict: HealthVerdict
    user_impact: UserImpact
    user_behavior: UserBehavior
    diagnosis: Diagnosis
    root_cause: RootCause
    actions: list[ImprovementAction] = Field(default_factory=list)
    follow_up: ProofAndFollowUp

    @model_validator(mode="after")
    def at_least_one_diagnosis_lens(self) -> InteractionReportV1:
        d = self.diagnosis
        if not (d.reliability or d.latency or d.measurement):
            raise ValueError(
                "diagnosis must include at least one non-empty lens "
                "(reliability, latency, or measurement)"
            )
        return self


def interaction_report_json_schema() -> dict:
    """Return JSON Schema for LLM structured output / validation."""
    return InteractionReportV1.model_json_schema()

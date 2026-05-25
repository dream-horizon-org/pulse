"""Unit tests for InteractionReportV1 schema and deterministic helpers."""

from __future__ import annotations

from datetime import date

import pytest
from pydantic import ValidationError

from pulse_ai.schemas.interaction_report_helpers import (
    ParadoxKpiHint,
    SegmentHighlightMapper,
    compute_paradox_kpi_hint,
)
from pulse_ai.schemas.interaction_report_v1 import (
    Diagnosis,
    ExperienceMix,
    FlowPattern,
    HealthVerdict,
    InteractionIdentity,
    InteractionReportV1,
    InteractionThresholds,
    KpiSnapshot,
    PeriodTargets,
    ProofAndFollowUp,
    ReportingPeriod,
    RootCause,
    UserBehavior,
    UserImpact,
    derive_health_rating,
    interaction_report_json_schema,
)
from pulse_ai.schemas.interaction_research_v1 import (
    InteractionResearchV1,
    interaction_research_json_schema,
)
from pulse_ai.schemas.root_cause import RootCausePayloadSchema, RootCauseSegmentSchema


def _minimal_report(**overrides: object) -> InteractionReportV1:
    """Build a valid InteractionReportV1 with sensible defaults."""
    base = dict(
        project_id="proj-1",
        identity=InteractionIdentity(
            name="PaymentGatewayHandshakeLatency",
            business_moment="User taps Pay",
            start_event="JUSPAY_INITIATE_REQUEST",
            end_event="JUSPAY_INITIATE_RESULT_SUCCESS",
            thresholds=InteractionThresholds(
                excellent_ms=316,
                good_ms=1400,
                average_ms=2100,
                timeout_ms=20000,
            ),
            reporting_period=ReportingPeriod(
                start=date(2026, 5, 1),
                end=date(2026, 5, 7),
            ),
        ),
        verdict=HealthVerdict(
            primary_kpi=KpiSnapshot(metric="apdex", value=0.72, display="0.72"),
            secondary_kpi=KpiSnapshot(metric="error_rate", value=5.2, display="5.2%"),
            rating="amber",
            summary="Payment handshake is slow for a subset of users.",
            poor_user_pct=12.0,
        ),
        user_impact=UserImpact(
            volume=12000,
            experience_mix=ExperienceMix(
                excellent_count=3000,
                good_count=4000,
                average_count=2000,
                poor_count=3000,
            ),
            error_rate_pct=5.2,
            failure_count=624,
            funnel_link="view_cart → begin_checkout → purchase",
            business_risk="payment_friction",
        ),
        user_behavior=UserBehavior(
            flow_pattern=FlowPattern(
                happy_path="JUSPAY_INITIATE_REQUEST → JUSPAY_INITIATE_RESULT_SUCCESS",
                deviant_paths=["Back press during init"],
            ),
        ),
        diagnosis=Diagnosis(reliability=["Error rate 5.2% exceeds 3% threshold."]),
        root_cause=RootCause(
            primary_cause="Carrier-specific latency on Vi India.",
            confidence="medium",
        ),
        follow_up=ProofAndFollowUp(
            sample_bad_session_ids=["sess-a", "sess-b"],
            next_period_targets=PeriodTargets(apdex_min=0.85, error_rate_max_pct=3.0),
            review_date=date(2026, 5, 14),
        ),
    )
    base.update(overrides)
    return InteractionReportV1.model_validate(base)


class TestInteractionReportV1Schema:
    def test_valid_minimal_report(self) -> None:
        report = _minimal_report()
        assert report.version == 1
        assert report.identity.name == "PaymentGatewayHandshakeLatency"

    def test_threshold_ordering_rejects_invalid(self) -> None:
        with pytest.raises(ValidationError):
            InteractionThresholds(
                excellent_ms=2000,
                good_ms=1000,
                average_ms=3000,
                timeout_ms=20000,
            )

    def test_reporting_period_end_before_start_rejected(self) -> None:
        with pytest.raises(ValidationError):
            ReportingPeriod(start=date(2026, 5, 10), end=date(2026, 5, 1))

    def test_verdict_primary_secondary_must_differ(self) -> None:
        with pytest.raises(ValidationError):
            HealthVerdict(
                primary_kpi=KpiSnapshot(metric="apdex", value=0.5),
                secondary_kpi=KpiSnapshot(metric="apdex", value=0.6),
                rating="amber",
                summary="x",
            )

    def test_diagnosis_requires_non_empty_lens(self) -> None:
        with pytest.raises(ValidationError, match="diagnosis must include"):
            _minimal_report(diagnosis=Diagnosis())

    def test_diagnosis_strips_blank_bullets(self) -> None:
        d = Diagnosis(reliability=["  ok  ", "", "  "])
        assert d.reliability == ["ok"]

    def test_interaction_report_json_schema_exports(self) -> None:
        schema = interaction_report_json_schema()
        assert schema["title"] == "InteractionReportV1"
        assert "properties" in schema

    def test_interaction_research_json_schema_exports(self) -> None:
        schema = interaction_research_json_schema()
        assert schema["title"] == "InteractionResearchV1"

    def test_interaction_research_v1_validates(self) -> None:
        research = InteractionResearchV1(
            project_id="proj-1",
            interaction_name="PaymentGatewayHandshakeLatency",
            reporting_period=ReportingPeriod(
                start=date(2026, 5, 1),
                end=date(2026, 5, 7),
            ),
            journey_summary="Most users complete payment in one attempt.",
            paradox_kpi_hint=ParadoxKpiHint(),
        )
        assert research.version == 1
        assert research.paradox_kpi_hint is not None


class TestDeriveHealthRating:
    def test_red_apdex_below_half(self) -> None:
        assert derive_health_rating(apdex=0.49) == "red"

    def test_red_error_rate_above_ten(self) -> None:
        assert derive_health_rating(error_rate_pct=10.1) == "red"

    def test_red_poor_users_above_fifteen(self) -> None:
        assert derive_health_rating(poor_user_pct=15.1) == "red"

    def test_green_all_conditions_met(self) -> None:
        assert (
            derive_health_rating(apdex=0.86, error_rate_pct=2.9, poor_user_pct=4.9)
            == "green"
        )

    def test_green_apdex_boundary_exclusive(self) -> None:
        assert derive_health_rating(apdex=0.85, error_rate_pct=1.0) == "amber"

    def test_green_error_rate_boundary_exclusive(self) -> None:
        assert derive_health_rating(apdex=0.90, error_rate_pct=3.0) == "amber"

    def test_green_poor_users_boundary_exclusive(self) -> None:
        assert (
            derive_health_rating(apdex=0.90, error_rate_pct=1.0, poor_user_pct=5.0)
            == "amber"
        )

    def test_amber_mixed_signals(self) -> None:
        assert derive_health_rating(apdex=0.70, error_rate_pct=5.0) == "amber"


class TestParadoxKpiHint:
    def test_triggers_when_error_above_three_and_apdex_above_seven_tenths(self) -> None:
        hint = compute_paradox_kpi_hint(apdex=0.71, error_rate_pct=3.1)
        assert hint is not None
        assert hint.primary_kpi == "error_rate"

    def test_no_hint_at_exact_boundaries(self) -> None:
        assert compute_paradox_kpi_hint(apdex=0.7, error_rate_pct=3.0) is None

    def test_no_hint_when_apdex_low(self) -> None:
        assert compute_paradox_kpi_hint(apdex=0.65, error_rate_pct=8.0) is None

    def test_no_hint_when_error_low(self) -> None:
        assert compute_paradox_kpi_hint(apdex=0.90, error_rate_pct=2.0) is None

    def test_no_hint_when_inputs_missing(self) -> None:
        assert compute_paradox_kpi_hint(apdex=None, error_rate_pct=5.0) is None


def _rca_segment(
    label: str,
    *,
    volume: int = 1000,
    poor_pct: float = 20.0,
    error_rate: float = 5.0,
    delta_poor: float = 10.0,
    delta_error: float = 2.0,
    server_rank: int | None = None,
) -> RootCauseSegmentSchema:
    return RootCauseSegmentSchema(
        label=label,
        metrics={
            "volume": volume,
            "poor_user_pct": poor_pct,
            "error_rate": error_rate,
        },
        deltas={"poor_user_pct": delta_poor, "error_rate": delta_error},
        serverRank=server_rank,
    )


class TestSegmentHighlightMapper:
    def test_returns_none_when_everything_good(self) -> None:
        payload = RootCausePayloadSchema(
            baseline={"error_rate": 5.0, "poor_user_pct": 10.0},
            segments=[_rca_segment("Vi India")],
            everythingGood=True,
        )
        assert SegmentHighlightMapper.map_highlights(payload, total_volume=10000) is None

    def test_returns_none_when_no_segments(self) -> None:
        payload = RootCausePayloadSchema(
            baseline={"error_rate": 5.0, "poor_user_pct": 10.0},
            segments=[],
        )
        assert SegmentHighlightMapper.map_highlights(payload, total_volume=10000) is None

    def test_returns_none_when_evenly_spread(self) -> None:
        payload = RootCausePayloadSchema(
            baseline={"error_rate": 5.0, "poor_user_pct": 10.0},
            segments=[
                _rca_segment("Seg A", delta_poor=6.0, delta_error=3.5, server_rank=1),
                _rca_segment("Seg B", delta_poor=5.5, delta_error=3.2, server_rank=2),
            ],
        )
        assert SegmentHighlightMapper.map_highlights(payload, total_volume=10000) is None

    def test_returns_none_when_no_eligible_segments(self) -> None:
        payload = RootCausePayloadSchema(
            baseline={"error_rate": 10.0, "poor_user_pct": 20.0},
            segments=[
                _rca_segment(
                    "At baseline",
                    poor_pct=10.0,
                    error_rate=10.0,
                    delta_poor=0.0,
                    delta_error=0.0,
                ),
            ],
        )
        assert SegmentHighlightMapper.map_highlights(payload, total_volume=10000) is None

    def test_maps_top_outliers_max_three(self) -> None:
        payload = RootCausePayloadSchema(
            baseline={"error_rate": 3.0, "poor_user_pct": 8.0},
            segments=[
                _rca_segment("Vi India", volume=2400, delta_poor=22.0, server_rank=1),
                _rca_segment("Android 13", volume=3600, delta_poor=14.0, server_rank=2),
                _rca_segment("iOS 17", volume=1200, delta_poor=12.0, server_rank=3),
                _rca_segment("WiFi", volume=800, delta_poor=6.0, server_rank=4),
            ],
        )
        highlights = SegmentHighlightMapper.map_highlights(payload, total_volume=12000)
        assert highlights is not None
        assert len(highlights) == 3
        assert highlights[0].label == "Vi India"
        assert highlights[0].rca_rank == 1
        assert highlights[0].volume_pct_of_total == 20.0

    def test_outlier_selection_prefers_server_rank(self) -> None:
        payload = RootCausePayloadSchema(
            baseline={"error_rate": 2.0, "poor_user_pct": 5.0},
            segments=[
                _rca_segment("Low rank big delta", delta_poor=30.0, server_rank=2),
                _rca_segment("High rank smaller delta", delta_poor=18.0, server_rank=1),
            ],
        )
        highlights = SegmentHighlightMapper.map_highlights(payload, total_volume=5000)
        assert highlights is not None
        assert highlights[0].label == "High rank smaller delta"

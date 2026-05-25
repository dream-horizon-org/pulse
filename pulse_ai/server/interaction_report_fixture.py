"""Static InteractionReportV1 fixture for tracer-bullet (issue 02) until live agents ship."""

from __future__ import annotations

from datetime import date, datetime, timezone

from pulse_ai.schemas.interaction_report_v1 import (
    BehaviorMetricLink,
    Diagnosis,
    ExperienceMix,
    FlowPattern,
    HealthVerdict,
    ImprovementAction,
    InteractionIdentity,
    InteractionReportV1,
    InteractionThresholds,
    KpiSnapshot,
    PeriodTargets,
    ProofAndFollowUp,
    ReportingPeriod,
    RootCause,
    RootCauseEvidence,
    UserBehavior,
    UserImpact,
    derive_health_rating,
)


def build_payment_gateway_fixture_report(
    *,
    project_id: str,
    interaction_name: str = "PaymentGatewayHandshakeLatency",
    period_start: date | None = None,
    period_end: date | None = None,
) -> InteractionReportV1:
    """PaymentGatewayHandshakeLatency-shaped stub for E2E tracer bullet."""
    start = period_start or date(2026, 5, 18)
    end = period_end or date(2026, 5, 24)
    apdex = 0.72
    error_rate = 5.2
    poor_pct = 12.0
    rating = derive_health_rating(
        apdex=apdex,
        error_rate_pct=error_rate,
        poor_user_pct=poor_pct,
    )
    return InteractionReportV1(
        project_id=project_id,
        generated_at=datetime.now(timezone.utc),
        identity=InteractionIdentity(
            name=interaction_name,
            business_moment="User taps Pay → Juspay SDK handshake completes",
            start_event="JUSPAY_INITIATE_REQUEST",
            end_event="JUSPAY_INITIATE_RESULT_SUCCESS",
            thresholds=InteractionThresholds(
                excellent_ms=316,
                good_ms=1400,
                average_ms=2100,
                timeout_ms=20000,
            ),
            reporting_period=ReportingPeriod(start=start, end=end),
        ),
        verdict=HealthVerdict(
            primary_kpi=KpiSnapshot(metric="apdex", value=apdex, display="0.72"),
            secondary_kpi=KpiSnapshot(
                metric="error_rate", value=error_rate, display="5.2%"
            ),
            rating=rating,
            summary="Payment handshake is slow for a meaningful subset of users.",
            poor_user_pct=poor_pct,
        ),
        user_impact=UserImpact(
            volume=12000,
            experience_mix=ExperienceMix(
                excellent_count=3000,
                good_count=4000,
                average_count=2000,
                poor_count=3000,
            ),
            error_rate_pct=error_rate,
            failure_count=624,
            funnel_link="Sits between view_cart and begin_checkout in checkout funnel.",
            business_risk="payment_friction",
        ),
        user_behavior=UserBehavior(
            flow_pattern=FlowPattern(
                happy_path="JUSPAY_INITIATE_REQUEST → JUSPAY_INITIATE_RESULT_SUCCESS",
                deviant_paths=["Back press during Juspay init", "Payment method switch"],
            ),
            behavior_metric_links=[
                BehaviorMetricLink(
                    user_action="Back press during init",
                    effect_on_metrics="Correlates with elevated error rate on retry.",
                ),
            ],
        ),
        diagnosis=Diagnosis(
            reliability=["Error rate 5.2% exceeds the 3% reliability threshold."],
            latency=["P95 handshake latency elevated vs excellent threshold (316ms)."],
        ),
        root_cause=RootCause(
            primary_cause="Carrier and OS-specific latency tails concentrate poor experiences.",
            contributing_factors=["Vi India network path", "Android 13 cohort"],
            evidence=[
                RootCauseEvidence(
                    source="rca_segment",
                    detail="NetworkProvider: Vi India — poor user % +22 vs baseline.",
                ),
            ],
            confidence="medium",
        ),
        actions=[
            ImprovementAction(
                priority="P0",
                action="Pre-warm Juspay SDK on checkout entry",
                type="app",
                owner="Mobile",
                effort="M",
                target_metric="apdex",
                expected_lift="Reduce poor bucket by improving init latency",
            ),
        ],
        follow_up=ProofAndFollowUp(
            sample_bad_session_ids=["sess-fixture-1", "sess-fixture-2"],
            pulse_drill_down_filters=["NetworkProvider", "OsVersion", interaction_name],
            next_period_targets=PeriodTargets(
                apdex_min=0.85,
                error_rate_max_pct=3.0,
                poor_user_max_pct=5.0,
            ),
            review_date=end,
        ),
    )

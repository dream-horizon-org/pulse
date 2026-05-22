"""Tests for pulse_ai.server.rca_runner — prompt construction and PII sanitization."""
from __future__ import annotations

from pulse_ai.schemas.rca_structured_v1 import (
    ErrorAttributionInsightV1,
    ErrorAttributionStructuredV1,
    RcaStructuredMetricRowV1,
    RcaStructuredReportV1,
    RcaStructuredSegmentV1,
    RelatedAttributionEntryStructuredV1,
)
from pulse_ai.schemas.root_cause import RootCausePayloadSchema, RootCauseSegmentSchema
from pulse_ai.server.rca_runner import _build_rca_prompt, _sanitize_rca_report


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------

def _minimal_payload() -> RootCausePayloadSchema:
    seg = RootCauseSegmentSchema(label="seg", metrics={}, deltas={})
    return RootCausePayloadSchema(
        baseline={},
        segments=[seg, seg],
        noDataAvailable=True,
        everythingGood=False,
    )


def _metric(label: str = "Volume") -> RcaStructuredMetricRowV1:
    return RcaStructuredMetricRowV1(
        metric_id="volume",
        metric_label=label,
        value_display="1 200",
        baseline_display="1 000",
        delta_display="+20%",
    )


def _segment(
    rank: int = 1,
    title: str = "Segment title",
    insights: str | None = None,
) -> RcaStructuredSegmentV1:
    return RcaStructuredSegmentV1(
        rank=rank,
        title=title,
        metrics=[_metric()],
        insights=insights,
    )


def _plain_report(
    executive_summary: str = "All good.",
    segments: list[RcaStructuredSegmentV1] | None = None,
    recommendations: list[str] | None = None,
) -> RcaStructuredReportV1:
    return RcaStructuredReportV1(
        executive_summary=executive_summary,
        segments=segments or [_segment()],
        recommendations=recommendations or ["Fix it."],
    )


def _full_report_with_attribution(
    summary_override: str | None = None,
    caveat_override: str | None = None,
    url_override: str | None = None,
    title_override: str | None = None,
    rr_undefined_reason_override: str | None = None,
) -> RcaStructuredReportV1:
    insights = [
        ErrorAttributionInsightV1(
            signal="anr",
            summary=summary_override or "No ANR issues.",
            caveat=caveat_override,
        ),
        ErrorAttributionInsightV1(signal="non_fatal", summary="No non-fatal issues."),
        ErrorAttributionInsightV1(signal="api", summary="No API issues."),
    ]
    related = [
        RelatedAttributionEntryStructuredV1(
            sourceSignal="api",
            rowKind="url",
            url=url_override or "https://api.example.com/checkout",
            title=title_override or "Checkout API",
            rrUndefinedReason=rr_undefined_reason_override,
            occurrences=5,
        )
    ]
    attribution = ErrorAttributionStructuredV1(
        disclaimer="Correlative only.",
        relatedAttributions=related,
    )
    return RcaStructuredReportV1(
        executive_summary="Summary.",
        segments=[_segment()],
        recommendations=["Fix it."],
        error_attribution_insights=insights,
        error_attribution=attribution,
    )


# ---------------------------------------------------------------------------
# _build_rca_prompt — prompt ordering
# ---------------------------------------------------------------------------

def test_error_attribution_block_before_session_evidence() -> None:
    prompt = _build_rca_prompt(
        "checkout",
        _minimal_payload(),
        example_session_ids=["sess-a", "sess-b"],
        error_attribution_payload={"relatedAttributions": []},
    )
    rc_idx = prompt.index("RootCausePayload(JSON):")
    attr_idx = prompt.index("ErrorAttributionPayload(JSON):")
    sess_idx = prompt.index("## Session Evidence")
    assert rc_idx < attr_idx < sess_idx


def test_omits_attribution_block_when_none() -> None:
    prompt = _build_rca_prompt(
        "checkout",
        _minimal_payload(),
        example_session_ids=None,
        error_attribution_payload=None,
    )
    assert "ErrorAttributionPayload(JSON)" not in prompt


# ---------------------------------------------------------------------------
# _sanitize_rca_report — free-text fields
# ---------------------------------------------------------------------------

class TestSanitizeRcaReportFreeText:

    def test_executive_summary_email_is_redacted(self):
        report = _plain_report(executive_summary="User admin@corp.com caused 5 crashes.")
        result = _sanitize_rca_report(report)
        assert "admin@corp.com" not in result.executive_summary
        assert "[REDACTED:EMAIL]" in result.executive_summary

    def test_segment_title_email_is_redacted(self):
        report = _plain_report(segments=[_segment(title="Segment for user@example.com")])
        result = _sanitize_rca_report(report)
        assert "user@example.com" not in result.segments[0].title
        assert "[REDACTED:EMAIL]" in result.segments[0].title

    def test_segment_insights_email_is_redacted(self):
        report = _plain_report(
            segments=[_segment(insights="Most affected: user@example.com, 10 sessions.")]
        )
        result = _sanitize_rca_report(report)
        assert "user@example.com" not in result.segments[0].insights
        assert "[REDACTED:EMAIL]" in result.segments[0].insights

    def test_recommendation_email_is_redacted(self):
        report = _plain_report(recommendations=["Notify dev@company.com about the issue."])
        result = _sanitize_rca_report(report)
        assert "dev@company.com" not in result.recommendations[0]
        assert "[REDACTED:EMAIL]" in result.recommendations[0]

    def test_segment_insights_none_stays_none(self):
        report = _plain_report(segments=[_segment(insights=None)])
        result = _sanitize_rca_report(report)
        assert result.segments[0].insights is None


class TestSanitizeRcaReportAttributionFields:

    def test_error_attribution_insight_summary_email_is_redacted(self):
        report = _full_report_with_attribution(
            summary_override="User anr@corp.com triggered ANR 3 times."
        )
        result = _sanitize_rca_report(report)
        assert "anr@corp.com" not in result.error_attribution_insights[0].summary
        assert "[REDACTED:EMAIL]" in result.error_attribution_insights[0].summary

    def test_error_attribution_insight_caveat_email_is_redacted(self):
        report = _full_report_with_attribution(
            caveat_override="Contact support@example.com for details."
        )
        result = _sanitize_rca_report(report)
        assert "support@example.com" not in result.error_attribution_insights[0].caveat
        assert "[REDACTED:EMAIL]" in result.error_attribution_insights[0].caveat

    def test_related_attribution_url_email_in_query_param_is_redacted(self):
        report = _full_report_with_attribution(
            url_override="https://api.example.com/v1?notify=user@example.com&page=1"
        )
        result = _sanitize_rca_report(report)
        url = result.error_attribution.relatedAttributions[0].url
        assert "user@example.com" not in url
        assert "[REDACTED:EMAIL]" in url

    def test_related_attribution_title_email_is_redacted(self):
        report = _full_report_with_attribution(
            title_override="Request by user@example.com"
        )
        result = _sanitize_rca_report(report)
        title = result.error_attribution.relatedAttributions[0].title
        assert "user@example.com" not in title
        assert "[REDACTED:EMAIL]" in title

    def test_rr_undefined_reason_email_is_redacted(self):
        report = _full_report_with_attribution(
            rr_undefined_reason_override="Insufficient data for user@example.com cohort."
        )
        result = _sanitize_rca_report(report)
        reason = result.error_attribution.relatedAttributions[0].rrUndefinedReason
        assert "user@example.com" not in reason
        assert "[REDACTED:EMAIL]" in reason


class TestSanitizeRcaReportMetricFieldsUnchanged:

    def test_metric_display_fields_not_modified(self):
        metric = RcaStructuredMetricRowV1(
            metric_id="volume",
            metric_label="Volume",
            value_display="1 200",
            baseline_display="1 000",
            delta_display="+20%",
        )
        report = RcaStructuredReportV1(
            executive_summary="Summary.",
            segments=[RcaStructuredSegmentV1(rank=1, title="Seg", metrics=[metric])],
            recommendations=["Fix it."],
        )
        result = _sanitize_rca_report(report)
        m = result.segments[0].metrics[0]
        assert m.value_display == "1 200"
        assert m.baseline_display == "1 000"
        assert m.delta_display == "+20%"
        assert m.metric_label == "Volume"


class TestSanitizeRcaReportEdgeCases:

    def test_report_without_error_attribution_does_not_raise(self):
        report = _plain_report()
        result = _sanitize_rca_report(report)
        assert result.error_attribution is None
        assert result.error_attribution_insights is None

    def test_clean_report_returned_unchanged(self):
        report = _plain_report(
            executive_summary="Apdex 0.82, P95 450ms.",
            recommendations=["Reduce query time."],
        )
        result = _sanitize_rca_report(report)
        assert result.executive_summary == "Apdex 0.82, P95 450ms."
        assert result.recommendations[0] == "Reduce query time."

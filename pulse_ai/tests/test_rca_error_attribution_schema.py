"""Schema validation for RCA structured report + error attribution NLP rows."""

from __future__ import annotations

import pytest
from pydantic import ValidationError

from pulse_ai.schemas.rca_structured_v1 import (
    ErrorAttributionInsightV1,
    ErrorAttributionStructuredV1,
    RelatedAttributionEntryStructuredV1,
    RcaStructuredMetricRowV1,
    RcaStructuredReportV1,
    RcaStructuredSegmentV1,
)


def _minimal_segment() -> RcaStructuredSegmentV1:
    row = RcaStructuredMetricRowV1(
        metric_id="volume",
        metric_label="Volume",
        value_display="1",
        baseline_display="1",
        delta_display="0",
        value_number=1.0,
        baseline_number=1.0,
    )
    return RcaStructuredSegmentV1(
        rank=1,
        title="test",
        metrics=[row],
        insights="x",
        affected_sessions=[],
    )


def _valid_insights() -> list[ErrorAttributionInsightV1]:
    return [
        ErrorAttributionInsightV1(signal="anr", summary="a"),
        ErrorAttributionInsightV1(signal="non_fatal", summary="b"),
        ErrorAttributionInsightV1(signal="api", summary="c"),
    ]


def _minimal_drill() -> ErrorAttributionStructuredV1:
    return ErrorAttributionStructuredV1(
        disclaimer="observational only",
        minRiskRatioForIssueAttribution=2.0,
        relatedAttributions=[
            RelatedAttributionEntryStructuredV1(
                sourceSignal="anr",
                rowKind="issue",
                title="t",
                occurrences=1,
            ),
        ],
    )


def test_accepts_three_insights_in_canonical_order_with_drill() -> None:
    report = RcaStructuredReportV1(
        executive_summary="s",
        segments=[_minimal_segment(), _minimal_segment()],
        recommendations=["r1", "r2", "r3"],
        error_attribution_insights=_valid_insights(),
        error_attribution=_minimal_drill(),
    )
    assert report.error_attribution_insights is not None
    assert [x.signal for x in report.error_attribution_insights] == ["anr", "non_fatal", "api"]
    assert report.error_attribution is not None


def test_rejects_insights_without_drill() -> None:
    with pytest.raises(ValidationError):
        RcaStructuredReportV1(
            executive_summary="s",
            segments=[_minimal_segment(), _minimal_segment()],
            recommendations=["r1", "r2", "r3"],
            error_attribution_insights=_valid_insights(),
        )


def test_rejects_drill_without_insights() -> None:
    with pytest.raises(ValidationError):
        RcaStructuredReportV1(
            executive_summary="s",
            segments=[_minimal_segment(), _minimal_segment()],
            recommendations=["r1", "r2", "r3"],
            error_attribution=_minimal_drill(),
        )


def test_rejects_wrong_order() -> None:
    bad = [
        ErrorAttributionInsightV1(signal="non_fatal", summary="b"),
        ErrorAttributionInsightV1(signal="anr", summary="a"),
        ErrorAttributionInsightV1(signal="api", summary="c"),
    ]
    with pytest.raises(ValidationError):
        RcaStructuredReportV1(
            executive_summary="s",
            segments=[_minimal_segment(), _minimal_segment()],
            recommendations=["r1", "r2", "r3"],
            error_attribution_insights=bad,
            error_attribution=_minimal_drill(),
        )


def test_rejects_partial_list_when_non_null() -> None:
    with pytest.raises(ValidationError):
        RcaStructuredReportV1(
            executive_summary="s",
            segments=[_minimal_segment(), _minimal_segment()],
            recommendations=["r1", "r2", "r3"],
            error_attribution_insights=[
                ErrorAttributionInsightV1(signal="anr", summary="only one"),
            ],
            error_attribution=_minimal_drill(),
        )


def test_accepts_null_insights_and_null_drill() -> None:
    RcaStructuredReportV1(
        executive_summary="s",
        segments=[_minimal_segment(), _minimal_segment()],
        recommendations=["r1", "r2", "r3"],
        error_attribution_insights=None,
        error_attribution=None,
    )

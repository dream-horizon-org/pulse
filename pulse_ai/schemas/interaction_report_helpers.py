"""Deterministic helpers for per-interaction health report generation."""

from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field

from pulse_ai.schemas.interaction_report_v1 import SegmentHighlight
from pulse_ai.schemas.root_cause import RootCausePayloadSchema, RootCauseSegmentSchema

PrimaryKpi = Literal["apdex", "error_rate"]

# Minimum delta vs baseline to treat a segment as a meaningful outlier.
_MIN_OUTLIER_DELTA_POOR_PCT = 5.0
_MIN_OUTLIER_DELTA_ERROR_PCT = 3.0
# Top segment must exceed second-place score by this ratio when 2+ eligible.
_OUTLIER_DOMINANCE_RATIO = 1.5


class ParadoxKpiHint(BaseModel):
    """Hint for Agent 2 when Apdex looks healthy but error rate is elevated."""

    primary_kpi: PrimaryKpi = Field(
        default="error_rate",
        description="Suggested primary KPI when paradox condition holds.",
    )
    reason: str = Field(
        default="error_rate > 3% while apdex > 0.7",
        description="Why error_rate should lead the verdict.",
    )


def compute_paradox_kpi_hint(
    *,
    apdex: float | None,
    error_rate_pct: float | None,
) -> ParadoxKpiHint | None:
    """Return hint when error_rate > 3% AND apdex > 0.7 (strict on both)."""
    if apdex is None or error_rate_pct is None:
        return None
    if error_rate_pct > 3 and apdex > 0.7:
        return ParadoxKpiHint()
    return None


def _coerce_float(value: object) -> float:
    if value is None:
        return 0.0
    if isinstance(value, (int, float)):
        return float(value)
    try:
        return float(str(value))
    except (TypeError, ValueError):
        return 0.0


def _sum_error_plus_poor(metrics: dict[str, object]) -> float:
    return _coerce_float(metrics.get("error_rate")) + _coerce_float(
        metrics.get("poor_user_pct")
    )


def _segment_outlier_score(segment: RootCauseSegmentSchema) -> float:
    """Higher score = more problematic vs baseline."""
    delta_poor = abs(_coerce_float(segment.deltas.get("poor_user_pct")))
    delta_error = abs(_coerce_float(segment.deltas.get("error_rate")))
    return delta_poor + delta_error


def _eligible_segments(
    payload: RootCausePayloadSchema,
) -> list[RootCauseSegmentSchema]:
    baseline_sum = _sum_error_plus_poor(payload.baseline)
    eligible: list[RootCauseSegmentSchema] = []
    for segment in payload.segments:
        if _sum_error_plus_poor(segment.metrics) > baseline_sum:
            eligible.append(segment)
    return eligible


def _is_evenly_spread(scores: list[float]) -> bool:
    if len(scores) < 2:
        return False
    ordered = sorted(scores, reverse=True)
    top, second = ordered[0], ordered[1]
    if top <= 0:
        return True
    return (top - second) / top < 0.15 or top < second * _OUTLIER_DOMINANCE_RATIO


def _segment_to_highlight(
    segment: RootCauseSegmentSchema,
    *,
    total_volume: int,
) -> SegmentHighlight:
    volume = int(_coerce_float(segment.metrics.get("volume")))
    volume_pct = round(100.0 * volume / total_volume, 2) if total_volume > 0 else None
    poor_pct = _coerce_float(segment.metrics.get("poor_user_pct"))
    error_pct = _coerce_float(segment.metrics.get("error_rate"))
    delta_poor = segment.deltas.get("poor_user_pct")
    delta_error = segment.deltas.get("error_rate")

    delta_poor_f = _coerce_float(delta_poor) if delta_poor is not None else None
    delta_error_f = _coerce_float(delta_error) if delta_error is not None else None

    impact_parts: list[str] = []
    if volume_pct is not None:
        impact_parts.append(f"~{volume_pct:g}% of volume")
    if delta_poor_f is not None and delta_poor_f > 0:
        impact_parts.append(f"poor UX +{delta_poor_f:g} pts vs baseline")
    elif poor_pct > 0:
        impact_parts.append(f"{poor_pct:g}% poor users")
    impact_summary = "; ".join(impact_parts) if impact_parts else segment.label

    dimensions = None
    if segment.dimensions:
        dimensions = {
            k: v for k, v in segment.dimensions.items() if v is not None
        } or None

    return SegmentHighlight(
        label=segment.label,
        volume=volume,
        volume_pct_of_total=volume_pct,
        poor_user_pct=poor_pct if poor_pct > 0 else None,
        delta_vs_baseline_poor_pct=delta_poor_f,
        error_rate_pct=error_pct if error_pct > 0 else None,
        delta_vs_baseline_error_rate_pct=delta_error_f,
        impact_summary=impact_summary,
        rca_rank=segment.serverRank,
        dimensions=dimensions,
    )


class SegmentHighlightMapper:
    """Map tabular RCA segments to optional Block 3 segment_highlights (max 3)."""

    @staticmethod
    def map_highlights(
        rca_payload: RootCausePayloadSchema,
        *,
        total_volume: int,
    ) -> list[SegmentHighlight] | None:
        """Return highlights when outliers exist; None when evenly spread or healthy."""
        if rca_payload.everythingGood is True or rca_payload.noDataAvailable is True:
            return None
        if not rca_payload.segments:
            return None

        eligible = _eligible_segments(rca_payload)
        if not eligible:
            return None

        scores = [_segment_outlier_score(seg) for seg in eligible]
        if max(scores) < _MIN_OUTLIER_DELTA_POOR_PCT and max(
            abs(_coerce_float(s.deltas.get("error_rate"))) for s in eligible
        ) < _MIN_OUTLIER_DELTA_ERROR_PCT:
            return None
        if _is_evenly_spread(scores):
            return None

        def _sort_key(seg: RootCauseSegmentSchema) -> tuple[int, float]:
            rank = seg.serverRank if seg.serverRank is not None else 9999
            return (rank, -_segment_outlier_score(seg))

        ranked = sorted(eligible, key=_sort_key)[:3]
        return [_segment_to_highlight(seg, total_volume=total_volume) for seg in ranked]


def map_segment_highlights(
    rca_payload: RootCausePayloadSchema,
    *,
    total_volume: int | None = None,
) -> list[SegmentHighlight] | None:
    """Convenience wrapper around :class:`SegmentHighlightMapper`."""
    vol = total_volume
    if vol is None or vol <= 0:
        vol = sum(
            int(_coerce_float(s.metrics.get("volume"))) for s in rca_payload.segments
        ) or 1
    return SegmentHighlightMapper.map_highlights(rca_payload, total_volume=vol)


def paradox_kpi_hint(
    *,
    apdex: float | None,
    error_rate_pct: float | None,
) -> PrimaryKpi | None:
    """Return suggested primary KPI metric id when paradox condition holds."""
    hint = compute_paradox_kpi_hint(apdex=apdex, error_rate_pct=error_rate_pct)
    return hint.primary_kpi if hint is not None else None


# Re-export for single import path (defined on interaction_report_v1).
from pulse_ai.schemas.interaction_report_v1 import derive_health_rating  # noqa: E402,F401

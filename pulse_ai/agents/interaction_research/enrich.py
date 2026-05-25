"""Deterministic enrichment for Agent 1 output (mapper, paradox hint, health rating)."""

from __future__ import annotations

from typing import Any

from pulse_ai.schemas import RootCausePayloadSchema
from pulse_ai.schemas.interaction_report_helpers import (
    compute_paradox_kpi_hint,
    map_segment_highlights,
)
from pulse_ai.schemas.interaction_report_v1 import derive_health_rating
from pulse_ai.schemas.interaction_research_v1 import InteractionResearchV1


def _coerce_float(value: object) -> float | None:
    if value is None:
        return None
    if isinstance(value, (int, float)):
        return float(value)
    try:
        return float(str(value))
    except (TypeError, ValueError):
        return None


def _metric_row(metrics_payload: dict[str, Any] | None) -> dict[str, Any] | None:
    if not metrics_payload:
        return None
    data = metrics_payload.get("data")
    if isinstance(data, list) and data:
        row = data[0]
        return row if isinstance(row, dict) else None
    if isinstance(data, dict):
        return data
    return None


def _extract_metric_values(
    metrics_payload: dict[str, Any] | None,
) -> tuple[float | None, float | None, float | None]:
    """Return (apdex, error_rate_pct, poor_user_pct) from composite tool payload."""
    row = _metric_row(metrics_payload)
    if not row:
        return None, None, None

    apdex = _coerce_float(row.get("apdex"))
    success = _coerce_float(row.get("success_count"))
    errors = _coerce_float(row.get("error_count"))
    error_rate = None
    if success is not None and errors is not None and (success + errors) > 0:
        error_rate = 100.0 * errors / (success + errors)

    poor = _coerce_float(row.get("user_poor"))
    total_users = sum(
        _coerce_float(row.get(k)) or 0.0
        for k in ("user_excellent", "user_good", "user_avg", "user_poor")
    )
    poor_pct = None
    if poor is not None and total_users > 0:
        poor_pct = 100.0 * poor / total_users

    return apdex, error_rate, poor_pct


def enrich_interaction_research(research: InteractionResearchV1) -> InteractionResearchV1:
    """Apply SegmentHighlightMapper and deterministic hints from tool payloads."""
    updates: dict[str, Any] = {}

    if research.rca_payload:
        try:
            rca = RootCausePayloadSchema.model_validate(research.rca_payload)
            updates["segment_highlights"] = map_segment_highlights(rca)
        except Exception:
            pass

    apdex, error_rate, poor_pct = _extract_metric_values(research.metrics_payload)
    updates["paradox_kpi_hint"] = compute_paradox_kpi_hint(
        apdex=apdex,
        error_rate_pct=error_rate,
    )
    updates["health_rating"] = derive_health_rating(
        apdex=apdex,
        error_rate_pct=error_rate,
        poor_user_pct=poor_pct,
    )

    return research.model_copy(update=updates)

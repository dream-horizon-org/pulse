from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field


# ---------------------------------------------------------------------------
# Day-level insight (output of POST /insight/anr/day)
# Produced per day; all numeric fields are passed through to the merge step so
# the merge agent can sum counts and recompute rates rather than averaging them.
# ---------------------------------------------------------------------------

class AnrDayInsightV1(BaseModel):
    """Structured summary for a single day's ANR snapshot."""

    date: str = Field(description="ISO date string, e.g. '2026-05-01'")

    # Raw numeric totals — must be passed through unchanged for merge aggregation
    anr_count: int = Field(ge=0)
    total_sessions: int = Field(ge=0)
    affected_sessions: int = Field(ge=0)
    affected_users: int = Field(ge=0)
    total_spans: int = Field(ge=0)

    # Derived / display
    anr_session_rate: float = Field(
        ge=0.0,
        description="affected_sessions / total_sessions × 100; 0.0 when total_sessions=0",
    )

    summary: str = Field(
        description="2–3 sentences describing this day's ANR activity and notable findings.",
    )
    worst_dimension: str | None = Field(
        default=None,
        description=(
            "Label of the (Platform × AppVersion × OsVersion × DeviceModel) combo with the "
            "highest anr_rate, e.g. 'Android / 9.7.0 / 8.1.0 / vivo 1820 (rate=3.2%)'. "
            "Null when no breakdown data is available."
        ),
    )
    top_exception_signature: str | None = Field(
        default=None,
        description=(
            "Brief label of the most frequent ANR exception group, "
            "e.g. 'NPE at ViewGroup#dispatchWindowVisibilityChanged (100 hits)'. Null when absent."
        ),
    )
    trend_signal: Literal["worsening", "stable", "improving", "no_data"] = Field(
        default="no_data",
        description=(
            "Day-level signal: 'no_data' when total_sessions=0; "
            "'worsening' when anr_session_rate > 3%, 'improving' when < 0.5%, else 'stable'."
        ),
    )

    # Raw breakdown rows — included verbatim so the merge agent can re-aggregate
    dimension_breakdown: list[dict] = Field(
        default_factory=list,
        description="Pass-through of the dimension_breakdown array from the input snapshot.",
    )
    top_anr_groups: list[dict] = Field(
        default_factory=list,
        description="Pass-through of the top_anr_groups array from the input snapshot.",
    )


# ---------------------------------------------------------------------------
# Merge report (output of POST /insight/anr/merge)
# Final 30-day summary stored in InsightReportCacheDao and returned to the UI.
# ---------------------------------------------------------------------------

class AnrTopDimensionV1(BaseModel):
    """Aggregated ANR metrics for one (Platform × AppVersion × OsVersion × DeviceModel) combo."""

    label: str = Field(
        description="Human-readable key, e.g. 'Android / 9.7.0 / 8.1.0 / vivo 1820'",
    )
    total_anr_count: int = Field(ge=0, description="SUM of anr_count across all days")
    total_spans: int = Field(ge=0, description="SUM of spans across all days")
    anr_rate: float = Field(
        ge=0.0,
        description="total_anr_count / total_spans × 100 (NOT an average of daily rates)",
    )


class AnrTopGroupV1(BaseModel):
    """Aggregated stats for one ANR exception group across the date range."""

    signature: str
    exception_type: str
    total_occurrences: int = Field(ge=0)
    total_affected_sessions: int = Field(ge=0)
    top_screens: list[str] = Field(default_factory=list)
    top_device_models: list[str] = Field(default_factory=list)
    insight: str = Field(description="1–2 sentences explaining the impact of this exception group.")


class AnrInsightReportV1(BaseModel):
    """Final ANR insight report for a date range. Stored verbatim in InsightReportCacheDao."""

    version: int = Field(default=1)
    entity_key: str
    start_date: str
    end_date: str

    executive_summary: str = Field(
        description="3–4 sentences: overall ANR health, trend direction, most impactful finding.",
    )

    # Aggregate totals — computed by SUMMING day values, never averaging rates
    total_anr_count: int = Field(ge=0)
    total_sessions: int = Field(ge=0, description="SUM of daily total_sessions")
    total_affected_sessions: int = Field(ge=0, description="SUM of daily affected_sessions")
    overall_anr_session_rate: float = Field(
        ge=0.0,
        description="total_affected_sessions / total_sessions × 100 (recomputed, not averaged)",
    )

    trend: Literal["worsening", "stable", "improving", "insufficient_data"] = Field(
        description=(
            "Direction over the date range: compare avg anr_session_rate of first-7-days "
            "vs last-7-days. 'insufficient_data' when fewer than 3 days have sessions."
        ),
    )
    peak_day: str | None = Field(
        default=None,
        description="ISO date of the day with the highest anr_count.",
    )

    top_dimensions: list[AnrTopDimensionV1] = Field(
        description="Top dimension combos by total_anr_count, descending. Up to 10.",
    )
    top_exception_groups: list[AnrTopGroupV1] = Field(
        description="Top ANR exception groups by total_occurrences, descending. Up to 10.",
    )

    recommendations: list[str] = Field(
        min_length=3,
        description="3–5 short, actionable recommendations derived from the findings.",
    )

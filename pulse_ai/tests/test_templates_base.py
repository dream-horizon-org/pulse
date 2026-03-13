"""Tests for templates/base.py — time range, filters, QueryRequest builder.

TDD RED: All tests written before pulse_ai/templates/base.py exists.
"""

import pytest
from freezegun import freeze_time


# ===================================================================
# compute_time_range() tests
# ===================================================================


@freeze_time("2026-03-09T12:00:00Z")
def test_compute_time_range_last_24h():
    """last_24h returns (now - 24h, now) in ISO 8601 with Z suffix."""
    from pulse_ai.templates.base import compute_time_range

    start, end = compute_time_range("last_24h")

    assert end == "2026-03-09T12:00:00Z"
    assert start == "2026-03-08T12:00:00Z"


@freeze_time("2026-03-09T12:00:00Z")
def test_compute_time_range_last_1h():
    """last_1h returns correct 1-hour delta."""
    from pulse_ai.templates.base import compute_time_range

    start, end = compute_time_range("last_1h")

    assert end == "2026-03-09T12:00:00Z"
    assert start == "2026-03-09T11:00:00Z"


@freeze_time("2026-03-09T12:00:00Z")
def test_compute_time_range_last_6h():
    """last_6h returns correct 6-hour delta."""
    from pulse_ai.templates.base import compute_time_range

    start, end = compute_time_range("last_6h")

    assert end == "2026-03-09T12:00:00Z"
    assert start == "2026-03-09T06:00:00Z"


@freeze_time("2026-03-09T12:00:00Z")
def test_compute_time_range_last_7d():
    """last_7d returns correct 7-day delta."""
    from pulse_ai.templates.base import compute_time_range

    start, end = compute_time_range("last_7d")

    assert end == "2026-03-09T12:00:00Z"
    assert start == "2026-03-02T12:00:00Z"


@freeze_time("2026-03-09T12:00:00Z")
def test_compute_time_range_last_30d():
    """last_30d returns correct 30-day delta."""
    from pulse_ai.templates.base import compute_time_range

    start, end = compute_time_range("last_30d")

    assert end == "2026-03-09T12:00:00Z"
    assert start == "2026-02-07T12:00:00Z"


# --- New simple delta ranges ---


@freeze_time("2026-03-09T12:00:00Z")
def test_compute_time_range_last_5m():
    """last_5m returns correct 5-minute delta."""
    from pulse_ai.templates.base import compute_time_range

    start, end = compute_time_range("last_5m")

    assert end == "2026-03-09T12:00:00Z"
    assert start == "2026-03-09T11:55:00Z"


@freeze_time("2026-03-09T12:00:00Z")
def test_compute_time_range_last_15m():
    """last_15m returns correct 15-minute delta."""
    from pulse_ai.templates.base import compute_time_range

    start, end = compute_time_range("last_15m")

    assert end == "2026-03-09T12:00:00Z"
    assert start == "2026-03-09T11:45:00Z"


@freeze_time("2026-03-09T12:00:00Z")
def test_compute_time_range_last_30m():
    """last_30m returns correct 30-minute delta."""
    from pulse_ai.templates.base import compute_time_range

    start, end = compute_time_range("last_30m")

    assert end == "2026-03-09T12:00:00Z"
    assert start == "2026-03-09T11:30:00Z"


@freeze_time("2026-03-09T12:00:00Z")
def test_compute_time_range_last_3h():
    """last_3h returns correct 3-hour delta."""
    from pulse_ai.templates.base import compute_time_range

    start, end = compute_time_range("last_3h")

    assert end == "2026-03-09T12:00:00Z"
    assert start == "2026-03-09T09:00:00Z"


@freeze_time("2026-03-09T12:00:00Z")
def test_compute_time_range_last_12h():
    """last_12h returns correct 12-hour delta."""
    from pulse_ai.templates.base import compute_time_range

    start, end = compute_time_range("last_12h")

    assert end == "2026-03-09T12:00:00Z"
    assert start == "2026-03-09T00:00:00Z"


@freeze_time("2026-03-09T12:00:00Z")
def test_compute_time_range_last_2d():
    """last_2d returns correct 2-day delta."""
    from pulse_ai.templates.base import compute_time_range

    start, end = compute_time_range("last_2d")

    assert end == "2026-03-09T12:00:00Z"
    assert start == "2026-03-07T12:00:00Z"


@freeze_time("2026-03-09T12:00:00Z")
def test_compute_time_range_last_90d():
    """last_90d returns correct 90-day delta."""
    from pulse_ai.templates.base import compute_time_range

    start, end = compute_time_range("last_90d")

    assert end == "2026-03-09T12:00:00Z"
    assert start == "2025-12-09T12:00:00Z"


# --- Calendar-relative ranges ---


@freeze_time("2026-03-09T14:30:00Z")  # a Monday
def test_compute_time_range_yesterday():
    """yesterday returns start-of-yesterday to end-of-yesterday."""
    from pulse_ai.templates.base import compute_time_range

    start, end = compute_time_range("yesterday")

    assert start == "2026-03-08T00:00:00Z"
    assert end == "2026-03-08T23:59:59Z"


@freeze_time("2026-03-11T14:30:00Z")  # a Wednesday
def test_compute_time_range_previous_week():
    """previous_week returns Monday 00:00 to Sunday 23:59:59 of last week."""
    from pulse_ai.templates.base import compute_time_range

    start, end = compute_time_range("previous_week")

    # Previous week: Mon Mar 2 to Sun Mar 8
    assert start == "2026-03-02T00:00:00Z"
    assert end == "2026-03-08T23:59:59Z"


@freeze_time("2026-03-15T14:30:00Z")  # mid-March
def test_compute_time_range_previous_month():
    """previous_month returns first-of-last-month to last-day-of-last-month."""
    from pulse_ai.templates.base import compute_time_range

    start, end = compute_time_range("previous_month")

    assert start == "2026-02-01T00:00:00Z"
    assert end == "2026-02-28T23:59:59Z"


@freeze_time("2026-03-09T14:30:00Z")
def test_compute_time_range_today_so_far():
    """today_so_far returns start-of-today to now."""
    from pulse_ai.templates.base import compute_time_range

    start, end = compute_time_range("today_so_far")

    assert start == "2026-03-09T00:00:00Z"
    assert end == "2026-03-09T14:30:00Z"


@freeze_time("2026-03-11T14:30:00Z")  # Wednesday
def test_compute_time_range_this_week():
    """this_week returns start-of-this-week (Monday) to now."""
    from pulse_ai.templates.base import compute_time_range

    start, end = compute_time_range("this_week")

    assert start == "2026-03-09T00:00:00Z"  # Monday
    assert end == "2026-03-11T14:30:00Z"


@freeze_time("2026-03-15T14:30:00Z")
def test_compute_time_range_this_month_so_far():
    """this_month_so_far returns first-of-month to now."""
    from pulse_ai.templates.base import compute_time_range

    start, end = compute_time_range("this_month_so_far")

    assert start == "2026-03-01T00:00:00Z"
    assert end == "2026-03-15T14:30:00Z"


def test_compute_time_range_custom_passthrough():
    """custom time_range passes start_time and end_time through as-is."""
    from pulse_ai.templates.base import compute_time_range

    start, end = compute_time_range(
        "custom",
        start_time="2026-03-01T00:00:00Z",
        end_time="2026-03-05T23:59:59Z",
    )

    assert start == "2026-03-01T00:00:00Z"
    assert end == "2026-03-05T23:59:59Z"


def test_compute_time_range_custom_missing_times():
    """custom time_range without start/end raises ValueError."""
    from pulse_ai.templates.base import compute_time_range

    with pytest.raises(ValueError, match="start_time.*end_time"):
        compute_time_range("custom")


def test_compute_time_range_invalid_enum():
    """Unknown time_range enum raises ValueError."""
    from pulse_ai.templates.base import compute_time_range

    with pytest.raises(ValueError, match="last_99d"):
        compute_time_range("last_99d")


@freeze_time("2026-03-09T12:00:00Z")
def test_compute_time_range_always_utc():
    """Output always ends with Z, never +00:00."""
    from pulse_ai.templates.base import compute_time_range

    start, end = compute_time_range("last_24h")

    assert end.endswith("Z")
    assert start.endswith("Z")
    assert "+00:00" not in start
    assert "+00:00" not in end


# ===================================================================
# build_filters() tests
# ===================================================================


def test_build_filters_single_kv():
    """Single key-value translates to one backend filter object."""
    from pulse_ai.templates.base import build_filters

    result = build_filters({"platform": "Android"})

    assert result == [{"field": "Platform", "operator": "EQ", "value": ["Android"]}]


def test_build_filters_multiple_kv():
    """Multiple key-values produce multiple filter objects."""
    from pulse_ai.templates.base import build_filters

    result = build_filters({"platform": "Android", "app_version": "5.29.1"})

    assert len(result) == 2
    fields = [f["field"] for f in result]
    assert "Platform" in fields
    assert "AppVersion" in fields


def test_build_filters_list_value():
    """List value is preserved as-is (multi-value filter)."""
    from pulse_ai.templates.base import build_filters

    result = build_filters({"platform": ["Android", "iOS"]})

    assert result[0]["value"] == ["Android", "iOS"]


def test_build_filters_empty_none():
    """None or empty dict returns empty list."""
    from pulse_ai.templates.base import build_filters

    assert build_filters(None) == []
    assert build_filters({}) == []


def test_build_filters_unknown_key():
    """Unknown filter key raises ValueError."""
    from pulse_ai.templates.base import build_filters

    with pytest.raises(ValueError, match="unknown_field"):
        build_filters({"unknown_field": "value"})


# ===================================================================
# build_query_request() tests
# ===================================================================


@freeze_time("2026-03-09T12:00:00Z")
def test_build_query_request_minimal():
    """Minimal QueryRequest has dataType, timeRange, select."""
    from pulse_ai.templates.base import build_query_request

    result = build_query_request(
        select=[{"function": "APDEX", "alias": "apdex"}],
        time_range="last_24h",
    )

    assert result["dataType"] == "TRACES"
    assert "timeRange" in result
    assert result["timeRange"]["start"] == "2026-03-08T12:00:00Z"
    assert result["timeRange"]["end"] == "2026-03-09T12:00:00Z"
    assert result["select"] == [{"function": "APDEX", "alias": "apdex"}]


@freeze_time("2026-03-09T12:00:00Z")
def test_build_query_request_with_user_filters():
    """User filters are merged into the request filters list."""
    from pulse_ai.templates.base import build_query_request

    result = build_query_request(
        select=[{"function": "APDEX", "alias": "apdex"}],
        time_range="last_24h",
        user_filters={"platform": "Android"},
    )

    filter_fields = [f["field"] for f in result["filters"]]
    assert "Platform" in filter_fields


@freeze_time("2026-03-09T12:00:00Z")
def test_build_query_request_injects_pulse_type():
    """Interaction queries always include PulseType=interaction base filter."""
    from pulse_ai.templates.base import build_query_request

    result = build_query_request(
        select=[{"function": "APDEX", "alias": "apdex"}],
        time_range="last_24h",
        inject_interaction_filters=True,
    )

    pulse_type_filters = [
        f for f in result["filters"]
        if f["field"] == "PulseType"
    ]
    assert len(pulse_type_filters) == 1
    assert pulse_type_filters[0]["value"] == ["interaction"]


@freeze_time("2026-03-09T12:00:00Z")
def test_build_query_request_injects_span_name():
    """When interaction_name given, SpanName filter is auto-injected."""
    from pulse_ai.templates.base import build_query_request

    result = build_query_request(
        select=[{"function": "APDEX", "alias": "apdex"}],
        time_range="last_24h",
        inject_interaction_filters=True,
        interaction_name="ContestJoinSuccess",
    )

    span_filters = [
        f for f in result["filters"]
        if f["field"] == "SpanName"
    ]
    assert len(span_filters) == 1
    assert span_filters[0]["value"] == ["ContestJoinSuccess"]


@freeze_time("2026-03-09T12:00:00Z")
def test_build_query_request_no_tenant_id():
    """Output never contains tenantId field."""
    from pulse_ai.templates.base import build_query_request

    result = build_query_request(
        select=[{"function": "APDEX", "alias": "apdex"}],
        time_range="last_24h",
    )

    assert "tenantId" not in result

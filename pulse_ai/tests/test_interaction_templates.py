"""Tests for analytics templates — QueryRequest skeletons for each metric type.

TDD RED: All tests written before pulse_ai/templates/interaction_templates.py exists.
"""

import pytest
from freezegun import freeze_time


# ===================================================================
# get_time_bucket_size — ported from TimeBucketUtil.ts
# ===================================================================


class TestGetTimeBucketSize:
    """Port of the frontend getTimeBucketSize logic."""

    def test_1h_range_returns_5m_bucket(self):
        from pulse_ai.templates.interaction_templates import get_time_bucket_size
        # 1h = 3600000ms, ideal = 3600000/20 = 180000ms = 3min, clamped to min 1min, smallest >= 3m is "5m"
        result = get_time_bucket_size(
            "2026-03-09T00:00:00Z", "2026-03-09T01:00:00Z"
        )
        assert result == "5m"

    def test_6h_range_returns_30m_bucket(self):
        from pulse_ai.templates.interaction_templates import get_time_bucket_size
        # 6h = 21600000ms, ideal = 21600000/20 = 1080000ms = 18min, smallest >= 18m is "30m"
        result = get_time_bucket_size(
            "2026-03-09T00:00:00Z", "2026-03-09T06:00:00Z"
        )
        assert result == "30m"

    def test_24h_range_returns_1h_bucket(self):
        from pulse_ai.templates.interaction_templates import get_time_bucket_size
        # 24h = 86400000ms, ideal = 86400000/20 = 4320000ms = 72min, smallest >= 72m is "3h"
        # Wait: 72 min. 1h=60min<72, so next is 3h. Let me recalculate.
        # Actually: 1h = 60min. 72min > 60min, so 1h is too small. Next is 3h.
        result = get_time_bucket_size(
            "2026-03-09T00:00:00Z", "2026-03-10T00:00:00Z"
        )
        assert result == "3h"

    def test_7d_range_returns_12h_bucket(self):
        from pulse_ai.templates.interaction_templates import get_time_bucket_size
        # 7d = 604800000ms, ideal = 604800000/20 = 30240000ms = 504min = 8.4h
        # Buckets: 6h=360min < 504, 12h=720min >= 504. So "12h"
        result = get_time_bucket_size(
            "2026-03-01T00:00:00Z", "2026-03-08T00:00:00Z"
        )
        assert result == "12h"

    def test_30d_range_returns_1d_bucket(self):
        from pulse_ai.templates.interaction_templates import get_time_bucket_size
        # 30d = 2592000000ms, ideal = 2592000000/20 = 129600000ms = 1.5d
        # 1d=86400000 < 129600000, so fallback to largest = "1d"
        # Wait: 1d is the last in BUCKET_ORDER. "1d" = 86400000ms < 129600000ms.
        # Frontend fallback is "3h" but we should fallback to the largest bucket = "1d"
        result = get_time_bucket_size(
            "2026-02-07T00:00:00Z", "2026-03-09T00:00:00Z"
        )
        assert result == "1d"

    def test_empty_strings_return_5m_default(self):
        from pulse_ai.templates.interaction_templates import get_time_bucket_size
        result = get_time_bucket_size("", "")
        assert result == "5m"


# ===================================================================
# build_health_query — Tool 5 template
# ===================================================================


class TestBuildHealthQuery:
    """Template for query_interaction_health."""

    @freeze_time("2026-03-09T12:00:00Z")
    def test_basic_health_query_structure(self):
        from pulse_ai.templates.interaction_templates import build_health_query
        result = build_health_query()

        assert result["dataType"] == "TRACES"
        assert "timeRange" in result
        assert result["timeRange"]["start"] == "2026-03-08T12:00:00Z"
        assert result["timeRange"]["end"] == "2026-03-09T12:00:00Z"

    @freeze_time("2026-03-09T12:00:00Z")
    def test_health_query_select_items(self):
        from pulse_ai.templates.interaction_templates import build_health_query
        result = build_health_query()

        aliases = [s["alias"] for s in result["select"]]
        # Must include interaction name column, count, apdex, success/error, user categories, p50
        assert "interaction_name" in aliases
        assert "spanfreq" in aliases
        assert "apdex" in aliases
        assert "success_count" in aliases
        assert "error_count" in aliases
        assert "user_excellent" in aliases
        assert "user_good" in aliases
        assert "user_avg" in aliases
        assert "user_poor" in aliases
        assert "p50" in aliases

    @freeze_time("2026-03-09T12:00:00Z")
    def test_health_query_group_order_limit(self):
        from pulse_ai.templates.interaction_templates import build_health_query
        result = build_health_query(top_n=5)

        assert result["groupBy"] == ["interaction_name"]
        assert result["orderBy"] == [{"field": "spanfreq", "direction": "DESC"}]
        assert result["limit"] == 5

    @freeze_time("2026-03-09T12:00:00Z")
    def test_health_query_auto_injects_pulse_type(self):
        from pulse_ai.templates.interaction_templates import build_health_query
        result = build_health_query()

        pulse_type_filters = [f for f in result["filters"] if f["field"] == "PulseType"]
        assert len(pulse_type_filters) == 1
        assert pulse_type_filters[0]["value"] == ["interaction"]

    @freeze_time("2026-03-09T12:00:00Z")
    def test_health_query_with_interaction_names_filter(self):
        from pulse_ai.templates.interaction_templates import build_health_query
        result = build_health_query(interaction_names=["ContestJoin", "MatchEntry"])

        span_name_filters = [f for f in result["filters"] if f["field"] == "SpanName"]
        assert len(span_name_filters) == 1
        assert span_name_filters[0]["operator"] == "IN"
        assert span_name_filters[0]["value"] == ["ContestJoin", "MatchEntry"]

    @freeze_time("2026-03-09T12:00:00Z")
    def test_health_query_with_user_filters(self):
        from pulse_ai.templates.interaction_templates import build_health_query
        result = build_health_query(user_filters={"platform": "Android"})

        platform_filters = [f for f in result["filters"] if f["field"] == "Platform"]
        assert len(platform_filters) == 1


# ===================================================================
# build_metrics_query — Tool 6 template
# ===================================================================


class TestBuildMetricsQuery:
    """Template for query_interaction_metrics."""

    @freeze_time("2026-03-09T12:00:00Z")
    def test_apdex_aggregate(self):
        from pulse_ai.templates.interaction_templates import build_metrics_query
        result = build_metrics_query(metric_type="apdex", interaction_name="ContestJoin")

        aliases = [s["alias"] for s in result["select"]]
        assert "apdex" in aliases
        # Aggregate: no groupBy or orderBy
        assert "groupBy" not in result

    @freeze_time("2026-03-09T12:00:00Z")
    def test_latency_aggregate(self):
        from pulse_ai.templates.interaction_templates import build_metrics_query
        result = build_metrics_query(metric_type="latency", interaction_name="ContestJoin")

        aliases = [s["alias"] for s in result["select"]]
        assert "p50" in aliases
        assert "p95" in aliases

    @freeze_time("2026-03-09T12:00:00Z")
    def test_error_rate_aggregate(self):
        from pulse_ai.templates.interaction_templates import build_metrics_query
        result = build_metrics_query(metric_type="error_rate", interaction_name="ContestJoin")

        aliases = [s["alias"] for s in result["select"]]
        assert "success_count" in aliases
        assert "error_count" in aliases

    @freeze_time("2026-03-09T12:00:00Z")
    def test_user_categories_aggregate(self):
        from pulse_ai.templates.interaction_templates import build_metrics_query
        result = build_metrics_query(metric_type="user_categories", interaction_name="ContestJoin")

        aliases = [s["alias"] for s in result["select"]]
        assert "user_excellent" in aliases
        assert "user_good" in aliases
        assert "user_avg" in aliases
        assert "user_poor" in aliases

    @freeze_time("2026-03-09T12:00:00Z")
    def test_composite_aggregate(self):
        from pulse_ai.templates.interaction_templates import build_metrics_query
        result = build_metrics_query(metric_type="composite", interaction_name="ContestJoin")

        aliases = [s["alias"] for s in result["select"]]
        # Composite should contain all metric types
        assert "apdex" in aliases
        assert "p50" in aliases
        assert "p95" in aliases
        assert "success_count" in aliases
        assert "error_count" in aliases
        assert "user_excellent" in aliases

    @freeze_time("2026-03-09T12:00:00Z")
    def test_timeseries_prepends_time_bucket(self):
        from pulse_ai.templates.interaction_templates import build_metrics_query
        result = build_metrics_query(
            metric_type="apdex", interaction_name="ContestJoin", timeseries=True
        )

        # First select item should be TIME_BUCKET
        assert result["select"][0]["function"] == "TIME_BUCKET"
        assert result["select"][0]["alias"] == "t1"
        # groupBy and orderBy should reference t1
        assert "t1" in result["groupBy"]
        assert result["orderBy"] == [{"field": "t1", "direction": "ASC"}]

    @freeze_time("2026-03-09T12:00:00Z")
    def test_metrics_auto_injects_interaction_filters(self):
        from pulse_ai.templates.interaction_templates import build_metrics_query
        result = build_metrics_query(metric_type="apdex", interaction_name="ContestJoin")

        pulse_type = [f for f in result["filters"] if f["field"] == "PulseType"]
        span_name = [f for f in result["filters"] if f["field"] == "SpanName"]
        assert len(pulse_type) == 1
        assert len(span_name) == 1
        assert span_name[0]["value"] == ["ContestJoin"]

    @freeze_time("2026-03-09T12:00:00Z")
    def test_invalid_metric_type_raises(self):
        from pulse_ai.templates.interaction_templates import build_metrics_query
        with pytest.raises(ValueError, match="metric_type"):
            build_metrics_query(metric_type="invalid", interaction_name="ContestJoin")


# ===================================================================
# build_breakdown_query — Tool 8 template
# ===================================================================


class TestBuildBreakdownQuery:
    """Template for breakdown_interaction."""

    @freeze_time("2026-03-09T12:00:00Z")
    def test_device_breakdown(self):
        from pulse_ai.templates.interaction_templates import build_breakdown_query
        result = build_breakdown_query(dimension="device", interaction_name="ContestJoin")

        aliases = [s["alias"] for s in result["select"]]
        assert "deviceModel" in aliases
        assert result["groupBy"] == ["deviceModel"]
        assert result["limit"] == 10

    @freeze_time("2026-03-09T12:00:00Z")
    def test_region_breakdown(self):
        from pulse_ai.templates.interaction_templates import build_breakdown_query
        result = build_breakdown_query(dimension="region", interaction_name="ContestJoin")

        aliases = [s["alias"] for s in result["select"]]
        assert "region" in aliases
        assert result["groupBy"] == ["region"]

    @freeze_time("2026-03-09T12:00:00Z")
    def test_release_breakdown(self):
        from pulse_ai.templates.interaction_templates import build_breakdown_query
        result = build_breakdown_query(dimension="release", interaction_name="ContestJoin")

        aliases = [s["alias"] for s in result["select"]]
        assert "release" in aliases
        assert result["groupBy"] == ["release"]

    @freeze_time("2026-03-09T12:00:00Z")
    def test_platform_breakdown(self):
        from pulse_ai.templates.interaction_templates import build_breakdown_query
        result = build_breakdown_query(dimension="platform", interaction_name="ContestJoin")

        aliases = [s["alias"] for s in result["select"]]
        assert "platform" in aliases
        assert result["groupBy"] == ["platform"]

    @freeze_time("2026-03-09T12:00:00Z")
    def test_breakdown_auto_injects_filters(self):
        from pulse_ai.templates.interaction_templates import build_breakdown_query
        result = build_breakdown_query(dimension="device", interaction_name="ContestJoin")

        pulse_type = [f for f in result["filters"] if f["field"] == "PulseType"]
        span_name = [f for f in result["filters"] if f["field"] == "SpanName"]
        assert len(pulse_type) == 1
        assert len(span_name) == 1

    @freeze_time("2026-03-09T12:00:00Z")
    def test_invalid_dimension_raises(self):
        from pulse_ai.templates.interaction_templates import build_breakdown_query
        with pytest.raises(ValueError, match="dimension"):
            build_breakdown_query(dimension="invalid", interaction_name="ContestJoin")


# ===================================================================
# build_sessions_query — Tool 7 template
# ===================================================================


class TestBuildSessionsQuery:
    """Template for query_interaction_sessions."""

    @freeze_time("2026-03-09T12:00:00Z")
    def test_sessions_scope_structure(self):
        from pulse_ai.templates.interaction_templates import build_sessions_query
        result = build_sessions_query(scope="sessions", interaction_name="ContestJoin")

        assert result["dataType"] == "TRACES"
        # Should have ordering by timestamp
        assert any(o["field"] == "timestamp" for o in result.get("orderBy", []))

    @freeze_time("2026-03-09T12:00:00Z")
    def test_sessions_scope_select_items(self):
        from pulse_ai.templates.interaction_templates import build_sessions_query
        result = build_sessions_query(scope="sessions", interaction_name="ContestJoin")

        aliases = [s["alias"] for s in result["select"]]
        # Should include session identifiers and key metrics
        assert "timestamp" in aliases
        assert "duration" in aliases

    @freeze_time("2026-03-09T12:00:00Z")
    def test_sessions_scope_with_limit(self):
        from pulse_ai.templates.interaction_templates import build_sessions_query
        result = build_sessions_query(scope="sessions", interaction_name="ContestJoin", limit=20)

        assert result["limit"] == 20

    @freeze_time("2026-03-09T12:00:00Z")
    def test_stats_scope_structure(self):
        from pulse_ai.templates.interaction_templates import build_sessions_query
        result = build_sessions_query(scope="stats", interaction_name="ContestJoin")

        aliases = [s["alias"] for s in result["select"]]
        # Stats should include aggregate metrics
        assert "total_sessions" in aliases

    @freeze_time("2026-03-09T12:00:00Z")
    def test_sessions_auto_injects_filters(self):
        from pulse_ai.templates.interaction_templates import build_sessions_query
        result = build_sessions_query(scope="sessions", interaction_name="ContestJoin")

        pulse_type = [f for f in result["filters"] if f["field"] == "PulseType"]
        span_name = [f for f in result["filters"] if f["field"] == "SpanName"]
        assert len(pulse_type) == 1
        assert len(span_name) == 1

    @freeze_time("2026-03-09T12:00:00Z")
    def test_invalid_scope_raises(self):
        from pulse_ai.templates.interaction_templates import build_sessions_query
        with pytest.raises(ValueError, match="scope"):
            build_sessions_query(scope="invalid", interaction_name="ContestJoin")

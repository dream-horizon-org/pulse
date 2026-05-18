"""Tests for pulse_ai.server.compaction_rules.

TDD RED: written before compaction_rules.py exists.

Each EM tool gets its own compaction template. Tests verify:
- Output is a structured string (not raw JSON)
- Key metrics are preserved in the summary
- Output is short (under 300 chars — well under 200 tokens)
- Edge cases: empty data, error responses, missing fields
"""

import pytest


# ── Helpers ──────────────────────────────────────────────────────────────────

def _success(data):
    return {"status": "success", "data": data}


def _error(message):
    return {"status": "error", "message": message}


# ── query_interaction_health ─────────────────────────────────────────────────

def test_health_summary_contains_interaction_count():
    from pulse_ai.server.compaction_rules import compact_tool_response
    data = [
        {"interactionName": "ContestJoin", "apdex": 0.82, "errorRate": 0.012},
        {"interactionName": "PaymentCheckout", "apdex": 0.91, "errorRate": 0.005},
    ]
    result = compact_tool_response("query_interaction_health", _success(data))
    assert "2" in result


def test_health_summary_contains_top_interaction_name():
    from pulse_ai.server.compaction_rules import compact_tool_response
    data = [{"interactionName": "ContestJoin", "apdex": 0.82, "errorRate": 0.012}]
    result = compact_tool_response("query_interaction_health", _success(data))
    assert "ContestJoin" in result


def test_health_summary_contains_apdex_value():
    from pulse_ai.server.compaction_rules import compact_tool_response
    data = [{"interactionName": "ContestJoin", "apdex": 0.82, "errorRate": 0.012}]
    result = compact_tool_response("query_interaction_health", _success(data))
    assert "0.82" in result


def test_health_summary_with_empty_data():
    from pulse_ai.server.compaction_rules import compact_tool_response
    result = compact_tool_response("query_interaction_health", _success([]))
    assert "no data" in result.lower()


def test_health_summary_with_error_response():
    from pulse_ai.server.compaction_rules import compact_tool_response
    result = compact_tool_response("query_interaction_health", _error("timeout"))
    assert "error" in result.lower()
    assert "timeout" in result


def test_health_summary_is_short():
    from pulse_ai.server.compaction_rules import compact_tool_response
    data = [
        {
            "interaction_name": f"Interaction{i}",
            "apdex": 0.8,
            "spanfreq": 1000,
            "error_count": 10,
            "user_poor": 50,
            "user_excellent": 600,
            "p50": 500,
        }
        for i in range(10)
    ]
    result = compact_tool_response("query_interaction_health", _success(data))
    assert len(result) < 300


def test_health_summary_contains_p50():
    from pulse_ai.server.compaction_rules import compact_tool_response
    data = [{"interaction_name": "ContestJoin", "apdex": 0.82, "spanfreq": 1000, "error_count": 10, "user_poor": 50, "user_excellent": 600, "p50": 890}]
    result = compact_tool_response("query_interaction_health", _success(data))
    assert "890" in result


def test_health_summary_computes_error_rate_from_counts():
    from pulse_ai.server.compaction_rules import compact_tool_response
    data = [{"interaction_name": "ContestJoin", "apdex": 0.82, "spanfreq": 500, "error_count": 10, "user_poor": 25, "user_excellent": 300, "p50": 400}]
    result = compact_tool_response("query_interaction_health", _success(data))
    assert "2.0" in result  # 10/500*100 = 2.0%


def test_health_summary_contains_poor_pct():
    from pulse_ai.server.compaction_rules import compact_tool_response
    data = [{"interaction_name": "ContestJoin", "apdex": 0.82, "spanfreq": 500, "error_count": 10, "user_poor": 50, "user_excellent": 300, "p50": 400}]
    result = compact_tool_response("query_interaction_health", _success(data))
    assert "10.0" in result  # 50/500*100 = 10.0%


def test_health_summary_contains_excellent_pct():
    from pulse_ai.server.compaction_rules import compact_tool_response
    data = [{"interaction_name": "ContestJoin", "apdex": 0.82, "spanfreq": 500, "error_count": 5, "user_poor": 20, "user_excellent": 300, "p50": 400}]
    result = compact_tool_response("query_interaction_health", _success(data))
    assert "60.0" in result  # 300/500*100 = 60.0%


def test_health_summary_handles_snake_case_key():
    from pulse_ai.server.compaction_rules import compact_tool_response
    data = [{"interaction_name": "SnakeCaseInteraction", "apdex": 0.9, "spanfreq": 100, "error_count": 1, "user_poor": 5, "user_excellent": 60, "p50": 200}]
    result = compact_tool_response("query_interaction_health", _success(data))
    assert "SnakeCaseInteraction" in result


# ── query_interaction_metrics ────────────────────────────────────────────────

def test_metrics_summary_contains_apdex():
    from pulse_ai.server.compaction_rules import compact_tool_response
    data = [{"apdex": 0.75, "p50": 310, "p95": 870}]
    result = compact_tool_response("query_interaction_metrics", _success(data))
    assert "0.75" in result


def test_metrics_summary_contains_latency_values():
    from pulse_ai.server.compaction_rules import compact_tool_response
    data = [{"apdex": 0.75, "p50": 310, "p95": 870}]
    result = compact_tool_response("query_interaction_metrics", _success(data))
    assert "310" in result


def test_metrics_summary_with_empty_data():
    from pulse_ai.server.compaction_rules import compact_tool_response
    result = compact_tool_response("query_interaction_metrics", _success([]))
    assert "no data" in result.lower()


def test_metrics_composite_contains_crash_and_anr():
    from pulse_ai.server.compaction_rules import compact_tool_response
    data = [{"apdex": 0.82, "p50": 234, "p95": 890, "success_count": 4380, "error_count": 120, "crash": 3, "anr": 1, "frozen_frame": 12}]
    result = compact_tool_response("query_interaction_metrics", _success(data))
    assert "crash" in result
    assert "anr" in result


def test_metrics_composite_contains_net_breakdown():
    from pulse_ai.server.compaction_rules import compact_tool_response
    data = [{"apdex": 0.82, "p50": 234, "p95": 890, "success_count": 4320, "error_count": 120, "net_2xx": 4320, "net_4xx": 45, "net_5xx": 12, "net_0": 3}]
    result = compact_tool_response("query_interaction_metrics", _success(data))
    assert "4320" in result
    assert "45" in result


def test_metrics_composite_contains_success_count():
    from pulse_ai.server.compaction_rules import compact_tool_response
    data = [{"apdex": 0.82, "p50": 234, "p95": 890, "success_count": 4380, "error_count": 120}]
    result = compact_tool_response("query_interaction_metrics", _success(data))
    assert "4380" in result


def test_metrics_composite_contains_user_excellent_and_poor():
    from pulse_ai.server.compaction_rules import compact_tool_response
    data = [{"apdex": 0.82, "user_excellent": 300, "user_good": 100, "user_avg": 60, "user_poor": 40}]
    result = compact_tool_response("query_interaction_metrics", _success(data))
    assert "60" in result   # excellent% = 300/500*100
    assert "8" in result    # poor% = 40/500*100


def test_metrics_error_rate_computed_from_counts():
    from pulse_ai.server.compaction_rules import compact_tool_response
    data = [{"success_count": 900, "error_count": 100}]
    result = compact_tool_response("query_interaction_metrics", _success(data))
    assert "10.0" in result  # 100/(900+100)*100


def test_metrics_timeseries_contains_min_max_last():
    from pulse_ai.server.compaction_rules import compact_tool_response
    data = [
        {"t1": "2024-01-01T00:00:00", "p95": 890},
        {"t1": "2024-01-01T01:00:00", "p95": 1400},
        {"t1": "2024-01-01T02:00:00", "p95": 920},
    ]
    result = compact_tool_response("query_interaction_metrics", _success(data))
    assert "890" in result   # min
    assert "1400" in result  # max
    assert "920" in result   # last


def test_metrics_timeseries_contains_point_count():
    from pulse_ai.server.compaction_rules import compact_tool_response
    data = [{"t1": f"2024-01-01T{i:02d}:00:00", "p95": 500 + i * 10} for i in range(20)]
    result = compact_tool_response("query_interaction_metrics", _success(data))
    assert "20" in result


def test_metrics_composite_is_short():
    from pulse_ai.server.compaction_rules import compact_tool_response
    data = [{"apdex": 0.82, "p50": 234, "p95": 890, "success_count": 4380, "error_count": 120,
             "crash": 3, "anr": 1, "frozen_frame": 12, "unanalysed_frame": 5, "analysed_frame": 200,
             "net_2xx": 4320, "net_4xx": 45, "net_5xx": 12, "net_0": 3,
             "user_excellent": 2600, "user_good": 1000, "user_avg": 600, "user_poor": 400}]
    result = compact_tool_response("query_interaction_metrics", _success(data))
    assert len(result) < 300


# ── breakdown_interaction ─────────────────────────────────────────────────────

def test_breakdown_summary_contains_segment_count():
    from pulse_ai.server.compaction_rules import compact_tool_response
    data = [
        {"platform": "Android", "apdex": 0.85},
        {"platform": "iOS", "apdex": 0.91},
    ]
    result = compact_tool_response("breakdown_interaction", _success(data))
    assert "2" in result


def test_breakdown_summary_contains_segment_names():
    from pulse_ai.server.compaction_rules import compact_tool_response
    data = [
        {"platform": "Android", "apdex": 0.85},
        {"platform": "iOS", "apdex": 0.91},
    ]
    result = compact_tool_response("breakdown_interaction", _success(data))
    assert "Android" in result
    assert "iOS" in result


def test_breakdown_summary_with_empty_data():
    from pulse_ai.server.compaction_rules import compact_tool_response
    result = compact_tool_response("breakdown_interaction", _success([]))
    assert "no data" in result.lower()


def test_breakdown_device_dimension_contains_crash_and_anr():
    from pulse_ai.server.compaction_rules import compact_tool_response
    data = [
        {"deviceModel": "Samsung Galaxy S21", "crash": 3, "anr": 1, "frozen_frame": 12},
        {"deviceModel": "Pixel 6", "crash": 0, "anr": 2, "frozen_frame": 5},
    ]
    result = compact_tool_response("breakdown_interaction", _success(data))
    assert "crash" in result
    assert "anr" in result


def test_breakdown_region_dimension_contains_error_and_poor():
    from pulse_ai.server.compaction_rules import compact_tool_response
    data = [
        {"region": "Maharashtra", "success_count": 900, "error_count": 20, "user_poor": 15},
        {"region": "Karnataka", "success_count": 800, "error_count": 10, "user_poor": 8},
    ]
    result = compact_tool_response("breakdown_interaction", _success(data))
    assert "Maharashtra" in result
    assert "20" in result


def test_breakdown_release_dimension_contains_apdex_and_crash():
    from pulse_ai.server.compaction_rules import compact_tool_response
    data = [
        {"release": "5.30.0", "apdex": 0.91, "crash": 0, "anr": 0, "success_count": 900, "error_count": 10},
        {"release": "5.29.1", "apdex": 0.82, "crash": 5, "anr": 2, "success_count": 800, "error_count": 40},
    ]
    result = compact_tool_response("breakdown_interaction", _success(data))
    assert "5.30.0" in result
    assert "crash" in result


def test_breakdown_latency_dimension_contains_p50_and_p95():
    from pulse_ai.server.compaction_rules import compact_tool_response
    data = [
        {"network": "WiFi", "p50": 210, "p95": 780},
        {"network": "4G", "p50": 450, "p95": 1200},
    ]
    result = compact_tool_response("breakdown_interaction", _success(data))
    assert "210" in result
    assert "780" in result


def test_breakdown_caps_at_top_5_segments():
    from pulse_ai.server.compaction_rules import compact_tool_response
    data = [{"platform": f"Platform{i}", "apdex": 0.8, "crash": i} for i in range(10)]
    result = compact_tool_response("breakdown_interaction", _success(data))
    assert "Platform0" in result
    assert "Platform4" in result
    assert "Platform9" not in result


# ── calculate ────────────────────────────────────────────────────────────────

def test_calculate_summary_contains_expression():
    from pulse_ai.server.compaction_rules import compact_tool_response
    response = {"status": "success", "result": 0.8, "expression": "12/1500*100"}
    result = compact_tool_response("calculate", response)
    assert "12/1500*100" in result


def test_calculate_summary_contains_result():
    from pulse_ai.server.compaction_rules import compact_tool_response
    response = {"status": "success", "result": 0.8, "expression": "12/1500*100"}
    result = compact_tool_response("calculate", response)
    assert "0.8" in result


# ── query_interactions ────────────────────────────────────────────────────────

def test_interactions_summary_with_list_contains_count():
    from pulse_ai.server.compaction_rules import compact_tool_response
    data = [{"name": "ContestJoin"}, {"name": "PaymentCheckout"}]
    result = compact_tool_response("query_interactions", _success(data))
    assert "2" in result


def test_interactions_summary_with_detail_dict_contains_name():
    from pulse_ai.server.compaction_rules import compact_tool_response
    data = {"name": "ContestJoin", "status": "ACTIVE", "apdexThreshold": 500}
    result = compact_tool_response("query_interactions", _success(data))
    assert "ContestJoin" in result


def test_interactions_detail_contains_status():
    from pulse_ai.server.compaction_rules import compact_tool_response
    data = {"name": "ContestJoin", "status": "RUNNING", "apdexThreshold": 500}
    result = compact_tool_response("query_interactions", _success(data))
    assert "RUNNING" in result


def test_interactions_detail_contains_apdex_threshold():
    from pulse_ai.server.compaction_rules import compact_tool_response
    data = {"name": "ContestJoin", "status": "ACTIVE", "apdexThreshold": 500}
    result = compact_tool_response("query_interactions", _success(data))
    assert "500" in result


def test_interactions_list_shows_first_5_names_with_suffix():
    from pulse_ai.server.compaction_rules import compact_tool_response
    data = [{"name": f"Interaction{i}"} for i in range(7)]
    result = compact_tool_response("query_interactions", _success(data))
    assert "Interaction0" in result
    assert "Interaction4" in result
    assert "Interaction5" not in result
    assert "2 more" in result


# ── query_alerts ─────────────────────────────────────────────────────────────

def test_alerts_summary_with_list_contains_count():
    from pulse_ai.server.compaction_rules import compact_tool_response
    data = [{"id": 1}, {"id": 2}, {"id": 3}]
    result = compact_tool_response("query_alerts", _success(data))
    assert "3" in result


def test_alerts_list_contains_alert_names_and_states():
    from pulse_ai.server.compaction_rules import compact_tool_response
    data = [
        {"name": "High Latency Alert", "state": "FIRING"},
        {"name": "Crash Rate Alert", "state": "NORMAL"},
    ]
    result = compact_tool_response("query_alerts", _success(data))
    assert "High Latency Alert" in result
    assert "FIRING" in result


def test_alerts_detail_contains_state():
    from pulse_ai.server.compaction_rules import compact_tool_response
    data = {"name": "High Latency Alert", "id": 42, "state": "FIRING", "threshold": "p95>2000ms"}
    result = compact_tool_response("query_alerts", _success(data))
    assert "FIRING" in result


def test_alerts_detail_contains_full_threshold():
    from pulse_ai.server.compaction_rules import compact_tool_response
    data = {"name": "High Latency Alert", "id": 42, "state": "FIRING", "threshold": "p95 latency > 2000ms for /checkout over last 15 minutes"}
    result = compact_tool_response("query_alerts", _success(data))
    assert "p95 latency > 2000ms for /checkout over last 15 minutes" in result


# ── query_interaction_sessions ────────────────────────────────────────────────

def test_sessions_summary_contains_count():
    from pulse_ai.server.compaction_rules import compact_tool_response
    data = [{"sessionId": "abc"}, {"sessionId": "def"}]
    result = compact_tool_response("query_interaction_sessions", _success(data))
    assert "2" in result


def test_sessions_stats_scope_contains_total():
    from pulse_ai.server.compaction_rules import compact_tool_response
    data = [{"total_sessions": 500, "success_count": 450, "error_count": 50, "crash": 3, "anr": 1, "apdex": 0.91, "p50": 780}]
    result = compact_tool_response("query_interaction_sessions", _success(data))
    assert "500" in result
    assert "stats" in result.lower()


def test_sessions_stats_scope_contains_all_fields():
    from pulse_ai.server.compaction_rules import compact_tool_response
    data = [{"total_sessions": 500, "success_count": 450, "error_count": 50, "crash": 3, "anr": 1, "apdex": 0.91, "p50": 780}]
    result = compact_tool_response("query_interaction_sessions", _success(data))
    assert "3" in result    # crash
    assert "0.91" in result  # apdex
    assert "780" in result   # p50


def test_sessions_sessions_scope_contains_status_distribution():
    from pulse_ai.server.compaction_rules import compact_tool_response
    data = [
        {"status_code": "OK", "platform": "Android", "device": "Pixel 6", "os_version": "Android 14", "app_version": "5.30.0"},
        {"status_code": "OK", "platform": "Android", "device": "S21", "os_version": "Android 13", "app_version": "5.30.0"},
        {"status_code": "ERROR", "platform": "iOS", "device": "iPhone 14", "os_version": "iOS 17", "app_version": "5.29.1"},
    ]
    result = compact_tool_response("query_interaction_sessions", _success(data))
    assert "OK" in result
    assert "ERROR" in result


def test_sessions_sessions_scope_contains_platform_and_version():
    from pulse_ai.server.compaction_rules import compact_tool_response
    data = [
        {"status_code": "OK", "platform": "Android", "device": "Pixel 6", "os_version": "Android 14", "app_version": "5.30.0"},
        {"status_code": "ERROR", "platform": "iOS", "device": "iPhone 14", "os_version": "iOS 17", "app_version": "5.30.0"},
    ]
    result = compact_tool_response("query_interaction_sessions", _success(data))
    assert "Android" in result
    assert "5.30.0" in result


def test_sessions_sessions_scope_contains_device_and_os():
    from pulse_ai.server.compaction_rules import compact_tool_response
    data = [
        {"status_code": "OK", "platform": "Android", "device": "Pixel 6", "os_version": "Android 14", "app_version": "5.30.0"},
        {"status_code": "OK", "platform": "Android", "device": "Pixel 6", "os_version": "Android 14", "app_version": "5.30.0"},
        {"status_code": "ERROR", "platform": "iOS", "device": "iPhone 14", "os_version": "iOS 17", "app_version": "5.29.1"},
    ]
    result = compact_tool_response("query_interaction_sessions", _success(data))
    assert "Pixel 6" in result
    assert "Android 14" in result


# ── generic / unknown tool ────────────────────────────────────────────────────

def test_unknown_tool_returns_structured_string():
    from pulse_ai.server.compaction_rules import compact_tool_response
    result = compact_tool_response("some_future_tool", _success([{"x": 1}, {"x": 2}]))
    assert "some_future_tool" in result
    assert isinstance(result, str)


# ── already-compacted guard ───────────────────────────────────────────────────

def test_already_compacted_response_is_not_double_compacted():
    from pulse_ai.server.compaction_rules import compact_tool_response
    already = {"compacted": True, "summary": "[query_interaction_health: 10 interactions]"}
    result = compact_tool_response("query_interaction_health", already)
    # Must return the original summary unchanged, not wrap it again
    assert result == already["summary"]


# ── error response (None / missing) ──────────────────────────────────────────

def test_none_response_returns_error_string():
    from pulse_ai.server.compaction_rules import compact_tool_response
    result = compact_tool_response("query_interaction_health", None)
    assert "error" in result.lower()

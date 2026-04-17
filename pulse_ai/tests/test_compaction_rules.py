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
    data = [{"interactionName": f"Interaction{i}", "apdex": 0.8, "errorRate": 0.01} for i in range(10)]
    result = compact_tool_response("query_interaction_health", _success(data))
    assert len(result) < 300


# ── query_interaction_metrics ─────────────────────────────────────────────────

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


# ── query_alerts ─────────────────────────────────────────────────────────────

def test_alerts_summary_with_list_contains_count():
    from pulse_ai.server.compaction_rules import compact_tool_response
    data = [{"id": 1}, {"id": 2}, {"id": 3}]
    result = compact_tool_response("query_alerts", _success(data))
    assert "3" in result


# ── query_interaction_sessions ────────────────────────────────────────────────

def test_sessions_summary_contains_count():
    from pulse_ai.server.compaction_rules import compact_tool_response
    data = [{"sessionId": "abc"}, {"sessionId": "def"}]
    result = compact_tool_response("query_interaction_sessions", _success(data))
    assert "2" in result


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

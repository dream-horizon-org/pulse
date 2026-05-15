"""Tests for pulse_ai.output_guard (Layer 1 + Layer 3 of LLM leakage defense)."""
from types import SimpleNamespace

from pulse_ai.output_guard import (
    FilteredDeltaTracker,
    em_output_sanitize_callback,
    sanitize_em_output,
    sanitize_pii,
    sanitize_time_range_ids,
)


# ---------------------------------------------------------------------------
# Minimal mock for ADK CallbackContext — only the state interface used by the callback
# ---------------------------------------------------------------------------

class _MockState(dict):
    """dict subclass that mimics CallbackContext.state get/set behaviour."""


def _make_ctx(**state_kwargs) -> SimpleNamespace:
    return SimpleNamespace(state=_MockState(state_kwargs))


# ---------------------------------------------------------------------------
# sanitize_em_output — edge cases
# ---------------------------------------------------------------------------

def test_sanitize_none_returns_empty_string():
    assert sanitize_em_output(None) == ""


def test_sanitize_empty_string_returns_empty_string():
    assert sanitize_em_output("") == ""


def test_sanitize_no_match_returns_text_unchanged():
    text = "The Apdex score is 0.8 for the past 24 hours."
    assert sanitize_em_output(text) == text


def test_sanitize_removes_em_tool_names():
    assert sanitize_em_output("query_interaction_health") == ""
    assert sanitize_em_output("query_interaction_metrics") == ""
    assert sanitize_em_output("query_interaction_sessions") == ""
    assert sanitize_em_output("query_interactions") == ""
    assert sanitize_em_output("query_alerts") == ""
    assert sanitize_em_output("breakdown_interaction") == ""


def test_sanitize_tool_names_case_insensitive():
    assert sanitize_em_output("QUERY_INTERACTION_HEALTH") == ""
    assert sanitize_em_output("Query_Interaction_Health") == ""


def test_sanitize_removes_agent_and_pipeline_names():
    assert sanitize_em_output("EMAgent") == ""
    assert sanitize_em_output("root_agent") == ""
    assert sanitize_em_output("SequentialAgent") == ""
    assert sanitize_em_output("ReportAgent") == ""
    assert sanitize_em_output("PulseAIPipeline") == ""
    assert sanitize_em_output("RcaAgent") == ""
    assert sanitize_em_output("engineering_manager_result") == ""


def test_sanitize_replaces_clickhouse_case_insensitive():
    assert sanitize_em_output("ClickHouse") == "analytics database"
    assert sanitize_em_output("clickhouse") == "analytics database"
    assert sanitize_em_output("CLICKHOUSE") == "analytics database"


def test_sanitize_leftmost_longest_replacingmergetree_beats_mergetree():
    # ReplacingMergeTree contains MergeTree — leftmost-longest must not split it
    assert sanitize_em_output("ReplacingMergeTree") == ""
    assert sanitize_em_output("MergeTree") == ""


def test_sanitize_replaces_table_names():
    assert sanitize_em_output("otel_traces") == "traces"
    assert sanitize_em_output("otel_logs") == "logs"
    assert sanitize_em_output("otel_metrics_gauge") == "metrics"
    assert sanitize_em_output("otel_metrics_sum") == "metrics"
    assert sanitize_em_output("stack_trace_events") == "crash data"
    assert sanitize_em_output("interaction_heatmaps_daily") == "heatmap data"
    assert sanitize_em_output("root_cause_cache") == "analysis cache"


def test_sanitize_replaces_column_names():
    assert sanitize_em_output("SpanAttributes") == "attributes"
    assert sanitize_em_output("LogAttributes") == "attributes"
    assert sanitize_em_output("ResourceAttributes") == "attributes"
    assert sanitize_em_output("ProjectId") == "project identifier"
    assert sanitize_em_output("PulseType") == "event type"
    assert sanitize_em_output("SessionId") == "session identifier"


def test_sanitize_replaces_service_names():
    assert sanitize_em_output("pulse-server") == "backend service"
    assert sanitize_em_output("pulse-ai-agent") == "AI service"
    assert sanitize_em_output("pulse-alerts-cron") == "alerting service"


def test_sanitize_removes_model_and_framework_strings():
    assert sanitize_em_output("gemini-2.5-flash") == ""
    assert sanitize_em_output("gemini-2.0-flash") == ""
    assert sanitize_em_output("google.adk") == ""
    assert sanitize_em_output("google adk") == ""


def test_sanitize_handles_multiple_patterns_in_one_sentence():
    raw = (
        "I used query_interaction_health to check EMAgent analysis of otel_traces "
        "stored in ClickHouse. The ReplacingMergeTree engine processes SpanAttributes "
        "and ProjectId fields. The pulse-server returned engineering_manager_result "
        "from root_cause_cache using the google.adk framework with gemini-2.5-flash."
    )
    result = sanitize_em_output(raw)
    # None of the denylist terms should survive
    for forbidden in [
        "query_interaction_health", "EMAgent", "otel_traces", "ClickHouse",
        "ReplacingMergeTree", "MergeTree", "SpanAttributes", "ProjectId",
        "pulse-server", "engineering_manager_result", "root_cause_cache",
        "google.adk", "gemini-2.5-flash",
    ]:
        assert forbidden.lower() not in result.lower(), f"{forbidden!r} still present"
    # Meaningful replacements survive
    assert "analytics database" in result
    assert "traces" in result
    assert "attributes" in result
    assert "project identifier" in result
    assert "backend service" in result
    assert "analysis cache" in result


# ---------------------------------------------------------------------------
# em_output_sanitize_callback
# ---------------------------------------------------------------------------

def test_callback_sanitizes_em_result_in_state():
    ctx = _make_ctx(engineering_manager_result="Used query_interaction_health via EMAgent.")
    em_output_sanitize_callback(ctx)
    sanitized = ctx.state["engineering_manager_result"]
    assert "query_interaction_health" not in sanitized
    assert "EMAgent" not in sanitized


def test_callback_no_op_when_key_missing():
    ctx = _make_ctx()  # no engineering_manager_result key
    em_output_sanitize_callback(ctx)  # must not raise
    assert "engineering_manager_result" not in ctx.state


def test_callback_no_op_when_value_is_empty_string():
    ctx = _make_ctx(engineering_manager_result="")
    em_output_sanitize_callback(ctx)
    # key must not be written (empty string is falsy — early return)
    assert ctx.state.get("engineering_manager_result") == ""


# ---------------------------------------------------------------------------
# FilteredDeltaTracker (Layer 3 — SSE stream filter)
# ---------------------------------------------------------------------------

class TestFilteredDeltaTrackerBasics:

    def test_flush_on_empty_returns_empty_string(self):
        t = FilteredDeltaTracker()
        assert t.flush() == ""

    def test_push_and_flush_reconstruct_clean_text(self):
        # push + flush must equal the original clean text (no data loss)
        t = FilteredDeltaTracker()
        text = "The Apdex score is excellent at 0.95 (95th percentile 200ms)."
        d = t.push(text)
        tail = t.flush()
        assert d + tail == text

    def test_push_long_clean_text_emits_partial_delta(self):
        # Text longer than hold_back means push itself emits something
        t = FilteredDeltaTracker()
        text = "A" * 100
        delta = t.push(text)
        assert delta  # non-empty — hold_back < 100
        assert delta == "A" * len(delta)  # still clean

    def test_push_cumulative_no_repetition(self):
        # ADK sends full-text-so-far; combined output must equal the final text exactly
        t = FilteredDeltaTracker()
        d1 = t.push("A" * 60)
        d2 = t.push("A" * 60 + "B" * 60)
        tail = t.flush()
        assert d1 + d2 + tail == "A" * 60 + "B" * 60

    def test_reset_clears_accumulated_state(self):
        t = FilteredDeltaTracker()
        t.push("first message content here with lots of padding chars")
        t.reset()
        d = t.push("Hello")
        tail = t.flush()
        assert d + tail == "Hello"


class TestFilteredDeltaTrackerSanitization:

    def test_forbidden_pattern_removed_in_full_output(self):
        t = FilteredDeltaTracker()
        text = "Used EMAgent for analysis. " * 4  # repeat to exceed hold_back
        d = t.push(text)
        tail = t.flush()
        result = d + tail
        assert "EMAgent" not in result
        assert "emAgent" not in result.lower()

    def test_tool_name_removed_in_full_output(self):
        t = FilteredDeltaTracker()
        text = "I called query_interaction_health to fetch data. " * 3
        d = t.push(text)
        tail = t.flush()
        assert "query_interaction_health" not in d + tail

    def test_hold_back_catches_pattern_split_across_chunks(self):
        # "EMAgent" arrives across two ADK events: first "EMAge", then full "EMAgent"
        # The hold_back buffer must prevent "EMAge" from being emitted before pattern completes
        t = FilteredDeltaTracker()
        d1 = t.push("x" * 20 + "EMAge")   # partial match — not a banned token on its own
        d2 = t.push("x" * 20 + "EMAgent" + "x" * 10)  # pattern now complete
        tail = t.flush()
        result = d1 + d2 + tail
        assert "EMAgent" not in result
        assert "emAgent" not in result.lower()
        assert result == "x" * 30  # 20 prefix + 10 suffix; pattern removed

    def test_clean_text_not_corrupted_by_sanitization(self):
        t = FilteredDeltaTracker()
        text = "Apdex 0.8, P95 450ms, error rate 1.2% (18 of 1500 requests)." * 3
        d = t.push(text)
        tail = t.flush()
        assert d + tail == text


# ---------------------------------------------------------------------------
# sanitize_time_range_ids — deterministic time range id → human-readable phrase
# ---------------------------------------------------------------------------

class TestSanitizeTimeRangeIds:

    def test_last_30d_becomes_last_30_days(self):
        assert sanitize_time_range_ids("last_30d") == "last 30 days"

    def test_last_7d_becomes_last_7_days(self):
        assert sanitize_time_range_ids("last_7d") == "last 7 days"

    def test_last_1d_uses_singular_day(self):
        assert sanitize_time_range_ids("last_1d") == "last 1 day"

    def test_last_24h_becomes_last_24_hours(self):
        assert sanitize_time_range_ids("last_24h") == "last 24 hours"

    def test_last_1h_uses_singular_hour(self):
        assert sanitize_time_range_ids("last_1h") == "last 1 hour"

    def test_last_12h_becomes_last_12_hours(self):
        assert sanitize_time_range_ids("last_12h") == "last 12 hours"

    def test_last_5m_becomes_last_5_minutes(self):
        assert sanitize_time_range_ids("last_5m") == "last 5 minutes"

    def test_last_1m_uses_singular_minute(self):
        assert sanitize_time_range_ids("last_1m") == "last 1 minute"

    def test_pattern_embedded_in_sentence(self):
        result = sanitize_time_range_ids("Try last_30d for more data.")
        assert "last_30d" not in result
        assert "last 30 days" in result

    def test_pattern_inside_quotes(self):
        result = sanitize_time_range_ids('(e.g., "last_30d")')
        assert "last_30d" not in result
        assert "last 30 days" in result

    def test_invalid_pattern_unchanged(self):
        assert sanitize_time_range_ids("last_abc") == "last_abc"
        assert sanitize_time_range_ids("last_") == "last_"

    def test_multiple_patterns_in_one_string(self):
        result = sanitize_time_range_ids("Try last_7d or last_30d instead.")
        assert "last_7d" not in result
        assert "last_30d" not in result
        assert "last 7 days" in result
        assert "last 30 days" in result

    def test_none_returns_empty_string(self):
        assert sanitize_time_range_ids(None) == ""

    def test_empty_string_returns_empty_string(self):
        assert sanitize_time_range_ids("") == ""

    def test_clean_text_unchanged(self):
        text = "Apdex 0.8, P95 450ms for the last 7 days."
        assert sanitize_time_range_ids(text) == text


# ---------------------------------------------------------------------------
# sanitize_pii — PII redaction (email, JWT, credit card)
# ---------------------------------------------------------------------------

class TestSanitizePiiEmail:

    def test_plain_email_is_redacted(self):
        assert sanitize_pii("user@example.com") == "[REDACTED:EMAIL]"

    def test_email_with_plus_tag_is_redacted(self):
        assert sanitize_pii("user+tag@example.com") == "[REDACTED:EMAIL]"

    def test_email_in_url_query_string_is_redacted(self):
        result = sanitize_pii("https://api.example.com/v1?email=jane@corp.com&page=1")
        assert "jane@corp.com" not in result
        assert "[REDACTED:EMAIL]" in result

    def test_dotted_non_email_string_not_redacted(self):
        assert sanitize_pii("version.1.2.3") == "version.1.2.3"

    def test_email_in_sentence_is_redacted(self):
        result = sanitize_pii("Session user jane.doe@example.com hit 3 crashes.")
        assert "jane.doe@example.com" not in result
        assert "[REDACTED:EMAIL]" in result
        assert "Session user" in result
        assert "hit 3 crashes." in result


class TestSanitizePiiJwt:

    _JWT = (
        "eyJhbGciOiJSUzI1NiJ9"
        ".eyJzdWIiOiJ1c2VyQGV4YW1wbGUuY29tIiwiaWF0IjoxNjAwMDAwMDAwfQ"
        ".SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"
    )

    def test_jwt_is_redacted(self):
        assert sanitize_pii(self._JWT) == "[REDACTED:TOKEN]"

    def test_jwt_in_bearer_text_is_redacted(self):
        text = f"Authorization: Bearer {self._JWT}"
        result = sanitize_pii(text)
        assert "[REDACTED:TOKEN]" in result
        assert "eyJhbGci" not in result

    def test_jwt_runs_before_email_no_partial_match_inside_payload(self):
        # The JWT payload contains a base64-encoded email; email pattern must not fire
        # inside the token and produce a corrupted replacement.
        result = sanitize_pii(self._JWT)
        assert result == "[REDACTED:TOKEN]"
        assert "[REDACTED:EMAIL]" not in result


class TestSanitizePiiCard:

    def test_visa_16_digit_card_is_redacted(self):
        assert sanitize_pii("4111111111111111") == "[REDACTED:CARD]"

    def test_amex_15_digit_card_is_redacted(self):
        assert sanitize_pii("378282246310005") == "[REDACTED:CARD]"

    def test_mastercard_new_bin_range_is_redacted(self):
        assert sanitize_pii("2221000000000009") == "[REDACTED:CARD]"

    def test_short_status_code_not_redacted(self):
        assert sanitize_pii("404") == "404"

    def test_card_in_url_query_param_is_redacted(self):
        result = sanitize_pii("https://pay.example.com/charge?card=4111111111111111&amount=50")
        assert "4111111111111111" not in result
        assert "[REDACTED:CARD]" in result


class TestSanitizePiiCombined:

    def test_multiple_pii_types_in_one_string_all_redacted(self):
        jwt = (
            "eyJhbGciOiJSUzI1NiJ9"
            ".eyJzdWIiOiJ1c2VyQGV4YW1wbGUuY29tIn0"
            ".SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"
        )
        text = f"User admin@corp.com used token {jwt} on card 4111111111111111."
        result = sanitize_pii(text)
        assert "admin@corp.com" not in result
        assert "eyJhbGci" not in result
        assert "4111111111111111" not in result

    def test_clean_analytics_text_unchanged(self):
        text = "Apdex 0.82, P95 450ms, error rate 1.2% (18 of 1500 requests)."
        assert sanitize_pii(text) == text

    def test_empty_string_returns_empty_string(self):
        assert sanitize_pii("") == ""

    def test_none_returns_none(self):
        assert sanitize_pii(None) is None


# ---------------------------------------------------------------------------
# FilteredDeltaTracker — PII passes through stream and is redacted
# ---------------------------------------------------------------------------

class TestFilteredDeltaTrackerPii:

    def test_email_in_stream_is_redacted(self):
        t = FilteredDeltaTracker()
        text = "Session user jane@example.com had 5 errors. " * 3
        d = t.push(text)
        tail = t.flush()
        result = d + tail
        assert "jane@example.com" not in result
        assert "[REDACTED:EMAIL]" in result

    def test_jwt_in_stream_is_redacted(self):
        jwt = (
            "eyJhbGciOiJSUzI1NiJ9"
            ".eyJzdWIiOiJ1c2VyQGV4YW1wbGUuY29tIn0"
            ".SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"
        )
        t = FilteredDeltaTracker()
        text = f"Token found: {jwt} in the session attributes. " * 2
        d = t.push(text)
        tail = t.flush()
        result = d + tail
        assert "eyJhbGci" not in result
        assert "[REDACTED:TOKEN]" in result


class TestSanitizeEmOutputTimeRangeIntegration:

    def test_sanitize_em_output_replaces_time_range_id(self):
        result = sanitize_em_output('Would you like to try "last_30d"?')
        assert "last_30d" not in result
        assert "last 30 days" in result

    def test_filtered_delta_tracker_strips_time_range_id(self):
        t = FilteredDeltaTracker()
        text = 'Would you like to try a different time range (e.g., "last_30d") or explore other interactions? ' * 3
        d = t.push(text)
        tail = t.flush()
        result = d + tail
        assert "last_30d" not in result
        assert "last 30 days" in result

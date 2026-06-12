"""
Output guard: sanitizes EM agent output (Layer 1) and filters SSE stream deltas (Layer 3).
"""
from __future__ import annotations

import re

import ahocorasick
import phonenumbers
from phonenumbers import PhoneNumberMatcher, Leniency

# ---------------------------------------------------------------------------
# PII redaction patterns — applied to all AI-generated text before egress
# Order matters: JWT before email (JWT payloads contain base64-encoded emails)
# ---------------------------------------------------------------------------

_PII_PATTERNS: list[tuple[re.Pattern[str], str]] = [
    (
        re.compile(
            r"\beyJ[A-Za-z0-9\-_]{10,500}\.eyJ[A-Za-z0-9\-_]{10,500}\.[A-Za-z0-9\-_]{10,500}\b"
        ),
        "[REDACTED:TOKEN]",
    ),
    (
        re.compile(
            r"\b[A-Za-z0-9._%+\-]{1,64}@[A-Za-z0-9\-]{1,63}"
            r"(?:\.[A-Za-z0-9\-]{1,63}){0,4}\.[A-Za-z]{2,}\b"
        ),
        "[REDACTED:EMAIL]",
    ),
    (
        re.compile(
            r"\b(?:"
            r"4[0-9]{12}(?:[0-9]{3,6})?"
            r"|(?:5[1-5]|2[2-7])[0-9]{14}"
            r"|3[47][0-9]{13}"
            r"|3(?:0[0-5]|[68][0-9])[0-9]{11}"
            r"|6(?:011|5[0-9]{2})[0-9]{12}"
            r"|(?:2131|1800|35\d{3})\d{11}"
            r")\b"
        ),
        "[REDACTED:CARD]",
    ),
]


_PHONE_DEFAULT_REGION = "IN"


def _redact_phones(text: str) -> str:
    """Redact phone numbers detected by libphonenumber (default region: IN)."""
    matches = list(PhoneNumberMatcher(text, _PHONE_DEFAULT_REGION, leniency=Leniency.VALID))
    if not matches:
        return text
    parts: list[str] = []
    pos = 0
    for m in matches:
        parts.append(text[pos:m.start])
        parts.append("[REDACTED:PHONE]")
        pos = m.end
    parts.append(text[pos:])
    return "".join(parts)


def sanitize_pii(text: str | None) -> str | None:
    """Redact PII (JWTs, emails, card numbers, phone numbers) from a text string."""
    if not text:
        return text
    for pattern, replacement in _PII_PATTERNS:
        text = pattern.sub(replacement, text)
    return _redact_phones(text)


# ---------------------------------------------------------------------------
# Internal helpers
# ---------------------------------------------------------------------------

def _compile(rules: dict[str, str], case_insensitive: bool):
    """Compile Aho-Corasick automaton. Returns (automaton | None, max_pattern_len)."""
    if not rules:
        return None, 0
    A = ahocorasick.Automaton()
    for pattern, replacement in rules.items():
        key = pattern.lower() if case_insensitive else pattern
        A.add_word(key, (len(key), replacement))
    A.make_automaton()
    return A, max(len(p) for p in rules)


def _replace_all(text: str, automaton, case_insensitive: bool) -> str:
    """Leftmost-longest Aho-Corasick replacement. Skips overlapping matches."""
    if not text or automaton is None:
        return text
    search_text = text.lower() if case_insensitive else text

    matches: list[tuple[int, int, str]] = []
    for end_idx, (key_len, replacement) in automaton.iter(search_text):
        matches.append((end_idx - key_len + 1, end_idx, replacement))

    if not matches:
        return text

    # Leftmost first; longest first when starts are equal
    matches.sort(key=lambda m: (m[0], -(m[1] - m[0])))

    filtered: list[tuple[int, int, str]] = []
    last_end = -1
    for start, end, replacement in matches:
        if start > last_end:
            filtered.append((start, end, replacement))
            last_end = end

    parts: list[str] = []
    pos = 0
    for start, end, replacement in filtered:
        parts.append(text[pos:start])
        parts.append(replacement)
        pos = end + 1
    parts.append(text[pos:])
    return "".join(parts)


# ---------------------------------------------------------------------------
# Layer 1 — EM output sanitizer (broad, case-insensitive)
# ---------------------------------------------------------------------------

_EM_RULES: dict[str, str] = {
    # EM tool names → removed
    "query_interaction_health": "",
    "query_interaction_metrics": "",
    "query_interaction_sessions": "",
    "query_interactions": "",
    "query_alerts": "",
    "breakdown_interaction": "",
    # Agent / pipeline names → removed
    "EMAgent": "",
    "root_agent": "",
    "SequentialAgent": "",
    "ReportAgent": "",
    "PulseAIPipeline": "",
    "RcaAgent": "",
    "engineering_manager_result": "",
    # ClickHouse infrastructure → generic (ReplacingMergeTree before MergeTree for leftmost-longest)
    "ClickHouse": "analytics database",
    "ReplacingMergeTree": "",
    "MergeTree": "",
    # Internal service names → generic
    "pulse-server": "backend service",
    "pulse-ai-agent": "AI service",
    "pulse-alerts-cron": "alerting service",
    # ClickHouse table names → generic
    "otel_traces": "traces",
    "otel_logs": "logs",
    "otel_metrics_gauge": "metrics",
    "otel_metrics_sum": "metrics",
    "stack_trace_events": "crash data",
    "interaction_heatmaps_daily": "heatmap data",
    "root_cause_cache": "analysis cache",
    # ClickHouse materialized column names → generic
    "SpanAttributes": "attributes",
    "LogAttributes": "attributes",
    "ResourceAttributes": "attributes",
    "ProjectId": "project identifier",
    "PulseType": "event type",
    "SessionId": "session identifier",
    # Framework / model version strings → removed
    "google adk": "",
    "google.adk": "",
    "gemini-2.5-flash": "",
    "gemini-2.0-flash": "",
}

_EM_AUTOMATON, _EM_MAX_LEN = _compile(_EM_RULES, case_insensitive=True)
_HOLD_BACK = max(0, _EM_MAX_LEN - 1)


_TIME_RANGE_RE = re.compile(r"last_(\d+)(d|h|m)\b")
_TIME_UNITS = {"d": ("day", "days"), "h": ("hour", "hours"), "m": ("minute", "minutes")}


def sanitize_time_range_ids(text: str) -> str:
    """Replace internal time range identifiers (e.g. last_30d) with human-readable phrases."""
    if not text:
        return ""

    def _replace(m: re.Match) -> str:
        n, unit = int(m.group(1)), m.group(2)
        singular, plural = _TIME_UNITS[unit]
        return f"last {n} {singular if n == 1 else plural}"

    return _TIME_RANGE_RE.sub(_replace, text)


def sanitize_em_output(text: str) -> str:
    """Strip implementation details from EM agent output before Report Agent context injection."""
    if not text:
        return ""
    text = _replace_all(text, _EM_AUTOMATON, case_insensitive=True)
    text = sanitize_pii(text)
    return sanitize_time_range_ids(text)


# ---------------------------------------------------------------------------
# Layer 3 — SSE stream filter (hold-back buffer to catch boundary-straddling patterns)
# ---------------------------------------------------------------------------


class FilteredDeltaTracker:
    """Deduplicates ADK cumulative SSE text and sanitizes forbidden patterns.

    ADK emits full-text-so-far events. This class extracts the new delta
    (like DeltaTracker) while also sanitizing the accumulated text.
    A hold-back buffer of _HOLD_BACK chars prevents partial pattern emission
    at chunk boundaries. Call flush() after the final chunk to drain the tail.
    """

    def __init__(self) -> None:
        self._full_raw = ""
        self._n_emitted = 0

    def push(self, cumulative_text: str) -> str:
        if cumulative_text.startswith(self._full_raw):
            self._full_raw = cumulative_text
        else:
            self._full_raw += cumulative_text
        sanitized = sanitize_em_output(self._full_raw)
        safe_end = max(0, len(sanitized) - _HOLD_BACK)
        delta = sanitized[self._n_emitted:safe_end]
        self._n_emitted = safe_end
        return delta

    def flush(self) -> str:
        if not self._full_raw:
            return ""
        sanitized = sanitize_em_output(self._full_raw)
        tail = sanitized[self._n_emitted:]
        self._n_emitted = len(sanitized)
        return tail

    def reset(self) -> None:
        self._full_raw = ""
        self._n_emitted = 0


def em_output_sanitize_callback(callback_context) -> None:
    """ADK after_agent_callback for the EM agent.

    Reads engineering_manager_result from session state, sanitizes it in place,
    and writes the cleaned value back so all downstream consumers receive safe text.
    """
    raw = callback_context.state.get("engineering_manager_result")
    if not raw:
        return
    callback_context.state["engineering_manager_result"] = sanitize_em_output(raw)

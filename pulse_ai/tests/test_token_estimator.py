"""Tests for pulse_ai.server.token_estimator.

TDD RED: written before token_estimator.py exists.
"""

from types import SimpleNamespace

import pytest


# ── estimate_tokens_for_text ─────────────────────────────────────────────────


def test_empty_string_returns_minimum_one():
    from pulse_ai.server.token_estimator import estimate_tokens_for_text
    assert estimate_tokens_for_text("") == 1


def test_short_string_uses_char_over_four_heuristic():
    from pulse_ai.server.token_estimator import estimate_tokens_for_text
    # "hello" = 5 chars → 5 // 4 = 1, but minimum 1
    assert estimate_tokens_for_text("hello") == 1


def test_sixteen_char_string_returns_four_tokens():
    from pulse_ai.server.token_estimator import estimate_tokens_for_text
    assert estimate_tokens_for_text("a" * 16) == 4


def test_four_hundred_char_string_returns_one_hundred_tokens():
    from pulse_ai.server.token_estimator import estimate_tokens_for_text
    assert estimate_tokens_for_text("x" * 400) == 100


def test_estimate_scales_linearly():
    from pulse_ai.server.token_estimator import estimate_tokens_for_text
    assert estimate_tokens_for_text("a" * 800) == 200


# ── estimate_tokens_for_event ────────────────────────────────────────────────


def _text_part(text: str):
    return SimpleNamespace(text=text, function_call=None, function_response=None)


def _fn_call_part(name: str, args: dict):
    import json
    fn_call = SimpleNamespace(name=name, args=args)
    return SimpleNamespace(text=None, function_call=fn_call, function_response=None)


def _fn_response_part(name: str, response: dict):
    fn_resp = SimpleNamespace(name=name, response=response)
    return SimpleNamespace(text=None, function_call=None, function_response=fn_resp)


def _event(author: str, parts: list):
    return SimpleNamespace(
        author=author,
        content=SimpleNamespace(parts=parts),
    )


def test_event_with_text_estimates_text_tokens():
    from pulse_ai.server.token_estimator import estimate_tokens_for_event
    event = _event("user", [_text_part("a" * 100)])
    assert estimate_tokens_for_event(event) == 25  # 100 // 4


def test_event_with_no_content_returns_minimum_one():
    from pulse_ai.server.token_estimator import estimate_tokens_for_event
    event = SimpleNamespace(author="user", content=None)
    assert estimate_tokens_for_event(event) == 1


def test_event_with_function_response_includes_response_tokens():
    from pulse_ai.server.token_estimator import estimate_tokens_for_event
    # Response dict serializes to JSON — tokens come from that string length
    large_response = {"data": [{"name": "x" * 100}]}
    event = _event("EMAgent", [_fn_response_part("query_interaction_health", large_response)])
    tokens = estimate_tokens_for_event(event)
    assert tokens > 1


def test_event_with_function_call_includes_call_tokens():
    from pulse_ai.server.token_estimator import estimate_tokens_for_event
    event = _event("EMAgent", [_fn_call_part("query_interaction_health", {"top_n": 10})])
    tokens = estimate_tokens_for_event(event)
    assert tokens > 1


def test_event_accumulates_tokens_across_multiple_parts():
    from pulse_ai.server.token_estimator import estimate_tokens_for_event
    parts = [
        _text_part("a" * 40),             # 10 tokens
        _text_part("b" * 40),             # 10 tokens
    ]
    event = _event("ReportAgent", parts)
    assert estimate_tokens_for_event(event) == 20


def test_event_with_empty_parts_list_returns_minimum_one():
    from pulse_ai.server.token_estimator import estimate_tokens_for_event
    event = _event("user", [])
    assert estimate_tokens_for_event(event) == 1

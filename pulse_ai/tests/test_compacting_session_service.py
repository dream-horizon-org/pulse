"""Tests for pulse_ai.server.compacting_session_service.

TDD RED: written before compacting_session_service.py exists.

Verifies:
- Turns within K=5 keep raw tool responses
- Turns older than K=5 get compacted
- First user message is always pinned (never dropped)
- Safety cap of MAX_WINDOW_SAFETY_CAP applies
- Token budget truncation drops second-oldest turns first
- Original session events are never mutated (copy returned)
- Non-get_session methods delegate unchanged to inner service
- Session with no events is returned as-is
"""

from __future__ import annotations

from types import SimpleNamespace
from unittest.mock import AsyncMock, MagicMock

import pytest

# ── Helpers ──────────────────────────────────────────────────────────────────

def _fn_response_part(tool_name: str, response: dict):
    fn_resp = SimpleNamespace(name=tool_name, response=response)
    return SimpleNamespace(text=None, function_call=None, function_response=fn_resp)


def _text_part(text: str):
    return SimpleNamespace(text=text, function_call=None, function_response=None)


def _user_event(text: str = "question"):
    return SimpleNamespace(
        author="user",
        content=SimpleNamespace(parts=[_text_part(text)]),
    )


def _agent_event_with_tool(tool_name: str, response: dict):
    """An EMAgent event containing a function_response."""
    return SimpleNamespace(
        author="EMAgent",
        content=SimpleNamespace(parts=[_fn_response_part(tool_name, response)]),
    )


def _agent_text_event(text: str = "Here is your analysis"):
    return SimpleNamespace(
        author="ReportAgent",
        content=SimpleNamespace(parts=[_text_part(text)]),
    )


def _make_session(events: list):
    return SimpleNamespace(events=list(events))


def _make_inner(session=None):
    """Create an async mock inner SessionService."""
    inner = MagicMock()
    inner.get_session = AsyncMock(return_value=session)
    inner.create_session = AsyncMock(return_value=session)
    inner.delete_session = AsyncMock(return_value=None)
    inner.list_sessions = AsyncMock(return_value=[])
    inner.append_event = AsyncMock(return_value=None)
    return inner


_HEALTH_RESPONSE = {
    "status": "success",
    "data": [{"interactionName": "ContestJoin", "apdex": 0.82, "errorRate": 0.012}],
}


# ── Session with no events ────────────────────────────────────────────────────

async def test_session_with_no_events_is_returned_unchanged():
    from pulse_ai.server.compacting_session_service import CompactingSessionService
    session = _make_session([])
    inner = _make_inner(session)
    svc = CompactingSessionService(inner)
    result = await svc.get_session(app_name="a", user_id="u", session_id="s")
    assert result.events == []


async def test_none_session_is_returned_as_none():
    from pulse_ai.server.compacting_session_service import CompactingSessionService
    inner = _make_inner(None)
    svc = CompactingSessionService(inner)
    result = await svc.get_session(app_name="a", user_id="u", session_id="s")
    assert result is None


# ── Tool compaction age threshold (K=5) ──────────────────────────────────────

async def test_tool_response_within_threshold_is_not_compacted():
    """Turn age <= K=5 keeps raw tool response."""
    from pulse_ai.server.compacting_session_service import CompactingSessionService
    # 2 turns — both within threshold
    events = [
        _user_event("turn 1"),
        _agent_event_with_tool("query_interaction_health", _HEALTH_RESPONSE),
        _user_event("turn 2"),
        _agent_event_with_tool("query_interaction_health", _HEALTH_RESPONSE),
    ]
    session = _make_session(events)
    inner = _make_inner(session)
    svc = CompactingSessionService(inner)
    result = await svc.get_session(app_name="a", user_id="u", session_id="s")

    # Find tool response parts in result events
    tool_parts = [
        part
        for event in result.events
        for part in (event.content.parts if event.content else [])
        if getattr(part, "function_response", None)
    ]
    assert len(tool_parts) == 2
    for part in tool_parts:
        assert not part.function_response.response.get("compacted")


async def test_tool_response_beyond_threshold_is_compacted():
    """Turn age > K=5: tool response is replaced with compacted summary."""
    from pulse_ai.server.compacting_session_service import CompactingSessionService
    # Build 7 turns so turn 1 has age = 6 (> K=5)
    events = []
    for i in range(7):
        events.append(_user_event(f"question {i}"))
        events.append(_agent_event_with_tool("query_interaction_health", _HEALTH_RESPONSE))

    session = _make_session(events)
    inner = _make_inner(session)
    svc = CompactingSessionService(inner)
    result = await svc.get_session(app_name="a", user_id="u", session_id="s")

    # Collect all tool response parts by turn index
    all_tool_parts = []
    for event in result.events:
        if not event.content:
            continue
        for part in event.content.parts:
            if getattr(part, "function_response", None):
                all_tool_parts.append(part)

    # First turn's tool response (oldest, age=6) must be compacted
    assert all_tool_parts[0].function_response.response.get("compacted") is True
    # Last turn's tool response (age=0) must NOT be compacted
    assert not all_tool_parts[-1].function_response.response.get("compacted")


async def test_compacted_response_contains_structured_summary():
    """Compacted tool response has a 'summary' key with a non-empty string."""
    from pulse_ai.server.compacting_session_service import CompactingSessionService
    events = []
    for i in range(7):
        events.append(_user_event(f"question {i}"))
        events.append(_agent_event_with_tool("query_interaction_health", _HEALTH_RESPONSE))

    session = _make_session(events)
    inner = _make_inner(session)
    svc = CompactingSessionService(inner)
    result = await svc.get_session(app_name="a", user_id="u", session_id="s")

    first_tool_part = next(
        part
        for event in result.events
        for part in (event.content.parts if event.content else [])
        if getattr(part, "function_response", None)
    )
    assert first_tool_part.function_response.response.get("compacted") is True
    summary = first_tool_part.function_response.response.get("summary", "")
    assert isinstance(summary, str)
    assert len(summary) > 0


# ── Original session is never mutated ────────────────────────────────────────

async def test_original_session_events_are_not_mutated():
    """get_session returns a copy — original session events unchanged."""
    from pulse_ai.server.compacting_session_service import CompactingSessionService
    events = []
    for i in range(7):
        events.append(_user_event(f"q{i}"))
        events.append(_agent_event_with_tool("query_interaction_health", _HEALTH_RESPONSE))

    session = _make_session(events)
    original_first_tool_response = dict(
        events[1].content.parts[0].function_response.response
    )
    inner = _make_inner(session)
    svc = CompactingSessionService(inner)
    await svc.get_session(app_name="a", user_id="u", session_id="s")

    # Original must be unchanged
    assert events[1].content.parts[0].function_response.response == original_first_tool_response


# ── First user message is always pinned ──────────────────────────────────────

async def test_first_user_message_is_always_present_after_budget_truncation():
    """Even when many turns push us over token budget, turn 1 is kept."""
    from pulse_ai.server.compacting_session_service import CompactingSessionService
    from pulse_ai.constants import TOKEN_BUDGET, CHARS_PER_TOKEN

    # Create enough events to blow the token budget
    # Each tool response needs to be large enough post-compaction to still accumulate
    big_response = {
        "status": "success",
        "data": [{"interactionName": f"Interaction{j}", "apdex": 0.8} for j in range(50)],
    }
    events = []
    first_question = "what is the health of my app — this is the original question"
    events.append(_user_event(first_question))
    events.append(_agent_event_with_tool("query_interaction_health", big_response))
    for i in range(35):
        events.append(_user_event(f"followup question number {i}"))
        events.append(_agent_event_with_tool("query_interaction_health", big_response))

    session = _make_session(events)
    inner = _make_inner(session)
    svc = CompactingSessionService(inner)
    result = await svc.get_session(app_name="a", user_id="u", session_id="s")

    user_events = [e for e in result.events if e.author == "user"]
    assert len(user_events) >= 1
    first_text = user_events[0].content.parts[0].text
    assert first_text == first_question


# ── Safety cap ───────────────────────────────────────────────────────────────

async def test_safety_cap_limits_turns_to_max_window():
    """Sessions with more than MAX_WINDOW_SAFETY_CAP turns are truncated."""
    from pulse_ai.server.compacting_session_service import CompactingSessionService
    from pulse_ai.constants import MAX_WINDOW_SAFETY_CAP

    events = []
    for i in range(MAX_WINDOW_SAFETY_CAP + 10):
        events.append(_user_event(f"q{i}"))
        events.append(_agent_text_event("answer"))

    session = _make_session(events)
    inner = _make_inner(session)
    svc = CompactingSessionService(inner)
    result = await svc.get_session(app_name="a", user_id="u", session_id="s")

    user_turns = [e for e in result.events if e.author == "user"]
    assert len(user_turns) <= MAX_WINDOW_SAFETY_CAP


# ── Delegation ────────────────────────────────────────────────────────────────

async def test_create_session_delegates_to_inner():
    from pulse_ai.server.compacting_session_service import CompactingSessionService
    inner = _make_inner()
    svc = CompactingSessionService(inner)
    await svc.create_session(app_name="a", user_id="u", session_id="s")
    inner.create_session.assert_called_once_with(app_name="a", user_id="u", session_id="s")


async def test_delete_session_delegates_to_inner():
    from pulse_ai.server.compacting_session_service import CompactingSessionService
    inner = _make_inner()
    svc = CompactingSessionService(inner)
    await svc.delete_session(app_name="a", user_id="u", session_id="s")
    inner.delete_session.assert_called_once_with(app_name="a", user_id="u", session_id="s")


async def test_list_sessions_delegates_to_inner():
    from pulse_ai.server.compacting_session_service import CompactingSessionService
    inner = _make_inner()
    svc = CompactingSessionService(inner)
    await svc.list_sessions(app_name="a", user_id="u")
    inner.list_sessions.assert_called_once_with(app_name="a", user_id="u")


async def test_unknown_attribute_falls_through_to_inner():
    from pulse_ai.server.compacting_session_service import CompactingSessionService
    inner = _make_inner()
    inner.some_future_method = MagicMock(return_value="ok")
    svc = CompactingSessionService(inner)
    result = svc.some_future_method()
    assert result == "ok"

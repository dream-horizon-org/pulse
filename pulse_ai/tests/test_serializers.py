"""Tests for pulse_ai.server.serializers (events_to_messages, etc.)."""

from types import SimpleNamespace

from pulse_ai.agents.settings import REPORT_AGENT_NAME
from pulse_ai.server.serializers import events_to_messages


def _part_text(text: str):
    return SimpleNamespace(text=text, function_response=None)


def _user_event(eid: str, text: str, inv: str = "inv-1"):
    return SimpleNamespace(
        author="user",
        id=eid,
        invocation_id=inv,
        content=SimpleNamespace(parts=[_part_text(text)]),
    )


def _report_event(eid: str, text: str = "", inv: str = "inv-1", blocks=None):
    parts = []
    if text:
        parts.append(_part_text(text))
    if blocks:
        parts.extend(blocks)
    if not parts:
        parts.append(_part_text(""))
    return SimpleNamespace(
        author=REPORT_AGENT_NAME,
        id=eid,
        invocation_id=inv,
        content=SimpleNamespace(parts=parts),
    )


def test_events_to_messages_user_includes_id_and_invocation():
    events = [
        _user_event("u-1", "hello"),
        _report_event("a-1", "hi there", inv="inv-1"),
    ]
    msgs = events_to_messages(events)
    assert len(msgs) == 2
    assert msgs[0]["role"] == "user"
    assert msgs[0]["id"] == "u-1"
    assert msgs[0]["invocation_id"] == "inv-1"
    assert msgs[1]["role"] == "model"
    assert msgs[1]["id"] == "a-1"


def test_events_to_messages_assistant_turn_uses_last_report_event_id():
    events = [
        _user_event("u-1", "q"),
        _report_event("a-1", "partial"),
        _report_event("a-2", "partial2"),
    ]
    msgs = events_to_messages(events)
    assert msgs[1]["id"] == "a-2"


def test_events_to_messages_skips_non_report_agents():
    other = SimpleNamespace(
        author="Planner",
        id="p-9",
        invocation_id="inv-x",
        content=SimpleNamespace(parts=[_part_text("plan")]),
    )
    events = [_user_event("u-1", "x"), other, _report_event("a-1", "answer")]
    msgs = events_to_messages(events)
    assert len(msgs) == 2
    assert msgs[1]["text"] == "answer"

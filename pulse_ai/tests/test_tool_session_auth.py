"""Tests for pulse_ai.tool_session_auth."""

from google.adk.sessions.state import State

from pulse_ai.constants import (
    PULSE_TOOL_SESSION_MISSING_BEARER,
    PULSE_TOOL_SESSION_MISSING_CONTEXT,
    PULSE_TOOL_SESSION_MISSING_PROJECT,
)
from pulse_ai.tool_session_auth import pulse_tool_session_auth_error


def test_pulse_tool_session_auth_error_none_context():
    assert pulse_tool_session_auth_error(None) == {
        "status": "error",
        "message": PULSE_TOOL_SESSION_MISSING_CONTEXT,
    }


def test_pulse_tool_session_auth_error_missing_bearer():
    ctx = type("C", (), {"state": {"project_id": "p"}})()
    assert pulse_tool_session_auth_error(ctx) == {
        "status": "error",
        "message": PULSE_TOOL_SESSION_MISSING_BEARER,
    }


def test_pulse_tool_session_auth_error_missing_project():
    ctx = type("C", (), {"state": {"bearer_token": "Bearer x"}})()
    assert pulse_tool_session_auth_error(ctx) == {
        "status": "error",
        "message": PULSE_TOOL_SESSION_MISSING_PROJECT,
    }


def test_pulse_tool_session_auth_error_success():
    ctx = type(
        "C",
        (),
        {"state": {"bearer_token": "Bearer ok", "project_id": "p1"}},
    )()
    assert pulse_tool_session_auth_error(ctx) is None


def test_pulse_tool_session_auth_error_success_adk_state_object():
    """Production ToolContext.state is ADK State, not dict; auth must still read keys."""
    session_state = {"bearer_token": "Bearer ok", "project_id": "p1"}
    st = State(value=session_state, delta={})
    ctx = type("C", (), {"state": st})()
    assert pulse_tool_session_auth_error(ctx) is None

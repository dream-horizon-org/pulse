"""Shared test fixtures for Pulse AI agent tests."""

import json
import os
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import AsyncMock, MagicMock

import httpx
import pytest


# ---------------------------------------------------------------------------
# Fixtures directory
# ---------------------------------------------------------------------------

FIXTURES_DIR = Path(__file__).parent / "fixtures"


def load_fixture(name: str) -> dict:
    """Load a JSON fixture file from tests/fixtures/."""
    with open(FIXTURES_DIR / name) as f:
        return json.load(f)


# ---------------------------------------------------------------------------
# Environment helpers
# ---------------------------------------------------------------------------

@pytest.fixture(autouse=True)
def _clean_env(monkeypatch):
    """Ensure tests don't leak env vars. Sets safe defaults."""
    monkeypatch.setenv("PULSE_BASE_URL", "http://localhost:8080")
    monkeypatch.setenv("PULSE_USER_EMAIL", "test@example.com")


# ---------------------------------------------------------------------------
# Mock tool_context
# ---------------------------------------------------------------------------

@pytest.fixture
def mock_tool_context():
    """Create a mock ADK ToolContext with state dict."""
    ctx = MagicMock()
    ctx.state = {
        "jwt": "ctx-access-token",
        "user_email": "ctx-user@example.com",
    }
    return ctx


@pytest.fixture
def pulse_tool_context():
    """Mock ADK ToolContext with bearer_token + project_id for EM tools."""
    ctx = MagicMock()
    ctx.state = {
        "bearer_token": "Bearer test-access-token",
        "project_id": "test-project-id",
    }
    return ctx


# ---------------------------------------------------------------------------
# Mock httpx responses
# ---------------------------------------------------------------------------

# ---------------------------------------------------------------------------
# Session / event construction helpers (shared by compaction tests)
# These are module-level functions, not fixtures, so they can be called
# directly from any test without needing to be declared as a parameter.
# ---------------------------------------------------------------------------

def make_text_part(text: str) -> SimpleNamespace:
    return SimpleNamespace(text=text, function_call=None, function_response=None)


def make_fn_response_part(tool_name: str, response: dict) -> SimpleNamespace:
    fn_resp = SimpleNamespace(name=tool_name, response=response)
    return SimpleNamespace(text=None, function_call=None, function_response=fn_resp)


def make_fn_call_part(tool_name: str, call_id: str | None = None) -> SimpleNamespace:
    fn_call = SimpleNamespace(name=tool_name, id=call_id)
    return SimpleNamespace(text=None, function_call=fn_call, function_response=None)


def make_user_event(text: str = "question") -> SimpleNamespace:
    return SimpleNamespace(
        author="user",
        content=SimpleNamespace(parts=[make_text_part(text)]),
    )


def make_agent_tool_event(tool_name: str, response: dict) -> SimpleNamespace:
    return SimpleNamespace(
        author="EMAgent",
        content=SimpleNamespace(parts=[make_fn_response_part(tool_name, response)]),
    )


def make_agent_text_event(text: str = "Here is your analysis.") -> SimpleNamespace:
    return SimpleNamespace(
        author="ReportAgent",
        content=SimpleNamespace(parts=[make_text_part(text)]),
    )


def make_session(events: list) -> SimpleNamespace:
    return SimpleNamespace(events=list(events))


def make_inner_service(session: SimpleNamespace | None = None) -> MagicMock:
    """Return an async mock ADK SessionService pre-wired to return *session*."""
    inner = MagicMock()
    inner.get_session = AsyncMock(return_value=session)
    inner.create_session = AsyncMock(return_value=session)
    inner.delete_session = AsyncMock(return_value=None)
    inner.list_sessions = AsyncMock(return_value=[])
    inner.append_event = AsyncMock(return_value=None)
    return inner


# ---------------------------------------------------------------------------
# Mock httpx responses
# ---------------------------------------------------------------------------

def make_response(
    status_code: int = 200,
    json_data: dict | None = None,
    text: str = "",
) -> httpx.Response:
    """Build a fake httpx.Response for testing."""
    resp = httpx.Response(
        status_code=status_code,
        json=json_data,
        text=text if json_data is None else "",
        request=httpx.Request("GET", "http://test"),
    )
    return resp

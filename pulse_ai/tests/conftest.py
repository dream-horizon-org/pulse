"""Shared test fixtures for Pulse AI agent tests."""

import json
import os
from pathlib import Path
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
    monkeypatch.setenv("PULSE_ACCESS_TOKEN", "test-access-token")
    monkeypatch.setenv("PULSE_REFRESH_TOKEN", "test-refresh-token")
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
        "refresh_token": "ctx-refresh-token",
        "user_email": "ctx-user@example.com",
    }
    return ctx


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

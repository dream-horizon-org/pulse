"""Agent-facing configuration: model, names, tool HTTP client defaults, session error strings."""

from __future__ import annotations

import os

DEFAULT_MODEL = "gemini-2.5-flash"
AGENT_MODEL_ENV_KEY = "AGENT_MODEL"
AGENT_MODEL = os.getenv(AGENT_MODEL_ENV_KEY, DEFAULT_MODEL)
PULSE_SERVER_BASE_URL = os.getenv("PULSE_SERVER_BASE_URL", "http://localhost:8080")

REPORT_AGENT_NAME = "ReportAgent"
PIPELINE_AGENT_NAME = "PulseAIPipeline"
RCA_AGENT_NAME = "RcaAgent"

CORE_ANALYSIS_AGENT_NAME = "CoreAnalysis"
DEPENDENT_ANALYSIS_AGENT_NAME = "DependentAnalysis"
EM_AGENT_NAME = "EMAgent"

# Timeouts shared by tools and server-side fetch paths that use the same client contract.
BACKEND_REQUEST_TIMEOUT_SECONDS = 30

PULSE_BASE_URL_ENV_KEY = "PULSE_BASE_URL"
DEFAULT_PULSE_BASE_URL = "http://localhost:8080"


def get_pulse_base_url() -> str:
    """Pulse-server base URL from ``PULSE_BASE_URL``, or :data:`DEFAULT_PULSE_BASE_URL` if unset/empty."""
    configured = os.getenv(PULSE_BASE_URL_ENV_KEY, "").strip()
    return configured or DEFAULT_PULSE_BASE_URL


# pulse_ai tools: user/session-scoped backend calls
PULSE_TOOL_SESSION_MISSING_CONTEXT = (
    "Chat session is missing tool context; cannot call the Pulse API."
)
PULSE_TOOL_SESSION_MISSING_BEARER = (
    "Authorization is missing from this chat session. Sign in again or start a new chat."
)
PULSE_TOOL_SESSION_MISSING_PROJECT = (
    "Project context is missing from this chat session. Select a project and try again."
)

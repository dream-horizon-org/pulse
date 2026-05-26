"""Validate ADK tool session state before calling pulse-server from tools."""

from __future__ import annotations

from typing import Any

from pulse_ai.constants import (
    PULSE_TOOL_SESSION_MISSING_BEARER,
    PULSE_TOOL_SESSION_MISSING_CONTEXT,
    PULSE_TOOL_SESSION_MISSING_PROJECT,
)


def pulse_tool_session_auth_error(tool_context: Any) -> dict | None:
    """Return a structured tool error if session state cannot safely call pulse-server.

    Use before constructing ``PulseClient(authorization_header=..., project_id=...)`` from ADK state.
    """
    context_missing = tool_context is None
    if context_missing:
        return {"status": "error", "message": PULSE_TOOL_SESSION_MISSING_CONTEXT}

    state = getattr(tool_context, "state", None)
    state_missing = state is None
    if state_missing:
        return {"status": "error", "message": PULSE_TOOL_SESSION_MISSING_CONTEXT}

    state_get = getattr(state, "get", None)
    is_readable_state = callable(state_get)
    if not is_readable_state:
        return {"status": "error", "message": PULSE_TOOL_SESSION_MISSING_CONTEXT}

    bearer_token = state_get("bearer_token")
    project_id = state_get("project_id")

    bearer_value = bearer_token if isinstance(bearer_token, str) else ""
    has_bearer = bool(bearer_value.strip())
    if not has_bearer:
        return {"status": "error", "message": PULSE_TOOL_SESSION_MISSING_BEARER}

    project_value = project_id if isinstance(project_id, str) else ""
    has_project = bool(project_value.strip())
    if not has_project:
        return {"status": "error", "message": PULSE_TOOL_SESSION_MISSING_PROJECT}

    return None


def pulse_tool_session_tenant_id(tool_context: Any) -> str | None:
    """Optional tenant id from ADK session state (interaction report pipeline)."""
    if tool_context is None:
        return None
    state = getattr(tool_context, "state", None)
    if state is None:
        return None
    state_get = getattr(state, "get", None)
    if not callable(state_get):
        return None
    tenant_id = state_get("tenant_id")
    if isinstance(tenant_id, str) and tenant_id.strip():
        return tenant_id.strip()
    return None


def pulse_client_kwargs_from_tool_context(tool_context: Any) -> dict[str, str]:
    """PulseClient constructor kwargs from validated tool session state."""
    bearer_token = tool_context.state.get("bearer_token")
    project_id = tool_context.state.get("project_id")
    kwargs: dict[str, str] = {
        "authorization_header": bearer_token,
        "project_id": project_id,
    }
    tenant_id = pulse_tool_session_tenant_id(tool_context)
    if tenant_id:
        kwargs["tenant_id"] = tenant_id
    return kwargs

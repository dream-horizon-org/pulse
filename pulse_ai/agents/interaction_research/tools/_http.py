"""Shared PulseClient helpers for interaction research tools."""

from __future__ import annotations

from google.adk.tools import ToolContext

from pulse_ai.client.pulse_client import PulseClient
from pulse_ai.tool_session_auth import (
    pulse_client_kwargs_from_tool_context,
    pulse_tool_session_auth_error,
)
from pulse_ai.agents.em.transformers.response_transformer import parse_error_response


async def pulse_get(
    path: str,
    *,
    params: dict | None = None,
    tool_context: ToolContext | None = None,
) -> dict:
    """Authenticated GET with standard success/error envelope."""
    session_error = pulse_tool_session_auth_error(tool_context)
    if session_error is not None:
        return session_error

    async with PulseClient(**pulse_client_kwargs_from_tool_context(tool_context)) as client:
        response = await client.request("GET", path, params=params or {})

        if isinstance(response, dict):
            return response

        if response.status_code >= 400:
            return parse_error_response(response)

        body = response.json()
        return {"status": "success", "data": body.get("data", body)}

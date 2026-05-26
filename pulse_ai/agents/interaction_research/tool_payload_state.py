"""Capture Interaction Research tool responses in session state (authoritative payloads)."""

from __future__ import annotations

import logging
from typing import Any

from pulse_ai.schemas.interaction_research_v1 import InteractionResearchV1

logger = logging.getLogger(__name__)

INTERACTION_RESEARCH_TOOL_PAYLOADS_KEY = "interaction_research_tool_payloads"

# Tool function name → InteractionResearchV1 field (last successful write wins for optional tools).
_TOOL_TO_RESEARCH_FIELD: dict[str, str] = {
    "fetch_interaction_config": "interaction_config",
    "fetch_interaction_metrics": "metrics_payload",
    "fetch_interaction_root_cause_segments": "rca_payload",
    "get_journey": "journey_payload",
    "get_funnel": "funnel_payload",
}


def _tool_name(tool: object) -> str | None:
    name = getattr(tool, "name", None)
    if isinstance(name, str) and name.strip():
        return name.strip()
    fn = getattr(tool, "func", None) or tool
    return getattr(fn, "__name__", None)


def capture_tool_response(
    *,
    tool: object,
    tool_response: object,
    state: dict[str, Any],
) -> None:
    """Persist successful tool JSON on session state for post-agent merge."""
    if not isinstance(tool_response, dict):
        return
    if tool_response.get("status") == "error":
        return

    tool_name = _tool_name(tool)
    if not tool_name:
        return

    payloads = state.get(INTERACTION_RESEARCH_TOOL_PAYLOADS_KEY)
    if not isinstance(payloads, dict):
        payloads = {}
    payloads[tool_name] = tool_response
    state[INTERACTION_RESEARCH_TOOL_PAYLOADS_KEY] = payloads


def _normalize_rca_payload(response: dict[str, Any]) -> dict[str, Any]:
    """RootCausePayloadSchema expects tabular shape, not the tool envelope."""
    if "baseline" in response and "segments" in response:
        return response
    data = response.get("data")
    if isinstance(data, dict):
        return data
    return response


def _extract_bad_session_ids(response: dict[str, Any]) -> list[str] | None:
    data = response.get("data")
    if not isinstance(data, list):
        return None
    ids: list[str] = []
    for row in data:
        if not isinstance(row, dict):
            continue
        sid = row.get("session_id") or row.get("sessionId") or row.get("trace_id")
        if isinstance(sid, str) and sid.strip():
            ids.append(sid.strip())
    return ids[:10] if ids else None


def apply_tool_payloads_to_research(
    research: InteractionResearchV1,
    tool_payloads: dict[str, Any] | None,
) -> InteractionResearchV1:
    """Overlay captured tool responses; overrides any LLM-copied payload strings."""
    if not tool_payloads:
        return research

    updates: dict[str, Any] = {}

    for tool_name, field in _TOOL_TO_RESEARCH_FIELD.items():
        raw = tool_payloads.get(tool_name)
        if not isinstance(raw, dict):
            continue
        if field == "rca_payload":
            updates[field] = _normalize_rca_payload(raw)
        else:
            updates[field] = raw

    bad_raw = tool_payloads.get("fetch_bad_interaction_sessions")
    if isinstance(bad_raw, dict):
        session_ids = _extract_bad_session_ids(bad_raw)
        if session_ids:
            updates["bad_session_ids"] = session_ids

    if not updates:
        return research

    return research.model_copy(update=updates)

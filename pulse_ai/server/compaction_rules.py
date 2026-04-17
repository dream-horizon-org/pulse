"""Tool-type-aware compaction rules for CompactingSessionService.

Each of the 7 EM tools gets a dedicated summarizer that extracts key metrics
into a short structured string (~80-200 tokens) instead of keeping the raw
JSON payload (~1,000-3,000 tokens).

Public API: compact_tool_response(tool_name, response) -> str
"""

from __future__ import annotations

from typing import Any


def compact_tool_response(tool_name: str, response: Any) -> str:
    """Return a structured summary string for a tool response.

    Handles:
    - None / missing response
    - Already-compacted responses (idempotent)
    - Error responses
    - Success responses (tool-type-aware)
    """
    # Guard: None
    if response is None:
        return f"[{tool_name}: error — empty response]"

    # Guard: already compacted — return existing summary unchanged
    if isinstance(response, dict) and response.get("compacted"):
        return response.get("summary", f"[{tool_name}: compacted]")

    # Guard: error status
    if isinstance(response, dict) and response.get("status") == "error":
        message = response.get("message", "unknown error")
        return f"[{tool_name}: error — {message}]"

    _compactors = {
        "query_interaction_health": _compact_health,
        "query_interaction_metrics": _compact_metrics,
        "breakdown_interaction": _compact_breakdown,
        "query_interactions": _compact_interactions,
        "query_alerts": _compact_alerts,
        "query_interaction_sessions": _compact_sessions,
        "calculate": _compact_calculate,
    }

    fn = _compactors.get(tool_name, _compact_generic)
    return fn(tool_name, response)


# ── Per-tool summarizers ──────────────────────────────────────────────────────


def _compact_health(tool_name: str, response: dict) -> str:
    data = response.get("data") or []
    if not data:
        return f"[{tool_name}: no data]"
    count = len(data)
    top = data[:3]
    parts = []
    for row in top:
        name = row.get("interactionName") or row.get("name", "?")
        apdex = row.get("apdex", "?")
        error_rate = row.get("errorRate") or row.get("error_rate", "?")
        parts.append(f"{name} (Apdex={apdex}, errors={error_rate})")
    return f"[{tool_name}: {count} interactions. Top: {', '.join(parts)}]"


def _compact_metrics(tool_name: str, response: dict) -> str:
    data = response.get("data") or []
    if not data:
        return f"[{tool_name}: no data]"
    row = data[0]
    fields = []
    for key in ("apdex", "p50", "p95", "errorRate", "error_rate", "crashRate"):
        if key in row:
            fields.append(f"{key}={row[key]}")
    summary = ", ".join(fields) if fields else f"{len(data)} rows"
    return f"[{tool_name}: {summary}]"


def _compact_breakdown(tool_name: str, response: dict) -> str:
    data = response.get("data") or []
    if not data:
        return f"[{tool_name}: no data]"
    count = len(data)
    # Identify the dimension key: first key that is not a known metric
    _metric_keys = {"apdex", "p50", "p95", "errorrate", "error_rate",
                    "count", "total", "crashrate", "status"}
    first_row = data[0]
    dim_key = next(
        (k for k in first_row if k.lower() not in _metric_keys),
        "segment",
    )
    top = data[:3]
    parts = []
    for row in top:
        seg = row.get(dim_key, "?")
        apdex = row.get("apdex", "?")
        parts.append(f"{seg} (Apdex={apdex})")
    return f"[{tool_name}: {count} segments — {', '.join(parts)}]"


def _compact_interactions(tool_name: str, response: dict) -> str:
    data = response.get("data")
    if isinstance(data, list):
        return f"[{tool_name}: {len(data)} interactions returned]"
    if isinstance(data, dict):
        name = data.get("name", "interaction")
        return f"[{tool_name}: config for {name}]"
    return f"[{tool_name}: data returned]"


def _compact_alerts(tool_name: str, response: dict) -> str:
    data = response.get("data")
    if isinstance(data, list):
        return f"[{tool_name}: {len(data)} alerts returned]"
    if isinstance(data, dict):
        name = data.get("name") or data.get("id", "alert")
        return f"[{tool_name}: alert {name}]"
    return f"[{tool_name}: data returned]"


def _compact_sessions(tool_name: str, response: dict) -> str:
    data = response.get("data") or []
    count = len(data) if isinstance(data, list) else 1
    return f"[{tool_name}: {count} sessions returned]"


def _compact_calculate(tool_name: str, response: dict) -> str:
    expr = response.get("expression", "?")
    result = response.get("result", "?")
    return f"[{tool_name}: {expr} = {result}]"


def _compact_generic(tool_name: str, response: dict) -> str:
    data = response.get("data")
    if isinstance(data, list):
        return f"[{tool_name}: {len(data)} items returned]"
    return f"[{tool_name}: data returned]"

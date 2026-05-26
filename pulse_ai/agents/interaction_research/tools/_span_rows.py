"""Normalize problematic interaction span rows for Agent 1 tools."""

from __future__ import annotations

from typing import Any


def _duration_to_ms(duration: Any) -> float | None:
    if duration is None or duration == "":
        return None
    try:
        ns = float(duration)
    except (TypeError, ValueError):
        return None
    return round(ns / 1_000_000, 2)


def normalize_problematic_span_row(row: dict[str, Any]) -> dict[str, Any]:
    """Map distribution column aliases to stable keys for reports."""
    session_id = row.get("sessionid") or row.get("session_id") or row.get("sessionId")
    trace_id = row.get("traceid") or row.get("trace_id") or row.get("traceId")
    normalized: dict[str, Any] = {
        "session_id": session_id,
        "trace_id": trace_id,
        "timestamp": row.get("interaction_timestamp") or row.get("timestamp"),
        "duration_ms": _duration_to_ms(row.get("duration")),
        "status_code": row.get("status_code"),
        "user_category": row.get("user_category"),
        "event_names": row.get("event_names") or "",
        "device": row.get("device"),
        "os_version": row.get("os_version"),
        "os_name": row.get("os_name"),
        "state": row.get("state"),
        "country": row.get("country"),
        "frozen_frame": row.get("frozen_frame"),
    }
    return {k: v for k, v in normalized.items() if v not in (None, "")}


def merge_problematic_span_payloads(
    existing: dict[str, Any] | None,
    incoming: dict[str, Any],
    *,
    max_rows: int = 10,
) -> dict[str, Any]:
    """Merge successive span_kind calls (error then poor) without duplicate session+trace pairs."""
    if not isinstance(existing, dict) or existing.get("status") != "success":
        return incoming

    old_rows = existing.get("data")
    new_rows = incoming.get("data")
    if not isinstance(old_rows, list) or not isinstance(new_rows, list):
        return incoming

    seen: set[tuple[str, str]] = set()
    merged: list[dict[str, Any]] = []
    for row in old_rows + new_rows:
        if not isinstance(row, dict):
            continue
        key = (str(row.get("session_id", "")), str(row.get("trace_id", "")))
        if key in seen:
            continue
        seen.add(key)
        merged.append(row)
        if len(merged) >= max_rows:
            break

    kinds = list(existing.get("span_kinds") or [])
    if not kinds and isinstance(existing.get("span_kind"), str):
        kinds.append(existing["span_kind"])
    kind = incoming.get("span_kind")
    if isinstance(kind, str) and kind not in kinds:
        kinds.append(kind)

    return {
        **incoming,
        "data": merged,
        "span_kinds": kinds,
        "count": len(merged),
    }


def merge_breakdown_payloads(
    existing: dict[str, Any] | None,
    incoming: dict[str, Any],
) -> dict[str, Any]:
    """Merge successive dimension calls into breakdowns list."""
    dimension = incoming.get("dimension")
    entry = {
        "dimension": dimension,
        "data": incoming.get("data") if isinstance(incoming.get("data"), list) else [],
    }
    if not isinstance(existing, dict) or existing.get("status") != "success":
        return {
            **incoming,
            "breakdowns": [entry],
        }

    breakdowns = [
        b
        for b in (existing.get("breakdowns") or [])
        if isinstance(b, dict) and b.get("dimension") != dimension
    ]
    breakdowns.append(entry)
    return {
        **incoming,
        "breakdowns": breakdowns,
    }

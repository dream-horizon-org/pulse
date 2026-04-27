"""Tool-type-aware compaction rules for CompactingSessionService.

Each of the 7 EM tools gets a dedicated summarizer that extracts key metrics
into a short structured string (~80-250 tokens) instead of keeping the raw
JSON payload (~1,000-3,000 tokens).

Public API: compact_tool_response(tool_name, response) -> str
"""

from __future__ import annotations

from collections import Counter
from typing import Any


def compact_tool_response(tool_name: str, response: Any) -> str:
    """Return a structured summary string for a tool response.

    Handles:
    - None / missing response
    - Already-compacted responses (idempotent)
    - Error responses
    - Success responses (tool-type-aware)
    """
    if response is None:
        return f"[{tool_name}: error — empty response]"

    if isinstance(response, dict) and response.get("compacted"):
        return response.get("summary", f"[{tool_name}: compacted]")

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
        name = (
            row.get("interaction_name")
            or row.get("interactionName")
            or row.get("name", "?")
        )
        apdex = row.get("apdex", "?")
        p50 = row.get("p50", "")
        spanfreq = row.get("spanfreq") or 1

        # error rate: use pre-computed field if present (test compat), else compute
        error_rate_raw = row.get("errorRate") or row.get("error_rate")
        if error_rate_raw is not None:
            error_pct = f"{float(error_rate_raw) * 100:.1f}%"
        else:
            error_count = row.get("error_count", 0) or 0
            error_pct = f"{error_count / spanfreq * 100:.1f}%"

        user_poor = row.get("user_poor", 0) or 0
        poor_pct = f"{user_poor / spanfreq * 100:.1f}%"

        user_excellent = row.get("user_excellent", 0) or 0
        excellent_pct = f"{user_excellent / spanfreq * 100:.1f}%"

        seg = f"{name} (Apdex={apdex}"
        if p50:
            seg += f", p50={p50}ms"
        seg += f", errors={error_pct}, poor={poor_pct}, excellent={excellent_pct})"
        parts.append(seg)
    return f"[{tool_name}: {count} interactions. Top: {', '.join(parts)}]"


def _compact_metrics(tool_name: str, response: dict) -> str:
    data = response.get("data") or []
    if not data:
        return f"[{tool_name}: no data]"

    row = data[0]

    if "t1" in row:
        return _compact_metrics_timeseries(tool_name, data)

    parts = []

    if "apdex" in row:
        parts.append(f"apdex={row['apdex']}")

    latency = []
    if "p50" in row:
        latency.append(f"p50={row['p50']}ms")
    if "p95" in row:
        latency.append(f"p95={row['p95']}ms")
    if latency:
        parts.append(", ".join(latency))

    if "success_count" in row or "error_count" in row:
        success = row.get("success_count", 0) or 0
        errors = row.get("error_count", 0) or 0
        total = success + errors
        rate_str = f"{errors / total * 100:.1f}%" if total else "n/a"
        parts.append(f"success={success}, errors={rate_str}")

    vitals = []
    if "crash" in row:
        vitals.append(f"crash={row['crash']}")
    if "anr" in row:
        vitals.append(f"anr={row['anr']}")
    if "frozen_frame" in row:
        vitals.append(f"frozen={row['frozen_frame']}")
    if vitals:
        parts.append(", ".join(vitals))

    net = []
    for key, label in [("net_2xx", "2xx"), ("net_4xx", "4xx"), ("net_5xx", "5xx"), ("net_0", "err")]:
        if key in row and row[key]:
            net.append(f"{label}={row[key]}")
    if net:
        parts.append("net: " + ", ".join(net))

    if "user_excellent" in row or "user_poor" in row:
        total_users = (
            (row.get("user_excellent") or 0)
            + (row.get("user_good") or 0)
            + (row.get("user_avg") or 0)
            + (row.get("user_poor") or 0)
        )
        if total_users:
            excellent_pct = f"{(row.get('user_excellent') or 0) / total_users * 100:.0f}%"
            poor_pct = f"{(row.get('user_poor') or 0) / total_users * 100:.0f}%"
            parts.append(f"users: excellent={excellent_pct}, poor={poor_pct}")

    summary = " | ".join(parts) if parts else f"{len(data)} rows"
    return f"[{tool_name}: {summary}]"


def _compact_metrics_timeseries(tool_name: str, data: list) -> str:
    count = len(data)
    metric_keys = [k for k in data[0] if k != "t1" and isinstance(data[0][k], (int, float))]
    trend_parts = []
    for key in metric_keys[:2]:
        values = [row[key] for row in data if key in row and row[key] is not None]
        if len(values) < 2:
            continue
        min_val = min(values)
        max_val = max(values)
        last_val = values[-1]
        first_val = values[0]
        if last_val > first_val * 1.05:
            direction = "rising"
        elif last_val < first_val * 0.95:
            direction = "falling"
        elif max_val > first_val * 1.1:
            direction = "recovered"
        else:
            direction = "stable"
        unit = "ms" if key in ("p50", "p95") else ""
        trend_parts.append(f"{key}: min={min_val}{unit}, max={max_val}{unit}, last={last_val}{unit} ({direction})")
    trend_str = ", ".join(trend_parts) if trend_parts else f"{count} pts"
    return f"[{tool_name}: timeseries {count} pts — {trend_str}]"


_METRIC_KEYS_SET = {
    "apdex", "p50", "p95", "count", "total", "status", "spanfreq",
    "crashrate", "error_rate", "errorrate", "errorRate",
    "frozen_frame", "unanalysed_frame", "analysed_frame",
    "crash", "anr", "success_count", "error_count", "user_poor",
    "user_excellent", "user_good", "user_avg",
    "net_0", "net_2xx", "net_4xx", "net_5xx",
}

_METRIC_PRIORITY = [
    ("apdex",        lambda v: f"Apdex={v}"),
    ("crash",        lambda v: f"crash={v}"),
    ("anr",          lambda v: f"anr={v}"),
    ("frozen_frame", lambda v: f"frozen={v}"),
    ("p95",          lambda v: f"p95={v}ms"),
    ("p50",          lambda v: f"p50={v}ms"),
    ("user_poor",    lambda v: f"poor={v}"),
    ("error_count",  lambda v: f"errors={v}"),
    ("success_count",lambda v: f"success={v}"),
]


def _compact_breakdown(tool_name: str, response: dict) -> str:
    data = response.get("data") or []
    if not data:
        return f"[{tool_name}: no data]"
    count = len(data)
    first_row = data[0]

    dim_key = next(
        (k for k in first_row if k.lower() not in _METRIC_KEYS_SET),
        "segment",
    )

    top = data[:5]
    parts = []
    for row in top:
        seg = row.get(dim_key, "?")
        metrics = []
        for field_key, fmt in _METRIC_PRIORITY:
            if field_key in row and row[field_key] is not None:
                metrics.append(fmt(row[field_key]))
                if len(metrics) == 3:
                    break
        metrics_str = ", ".join(metrics) if metrics else "?"
        parts.append(f"{seg} ({metrics_str})")

    return f"[{tool_name}: {count} segments — {', '.join(parts)}]"


def _compact_interactions(tool_name: str, response: dict) -> str:
    data = response.get("data")
    if isinstance(data, list):
        count = len(data)
        names = [
            d.get("name") or d.get("interaction_name") or d.get("interactionName", "?")
            for d in data[:5]
        ]
        names_str = ", ".join(names)
        suffix = f" + {count - 5} more" if count > 5 else ""
        return f"[{tool_name}: {count} interactions — {names_str}{suffix}]"
    if isinstance(data, dict):
        name = data.get("name", "?")
        status = data.get("status", "")
        threshold = data.get("apdexThreshold", "")
        parts = [str(name)]
        if status:
            parts.append(f"status={status}")
        if threshold:
            parts.append(f"apdexThreshold={threshold}ms")
        return f"[{tool_name}: {' | '.join(parts)}]"
    return f"[{tool_name}: data returned]"


def _compact_alerts(tool_name: str, response: dict) -> str:
    data = response.get("data")
    if isinstance(data, list):
        count = len(data)
        parts = []
        for d in data[:5]:
            name = d.get("name") or d.get("id", "?")
            state = d.get("state") or d.get("status", "")
            entry = f"{name} ({state})" if state else str(name)
            parts.append(entry)
        names_str = ", ".join(parts)
        suffix = f" + {count - 5} more" if count > 5 else ""
        return f"[{tool_name}: {count} alerts — {names_str}{suffix}]"
    if isinstance(data, dict):
        name = data.get("name") or data.get("id", "alert")
        alert_id = data.get("id", "")
        state = data.get("state") or data.get("status", "")
        threshold = data.get("threshold") or data.get("definition", "")
        parts = [str(name)]
        if alert_id and alert_id != name:
            parts.append(f"id={alert_id}")
        if state:
            parts.append(f"state={state}")
        if threshold:
            parts.append(f"threshold: {threshold}")
        return f"[{tool_name}: {' | '.join(parts)}]"
    return f"[{tool_name}: data returned]"


def _compact_sessions(tool_name: str, response: dict) -> str:
    data = response.get("data") or []
    if not data:
        return f"[{tool_name}: no data]"

    first = data[0] if isinstance(data, list) else data

    if isinstance(first, dict) and "total_sessions" in first:
        row = first
        fields = [
            ("total_sessions", "total"),
            ("success_count", "success"),
            ("error_count", "errors"),
            ("crash", "crash"),
            ("anr", "anr"),
            ("apdex", "apdex"),
            ("p50", "p50"),
        ]
        parts = []
        for key, label in fields:
            val = row.get(key)
            if val is not None:
                suffix = "ms" if key == "p50" else ""
                parts.append(f"{label}={val}{suffix}")
        return f"[{tool_name}: stats — {', '.join(parts)}]"

    count = len(data)
    sections = []
    for field, label in [
        ("status_code", "status"),
        ("platform", "platforms"),
        ("app_version", "version"),
        ("os_version", "os"),
        ("device", "device"),
    ]:
        dist = Counter(r.get(field, "?") for r in data if r.get(field))
        if dist:
            top = ", ".join(f"{k}={v}" for k, v in dist.most_common(2))
            sections.append(f"{label}: {top}")
    sections_str = " | ".join(sections) if sections else "no metadata"
    return f"[{tool_name}: {count} sessions — {sections_str}]"


def _compact_calculate(tool_name: str, response: dict) -> str:
    expr = response.get("expression", "?")
    result = response.get("result", "?")
    return f"[{tool_name}: {expr} = {result}]"


def _compact_generic(tool_name: str, response: dict) -> str:
    data = response.get("data")
    if isinstance(data, list):
        return f"[{tool_name}: {len(data)} items returned]"
    return f"[{tool_name}: data returned]"

"""Tool 5: query_interaction_health — Health summary for top interactions.

Uses build_health_query template → PulseClient POST → transform response.
"""

import json

from google.adk.tools import ToolContext

from pulse_ai.client.pulse_client import PulseClient
from pulse_ai.constants import (
    ERROR_RATE_CRITICAL_MIN,
    ERROR_RATE_ELEVATED_MIN,
    INTERACTION_HEALTH_MIN_VOLUME,
)
from pulse_ai.tool_session_auth import pulse_tool_session_auth_error
from pulse_ai.agents.em.templates.base import TIME_RANGE_DOC
from pulse_ai.agents.em.templates.interaction_templates import build_health_query
from pulse_ai.agents.em.transformers.response_transformer import (
    parse_error_response,
    transform_columnar,
)

DATA_QUERY_PATH = "/v1/interactions/performance-metric/distribution"


def _enrich_health_row(row: dict) -> dict:
    """Add pre-computed metrics to a health row.

    All numeric fields (poor_user_rate, error_rate, severity) are computed here
    so the LLM never performs arithmetic — it copies pre-formatted strings verbatim.
    Severity is derived from apdex, matching the card UI labels exactly.
    """
    excellent = row.get("user_excellent") or 0
    good = row.get("user_good") or 0
    avg = row.get("user_avg") or 0
    poor = row.get("user_poor") or 0
    total_categorized = excellent + good + avg + poor

    if total_categorized > 0:
        poor_user_rate = round(poor / total_categorized * 100, 1)
    else:
        poor_user_rate = None

    apdex = row.get("apdex") or 0.0
    if apdex >= 0.8:
        severity = "EXCELLENT"
    elif apdex >= 0.6:
        severity = "GOOD"
    elif apdex >= 0.4:
        severity = "FAIR"
    else:
        severity = "POOR"

    success = row.get("success_count") or 0
    error = row.get("error_count") or 0
    total_spans = success + error
    if total_spans > 0:
        error_rate = round(error / total_spans * 100, 1)
        if error_rate > ERROR_RATE_CRITICAL_MIN:
            error_severity = "CRITICAL_ERROR_RATE"
        elif error_rate > ERROR_RATE_ELEVATED_MIN:
            error_severity = "ELEVATED_ERROR_RATE"
        else:
            error_severity = "NORMAL_ERROR_RATE"
    else:
        error_rate = None
        error_severity = "UNKNOWN"

    # Pre-formatted display strings — LLM must copy these verbatim, never recalculate.
    apdex_str = f"{apdex:.2f}"
    poor_user_rate_str = f"{poor_user_rate:.1f}%" if poor_user_rate is not None else "N/A"
    error_rate_str = f"{error_rate:.1f}%" if error_rate is not None else "N/A"

    return {
        **row,
        "poor_user_rate": poor_user_rate,
        "severity": severity,
        "error_rate": error_rate,
        "error_severity": error_severity,
        "total_spans": total_spans,
        "total_categorized": total_categorized,
        "apdex_str": apdex_str,
        "poor_user_rate_str": poor_user_rate_str,
        "error_rate_str": error_rate_str,
    }


def _assign_priority_ranks(rows: list[dict]) -> list[dict]:
    """Assign numeric priority_rank to each row — Python decides order, LLM reads it.

    Ranking:
      POOR interactions: rank 1..N sorted by error_rate desc, poor_user_rate desc
      FAIR with elevated/critical errors: next ranks
      GOOD with elevated/critical errors: last ranks
      EXCELLENT (any error rate): priority_rank=None — NEVER in priority list
    """
    def priority_key(r):
        sev = r.get("severity")
        err_sev = r.get("error_severity")
        elevated = err_sev in ("ELEVATED_ERROR_RATE", "CRITICAL_ERROR_RATE")
        if sev == "POOR":
            # Volume first — more users impacted = higher priority.
            # If volumes are in similar ranges, higher error rate breaks the tie.
            return (0, -(r.get("total_categorized") or 0), -(r.get("error_rate") or 0), -(r.get("poor_user_rate") or 0))
        if sev == "FAIR" and elevated:
            return (1, -(r.get("error_rate") or 0), -(r.get("poor_user_rate") or 0))
        if sev == "GOOD" and elevated:
            return (2, -(r.get("error_rate") or 0), 0)
        return None  # EXCELLENT always excluded; FAIR/GOOD without elevated excluded

    indexed = [(i, r, priority_key(r)) for i, r in enumerate(rows)]
    eligible = sorted(
        [(i, r, k) for i, r, k in indexed if k is not None],
        key=lambda x: x[2],
    )

    result = [r.copy() for r in rows]
    for rank_num, (orig_idx, _, _) in enumerate(eligible, start=1):
        # Cap at 3 — UI shows at most 3 priorities
        result[orig_idx]["priority_rank"] = rank_num if rank_num <= 3 else None
    for r in result:
        r.setdefault("priority_rank", None)

    return result


async def query_interaction_health(
    top_n: int = 10,
    interaction_names: list[str] = None,
    time_range: str = "last_24h",
    start_time: str = None,
    end_time: str = None,
    filters: str = None,
    tool_context: ToolContext = None,
) -> dict:
    """Get health summary for top interactions — Apdex, errors, P50 latency, user categories.

    Args:
        top_n: Number of top interactions to show (default 10)
        interaction_names: Optional list of specific interaction names to query
        time_range: One of: """ + TIME_RANGE_DOC + """
        start_time: ISO 8601 start (only when time_range="custom")
        end_time: ISO 8601 end (only when time_range="custom")
        filters: Optional dimension filters as a JSON string. Valid keys:
            "platform"    → e.g. '{"platform": "Android"}' or '{"platform": "iOS"}'
            "app_version" → e.g. '{"app_version": "5.29.1"}' or '{"app_version": ["5.29.0", "5.29.1"]}'
            "device"      → e.g. '{"device": "Samsung Galaxy S21"}'
            "os_version"  → e.g. '{"os_version": "14.0"}'
            "network"     → e.g. '{"network": "WiFi"}' or '{"network": "4G"}'
            "region"      → currently state names e.g. '"region": "Maharashtra"' or '"region": "Karnataka"';
                            in future may also accept country names e.g. '"region": "India"' or '"region": "Canada"'
            Multiple:       '{"platform": "Android", "app_version": "5.29.1"}'
            Values can be a single string or a list of strings for multi-value filtering.
    """
    # Parse filters JSON string → dict
    parsed_filters = None
    if filters:
        try:
            parsed_filters = json.loads(filters)
        except (json.JSONDecodeError, TypeError):
            return {"status": "error", "message": f"Invalid JSON in filters: {filters}"}

    try:
        query_request = build_health_query(
            top_n=top_n,
            interaction_names=interaction_names,
            time_range=time_range,
            start_time=start_time,
            end_time=end_time,
            user_filters=parsed_filters,
        )
    except ValueError as e:
        return {"status": "error", "message": str(e)}

    session_error = pulse_tool_session_auth_error(tool_context)
    if session_error is not None:
        return session_error

    bearer_token = tool_context.state.get("bearer_token")
    project_id = tool_context.state.get("project_id")
    async with PulseClient(
        authorization_header=bearer_token,
        project_id=project_id,
    ) as client:
        response = await client.request("POST", DATA_QUERY_PATH, json=query_request)

        # Handle network errors (PulseClient returns dict on connection/timeout)
        if isinstance(response, dict):
            return response

        # Handle HTTP errors
        if response.status_code >= 400:
            return parse_error_response(response)

        # Transform columnar response → enrich → filter low-volume → assign priority ranks.
        body = response.json()
        data = body.get("data", {})
        enriched = [_enrich_health_row(row) for row in transform_columnar(data)]
        # Drop interactions below the minimum user volume threshold — too little data
        # to draw reliable conclusions. Uses total_categorized (distinct categorized users),
        # not span count, because one user can generate multiple spans.
        sufficient = [r for r in enriched if (r.get("total_categorized") or 0) >= INTERACTION_HEALTH_MIN_VOLUME]
        rows = _assign_priority_ranks(sufficient)

        # Accumulate health data in session state so the runner can build
        # the number-accurate summary without relying on LLM arithmetic.
        if tool_context is not None:
            existing: dict = tool_context.state.get("health_data") or {}
            for row in rows:
                name = row.get("interaction_name")
                if name:
                    existing[name] = row
            tool_context.state["health_data"] = existing

        return {"status": "success", "data": rows}

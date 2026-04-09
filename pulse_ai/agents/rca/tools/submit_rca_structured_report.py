from __future__ import annotations

import json

from google.adk.tools import ToolContext
from pydantic import ValidationError

from pulse_ai.schemas.rca_structured_v1 import RcaStructuredReportV1


async def submit_rca_structured_report(
    report_json: str,
    # ADK injects by name; must stay ``tool_context`` and match other Pulse tools
    # (``ToolContext = None``, not ``ToolContext | None`` — the union breaks AFC schema).
    tool_context: ToolContext = None,
) -> dict:
    """Submit the versioned RCA structured report (v1) as a JSON object.

    Args:
        report_json: JSON string matching RcaStructuredReportV1 (snake_case keys).
            Must include version: 1, executive_summary, segments (rank, title, metrics, optional impact,
            and OPTIONAL affected_sessions array), and recommendations. Each metric row must use a registered 
            metric_id from the backend (volume, apdex, error_rate, poor_user_pct, duration_p50, duration_p95, 
            crash_rate, anr_rate, frozen_frame_rate, slow_frame_rate).
            
            IMPORTANT: For each segment, include an "affected_sessions" field as an array of session IDs
            that demonstrate or support the segment's findings. Example segment structure:
            {
              "rank": 1,
              "title": "Platform Android + OsVersion 14",
              "metrics": [...],
              "impact": "...",
              "affected_sessions": ["sess-123", "sess-456"]  // INCLUDE THIS in every segment
            }
    """
    try:
        data = json.loads(report_json) if isinstance(report_json, str) else report_json
    except (json.JSONDecodeError, TypeError) as exc:
        return {"success": False, "error": f"Invalid JSON: {exc}"}

    if not isinstance(data, dict):
        return {"success": False, "error": "report_json must decode to a JSON object"}

    try:
        validated = RcaStructuredReportV1.model_validate(data)
    except ValidationError as exc:
        return {"success": False, "errors": exc.errors()}

    return {"success": True, "structured": validated.model_dump(mode="json")}

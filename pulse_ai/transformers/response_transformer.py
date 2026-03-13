"""Response transformers — columnar to dict, error parsing, value parsing.

Converts backend response formats into human-readable structures
that the LLM can interpret and present to the user.
"""

from __future__ import annotations

import httpx


# ---------------------------------------------------------------------------
# Value parsing
# ---------------------------------------------------------------------------


def _parse_value(val: str | None) -> int | float | str | None:
    """Parse a string value from ClickHouse into the appropriate Python type.

    All row values from the backend are strings. This converts:
    - Integer strings → int
    - Float strings → float (rounded to 4 decimal places)
    - Everything else → returned as-is

    Args:
        val: The string value to parse.

    Returns:
        Parsed int, float, original string, or None.
    """
    if val is None:
        return None
    if val == "":
        return ""

    try:
        if "." in val:
            return round(float(val), 4)
        return int(val)
    except (ValueError, TypeError):
        return val


# ---------------------------------------------------------------------------
# Columnar → dict transformation
# ---------------------------------------------------------------------------


def transform_columnar(data: dict | None) -> list[dict]:
    """Convert columnar {fields: [...], rows: [[...]]} to list of dicts.

    Backend returns:
        {"fields": ["name", "apdex", "p50"], "rows": [["ContestJoin", "0.52", "890"]]}

    This converts to:
        [{"name": "ContestJoin", "apdex": 0.52, "p50": 890}]

    Args:
        data: The backend response data with fields and rows.

    Returns:
        List of dicts with parsed values.
    """
    if not data:
        return []

    fields = data.get("fields", [])
    rows = data.get("rows", [])

    if not fields or not rows:
        return []

    return [
        {fields[i]: _parse_value(row[i]) for i in range(len(fields))}
        for row in rows
    ]


# ---------------------------------------------------------------------------
# Error response parsing
# ---------------------------------------------------------------------------


def parse_error_response(response: httpx.Response) -> dict:
    """Parse a backend error response into a structured error dict.

    Handles two backend error formats:
    - Standard: {"data": null, "error": {"code": "...", "message": "..."}}
    - Validation: {"errors": ["msg1", "msg2"]}

    Args:
        response: The httpx Response with an error status code.

    Returns:
        Dict with {"status": "error", "message": "..."}.
    """
    try:
        body = response.json()

        # Format 1: Standard backend error
        if "error" in body and body["error"]:
            return {
                "status": "error",
                "message": body["error"].get("message", "Unknown error"),
            }

        # Format 2: Validation errors (interaction create/update)
        if "errors" in body:
            return {
                "status": "error",
                "message": "; ".join(body["errors"]),
            }

    except Exception:
        pass

    # Fallback: unparseable response
    return {"status": "error", "message": f"HTTP {response.status_code}"}

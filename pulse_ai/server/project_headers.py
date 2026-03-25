"""HTTP helpers for project-scoped AI session APIs."""

from __future__ import annotations

from fastapi import HTTPException, Request

PROJECT_HEADER = "X-Project-ID"


def require_x_project_id(request: Request) -> str:
    """Return trimmed X-Project-ID or raise 400."""
    raw = request.headers.get(PROJECT_HEADER)
    if raw is None or not str(raw).strip():
        raise HTTPException(
            status_code=400,
            detail=f"{PROJECT_HEADER} header is required",
        )
    return str(raw).strip()

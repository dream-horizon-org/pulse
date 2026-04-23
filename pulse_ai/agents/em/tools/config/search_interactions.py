"""Tool: search_interactions — Resolve interaction names via server-side substring search.

Delegates to query_interactions(scope="list", name=...) so HTTP, auth, and
error handling stay single-sourced.
"""
from google.adk.tools import ToolContext

from .query_interactions import query_interactions


async def search_interactions(
    search_query: str,
    page: int = 0,
    size: int = 10,
    status: str | None = None,
    tool_context: ToolContext = None,
) -> dict:
    """Search registered interactions by name substring (Pulse list API).

    Calls ``GET /v1/interactions`` with the ``name`` query parameter. The
    backend applies a case-sensitive substring match (SQL ``LIKE``) on the
    interaction ``name`` column. Results are paginated.

    Use this **before** ``query_interactions(scope="detail", ...)`` and before
    analytics tools when the user gives a natural-language or partial label and
    the exact registered interaction name may differ.

    After search, use the **exact** ``name`` field from a returned row for
    ``detail``, ``query_interaction_metrics``, ``query_interaction_health``,
    ``query_interaction_sessions``, and ``breakdown_interaction``.

    **Status filter:** By default (``status=None``) both RUNNING and STOPPED
    interactions are included. Pass ``status="RUNNING"`` or ``status="STOPPED"``
    when the user or context asks to narrow.

    Args:
        search_query: Non-empty substring to match against interaction names.
        page: Page index for pagination (default 0).
        size: Page size (default 10).
        status: Optional ``RUNNING`` / ``STOPPED`` filter; ``None`` means no filter.
        tool_context: Session context (injected by ADK).
    """
    if not search_query or not search_query.strip():
        return {
            "status": "error",
            "message": "search_query is required and must be non-blank.",
        }

    # Forward tool_context via a direct await of query_interactions — not a
    # second ADK tool dispatch — so bearer_token / project_id stay on the same
    # ToolContext.state. Refactoring this to indirect dispatch would break auth.
    return await query_interactions(
        scope="list",
        name=search_query.strip(),
        page=page,
        size=size,
        status=status,
        tool_context=tool_context,
    )

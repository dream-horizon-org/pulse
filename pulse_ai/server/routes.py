"""
API route handlers for the Pulse AI server.
"""

from __future__ import annotations

import logging
import uuid
from typing import Any

from fastapi import HTTPException, Request, Response
from fastapi.responses import StreamingResponse

from pulse_ai.constants import APP_NAME

from .app import RunSSERequest, app, runner, session_scope_store, session_service
from .project_headers import require_x_project_id
from .run_sse_utils import (
    ensure_session_for_run,
    request_headers_to_state_delta,
    stream_adk_run_as_sse,
    user_content_from_parts,
)
from .serializers import events_to_messages, extract_title

logger = logging.getLogger(__name__)


# TODO: Add authentication middleware to validate Bearer tokens before production deployment


@app.post("/run_sse")
async def run_sse(http_request: Request, body: RunSSERequest) -> StreamingResponse:
    """Run agent with SSE streaming. Streams text deltas and content blocks.

    Passes the request's Authorization header to tools via state_delta so
    tools can use it when calling the Pulse backend (Bearer token from client).
    """
    project_id = require_x_project_id(http_request)
    await ensure_session_for_run(
        session_service,
        session_scope_store,
        app_name=APP_NAME,
        user_id=body.user_id,
        session_id=body.session_id,
        project_id=project_id,
    )
    state_delta = request_headers_to_state_delta(http_request)
    new_message = user_content_from_parts(body.new_message.parts)

    return StreamingResponse(
        stream_adk_run_as_sse(
            runner=runner,
            session_service=session_service,
            user_id=body.user_id,
            session_id=body.session_id,
            new_message=new_message,
            state_delta=state_delta,
        ),
        media_type="text/event-stream",
    )


@app.post("/sessions")
async def create_session_endpoint(
    request: Request,
    user_id: str,
    session_id: str | None = None,
) -> dict[str, str]:
    """Create a new agent session."""
    project_id = require_x_project_id(request)
    sid = session_id or str(uuid.uuid4())
    session = await session_service.create_session(
        app_name=APP_NAME,
        user_id=user_id,
        session_id=sid,
    )
    # Prefer ADK session.id when set; fall back to sid so clients always get a
    # non-empty id consistent with create_session registration.
    returned_id = getattr(session, "id", None) or sid
    returned_id_str = str(returned_id)
    await session_scope_store.upsert(
        app_name=APP_NAME,
        user_id=user_id,
        session_id=returned_id_str,
        project_id=project_id,
    )
    return {"session_id": returned_id_str, "user_id": user_id}


# TODO: list_sessions does N+1 get_session calls to extract titles.
# Consider caching titles or storing them as session metadata once ADK supports it.
@app.get("/sessions/{user_id}")
async def list_sessions(request: Request, user_id: str) -> list[dict[str, Any]]:
    """List sessions for a user scoped to X-Project-ID, each with a derived title."""
    project_id = require_x_project_id(request)
    ids = await session_scope_store.list_session_ids_for_user_project(
        app_name=APP_NAME,
        user_id=user_id,
        project_id=project_id,
    )
    items: list[dict[str, Any]] = []
    for sid in ids:
        full = await session_service.get_session(
            app_name=APP_NAME,
            user_id=user_id,
            session_id=sid,
        )
        if not full:
            continue
        items.append({
            "id": full.id,
            "user_id": full.user_id,
            "title": extract_title(full.events),
            "last_update_time": full.last_update_time,
        })
    items.sort(key=lambda x: x["last_update_time"], reverse=True)
    return items


@app.get("/sessions/{user_id}/{session_id}")
async def get_session(
    request: Request,
    user_id: str,
    session_id: str,
) -> dict[str, Any]:
    """Get session details with pre-processed message history."""
    project_id = require_x_project_id(request)
    session = await session_service.get_session(
        app_name=APP_NAME,
        user_id=user_id,
        session_id=session_id,
    )
    if not session:
        raise HTTPException(status_code=404, detail="Session not found")
    stored = await session_scope_store.get_project_id(
        app_name=APP_NAME,
        user_id=user_id,
        session_id=session_id,
    )
    if stored is None or stored != project_id:
        raise HTTPException(status_code=404, detail="Session not found")
    return {
        "id": session.id,
        "user_id": session.user_id,
        "messages": events_to_messages(session.events),
        "last_update_time": session.last_update_time,
    }


@app.delete("/sessions/{user_id}/{session_id}")
async def delete_session_endpoint(
    request: Request,
    user_id: str,
    session_id: str,
) -> Response:
    """Delete ADK session and sidecar row when scoped; idempotent 204."""
    project_id = require_x_project_id(request)
    stored = await session_scope_store.get_project_id(
        app_name=APP_NAME,
        user_id=user_id,
        session_id=session_id,
    )
    if stored is None:
        return Response(status_code=204)
    if stored != project_id:
        raise HTTPException(status_code=404, detail="Session not found")
    await session_service.delete_session(
        app_name=APP_NAME,
        user_id=user_id,
        session_id=session_id,
    )
    try:
        await session_scope_store.delete(
            app_name=APP_NAME,
            user_id=user_id,
            session_id=session_id,
        )
    except Exception as e:
        logger.warning(
            "Sidecar delete failed after ADK delete (app=%s user=%s session=%s): %s",
            APP_NAME,
            user_id,
            session_id,
            e,
        )
    return Response(status_code=204)


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok"}

"""
API route handlers for the Pulse AI server.
"""

from __future__ import annotations

import json
import logging
import uuid
from typing import Any

from fastapi import Header, HTTPException
from fastapi.responses import StreamingResponse
from google.genai.types import Content, Part

from pulse_ai.constants import APP_NAME, DEFAULT_TITLE

from .app import RunSSERequest, app, rca_runner, runner, session_service
from .backend_client import BackendClientError, fetch_root_cause_payload
from .rca_runner import RcaRunnerError, generate_rca_report
from .schemas import RcaReportRequest, RcaReportResponse
from pulse_ai.schemas import RootCausePayloadSchema
from .serializers import DeltaTracker, events_to_messages, extract_content_blocks, extract_title

logger = logging.getLogger(__name__)


# TODO: Add authentication middleware to validate Bearer tokens before production deployment


@app.post("/run_sse")
async def run_sse(request: RunSSERequest) -> StreamingResponse:
    """Run agent with SSE streaming. Streams text deltas and content blocks."""
    session = await session_service.get_session(
        app_name=APP_NAME,
        user_id=request.user_id,
        session_id=request.session_id,
    )
    if not session:
        session = await session_service.create_session(
            app_name=APP_NAME,
            user_id=request.user_id,
            session_id=request.session_id,
        )

    parts = [Part.from_text(text=p.text) for p in request.new_message.parts]
    new_message = Content(role="user", parts=parts)

    async def event_stream():
        content_blocks: list[dict] = []
        tracker = DeltaTracker()

        try:
            async for event in runner.run_async(
                user_id=request.user_id,
                session_id=request.session_id,
                new_message=new_message,
            ):
                if not event.content or not event.content.parts:
                    continue

                texts, blocks = extract_content_blocks(
                    event.content.parts, event.author,
                )

                if blocks:
                    tracker.reset()
                    content_blocks.extend(blocks)

                for text in texts:
                    delta = tracker.push(text)
                    if delta:
                        yield f"data: {json.dumps({'type': 'text', 'content': delta})}\n\n"

        except Exception as e:
            logger.exception("Error during agent execution")
            yield f"data: {json.dumps({'type': 'error', 'message': str(e)})}\n\n"

        if content_blocks:
            yield f"data: {json.dumps({'type': 'content_blocks', 'blocks': content_blocks})}\n\n"
        yield "data: [DONE]\n\n"

    return StreamingResponse(event_stream(), media_type="text/event-stream")


@app.post("/sessions")
async def create_session_endpoint(user_id: str, session_id: str = "") -> dict[str, str]:
    """Create a new agent session."""
    sid = session_id or str(uuid.uuid4())
    session = await session_service.create_session(
        app_name=APP_NAME,
        user_id=user_id,
        session_id=sid,
    )
    return {"session_id": session.id, "user_id": user_id}


# TODO: list_sessions does N+1 get_session calls to extract titles.
# Consider caching titles or storing them as session metadata once ADK supports it.
@app.get("/sessions/{user_id}")
async def list_sessions(user_id: str) -> list[dict[str, Any]]:
    """List all sessions for a user, each with a derived title."""
    result = await session_service.list_sessions(
        app_name=APP_NAME,
        user_id=user_id,
    )
    items = []
    for s in (result.sessions or []):
        full = await session_service.get_session(
            app_name=APP_NAME, user_id=user_id, session_id=s.id,
        )
        items.append({
            "id": s.id,
            "user_id": s.user_id,
            "title": extract_title(full.events) if full else DEFAULT_TITLE,
            "last_update_time": s.last_update_time,
        })
    items.sort(key=lambda x: x["last_update_time"], reverse=True)
    return items


@app.get("/sessions/{user_id}/{session_id}")
async def get_session(user_id: str, session_id: str) -> dict[str, Any]:
    """Get session details with pre-processed message history."""
    session = await session_service.get_session(
        app_name=APP_NAME,
        user_id=user_id,
        session_id=session_id,
    )
    if not session:
        raise HTTPException(status_code=404, detail="Session not found")
    return {
        "id": session.id,
        "user_id": session.user_id,
        "messages": events_to_messages(session.events),
        "last_update_time": session.last_update_time,
    }


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/rca/report")
async def generate_root_cause_report(
    request: RcaReportRequest,
    authorization: str | None = Header(default=None, alias="Authorization"),
    project_id: str | None = Header(default=None, alias="X-Project-ID"),
    service_key: str | None = Header(default=None, alias="X-Pulse-Service-Key"),
) -> RcaReportResponse:
    """Generate a non-conversational RCA report for an interaction.

    Accepts root-cause data two ways (in priority order):
    1. **Embedded** – ``rootCausePayload`` in the request body (preferred; avoids callback auth).
    2. **Callback** – omit ``rootCausePayload``; pulse_ai calls pulse-server to fetch it.
    """
    try:
        if request.rootCausePayload is not None:
            payload = RootCausePayloadSchema.model_validate(request.rootCausePayload)
        else:
            payload = await fetch_root_cause_payload(
                interaction_name=request.interactionName,
                date_value=request.date,
                authorization=authorization,
                project_id=project_id,
                service_key=service_key,
            )
        return await generate_rca_report(
            runner=rca_runner,
            payload=payload,
            interaction_name=request.interactionName,
        )
    except BackendClientError as error:
        raise HTTPException(status_code=error.status_code, detail=error.message) from error
    except RcaRunnerError as error:
        raise HTTPException(status_code=error.status_code, detail=error.message) from error

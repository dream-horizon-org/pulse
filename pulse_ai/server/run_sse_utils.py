"""
Helpers for POST /run_sse: session ensure, state_delta from headers, SSE payload streaming.
"""

from __future__ import annotations

import json
import logging
from collections.abc import AsyncIterator, Sequence
from typing import TYPE_CHECKING, Any

from fastapi import HTTPException, Request
from google.adk.agents.run_config import RunConfig, StreamingMode
from google.adk.runners import Runner
from google.genai.types import Content, Part

from pulse_ai.constants import APP_NAME, REPORT_AGENT_NAME
from pulse_ai.output_guard import FilteredDeltaTracker
from pulse_ai.server.serializers import extract_content_blocks

if TYPE_CHECKING:
    from pulse_ai.server.session_scope_store import SessionScopeStore

logger = logging.getLogger(__name__)


def request_headers_to_state_delta(http_request: Request) -> dict[str, Any] | None:
    """Build ADK session state_delta from Authorization and X-Project-ID."""
    authorization = http_request.headers.get("Authorization")
    project_id = http_request.headers.get("X-Project-ID")
    state_delta: dict[str, Any] = {}
    if authorization:
        state_delta["bearer_token"] = authorization
    if project_id and project_id.strip():
        state_delta["project_id"] = project_id.strip()
    return state_delta or None


async def ensure_session_for_run(
    session_service: Any,
    scope_store: "SessionScopeStore",
    *,
    app_name: str,
    user_id: str,
    session_id: str,
    project_id: str,
) -> Any:
    """Load session or create it; enforce sidecar project_id (404 if wrong/missing row)."""
    session = await session_service.get_session(
        app_name=app_name,
        user_id=user_id,
        session_id=session_id,
    )
    if session:
        stored = await scope_store.get_project_id(
            app_name=app_name,
            user_id=user_id,
            session_id=session_id,
        )
        if stored is None or stored != project_id:
            raise HTTPException(status_code=404, detail="Session not found")
        return session
    created = await session_service.create_session(
        app_name=app_name,
        user_id=user_id,
        session_id=session_id,
    )
    await scope_store.upsert(
        app_name=app_name,
        user_id=user_id,
        session_id=session_id,
        project_id=project_id,
    )
    return created


def user_content_from_parts(parts: Sequence[Any]) -> Content:
    """Build Gemini Content for the user turn from RunSSERequest message parts."""
    genai_parts = [Part.from_text(text=p.text) for p in parts]
    return Content(role="user", parts=genai_parts)


def sse_data_line(payload: dict[str, Any]) -> str:
    """One Server-Sent Events `data:` line (including trailing newlines)."""
    return f"data: {json.dumps(payload)}\n\n"


def user_meta_payload_from_session_events(events: list[Any] | None) -> dict[str, Any] | None:
    """Latest user event in the session → meta payload for the client, or None."""
    if not events:
        return None
    for ev in reversed(events):
        if (
            getattr(ev, "author", None) == "user"
            and ev.content
            and ev.content.parts
        ):
            uid = getattr(ev, "id", "") or ""
            if not uid:
                return None
            meta: dict[str, Any] = {"type": "meta", "user_event_id": uid}
            inv = getattr(ev, "invocation_id", "") or ""
            if inv:
                meta["invocation_id"] = inv
            return meta
    return None


async def stream_adk_run_as_sse(
    *,
    runner: Runner,
    session_service: Any,
    user_id: str,
    session_id: str,
    new_message: Content,
    state_delta: dict[str, Any] | None,
) -> AsyncIterator[str]:
    """Run ADK agent and yield SSE `data:` lines (text, meta, content_blocks, error, [DONE])."""
    content_blocks: list[dict[str, Any]] = []
    tracker = FilteredDeltaTracker()
    user_meta_sent = False
    last_assistant_meta_id: str | None = None

    try:
        async for event in runner.run_async(
            user_id=user_id,
            session_id=session_id,
            new_message=new_message,
            state_delta=state_delta,
            run_config=RunConfig(streaming_mode=StreamingMode.SSE),
        ):
            if not user_meta_sent:
                user_meta_sent = True
                refreshed = await session_service.get_session(
                    app_name=APP_NAME,
                    user_id=user_id,
                    session_id=session_id,
                )
                meta_payload = user_meta_payload_from_session_events(
                    getattr(refreshed, "events", None) if refreshed else None,
                )
                if meta_payload:
                    yield sse_data_line(meta_payload)

            if (
                event.author == REPORT_AGENT_NAME
                and getattr(event, "id", None)
                and event.id != last_assistant_meta_id
            ):
                last_assistant_meta_id = event.id
                yield sse_data_line(
                    {"type": "meta", "assistant_event_id": event.id},
                )

            if not event.content or not event.content.parts:
                continue

            texts, blocks = extract_content_blocks(
                event.content.parts,
                event.author,
            )

            if blocks:
                tail = tracker.flush()
                if tail:
                    yield sse_data_line({"type": "text", "content": tail})
                tracker.reset()
                content_blocks.extend(blocks)

            for text in texts:
                delta = tracker.push(text)
                if delta:
                    yield sse_data_line({"type": "text", "content": delta})

    except Exception as e:
        logger.exception("Error during agent execution")
        yield sse_data_line({"type": "error", "message": str(e)})

    tail = tracker.flush()
    if tail:
        yield sse_data_line({"type": "text", "content": tail})
    if content_blocks:
        yield sse_data_line({"type": "content_blocks", "blocks": content_blocks})
    yield f"data: [DONE]\n\n"

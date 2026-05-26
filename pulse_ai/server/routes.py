"""
API route handlers for the Pulse AI server.
"""

from __future__ import annotations

import json
import logging
import uuid
from typing import Any

from fastapi import HTTPException, Header, Request, Response
from fastapi.responses import StreamingResponse
from google.genai.types import Content, Part

from pulse_ai.constants import APP_NAME

from .app import (
    RunSSERequest,
    app,
    interaction_report_runner,
    rca_runner,
    screen_rca_runner,
    session_rca_runner,
    runner,
    session_service,
    session_scope_store,
)
from .serializers import DeltaTracker, events_to_messages, extract_content_blocks, extract_title
from .project_headers import require_x_project_id
from .run_sse_utils import (
    ensure_session_for_run,
    request_headers_to_state_delta,
    stream_adk_run_as_sse,
    user_content_from_parts,
)
from pulse_ai.schemas import RootCausePayloadSchema
from .root_cause_fetch import RootCauseFetchError, fetch_root_cause_payload
from .rca_runner import RcaRunnerError, generate_rca_report
from .screen_rca_runner import ScreenRcaRunnerError, generate_screen_rca_report
from .session_rca_runner import SessionRcaRunnerError, generate_session_rca_report
from .interaction_report_runner import (
    InteractionReportRunnerError,
    generate_interaction_report,
    interaction_report_request_state_delta,
)
from .schemas import (
    InteractionReportGenerateRequest,
    InteractionReportGenerateResponse,
    RcaReportRequest,
    RcaReportResponse,
    ScreenRcaReportRequest,
    ScreenRcaReportResponse,
    SessionRcaReportRequest,
    SessionRcaReportResponse,
)

logger = logging.getLogger(__name__)

RCA_CALLBACK_BEARER_PREFIX = "Bearer "


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


def _require_headers_for_rca_callback(
    authorization: str | None,
    project_id: str | None,
) -> tuple[str, str]:
    """
    Pulse-server requires the user's JWT and project id for OpenFGA; callback path must
    forward the same headers the client sent to this endpoint (via pulse-server proxy).
    """
    if authorization is None or not authorization.strip():
        raise HTTPException(
            status_code=401,
            detail="Authorization header is required when rootCausePayload is omitted",
        )
    auth_stripped = authorization.strip()
    bearer_prefix_len = len(RCA_CALLBACK_BEARER_PREFIX)
    token_part = auth_stripped[bearer_prefix_len:].strip()
    is_missing_bearer = not auth_stripped.startswith(RCA_CALLBACK_BEARER_PREFIX)
    is_empty_token = not token_part
    if is_missing_bearer or is_empty_token:
        raise HTTPException(
            status_code=401,
            detail="Valid Bearer token is required when rootCausePayload is omitted",
        )
    if project_id is None or not project_id.strip():
        raise HTTPException(
            status_code=400,
            detail="X-Project-ID header is required when rootCausePayload is omitted",
        )
    return auth_stripped, project_id.strip()


@app.post("/interaction-report/generate")
async def generate_interaction_report_endpoint(
    request: InteractionReportGenerateRequest,
    http_request: Request,
) -> InteractionReportGenerateResponse:
    """Run Research → Schema pipeline and return InteractionReportV1."""
    # TODO: Remove this once we have a proper project id
    project_id: str = "TheSouledStoreApp-bV5Uk1m7"
    # project_id: str = project_id.strip()
    # project_id: str = "fancode"
    entity_key = "PaymentGatewayHandshakeLatency"
    if not entity_key:
        raise HTTPException(status_code=400, detail="entityKey is required")
    period_start = None
    period_end = None
    if request.periodStart:
        from datetime import date as date_type

        period_start = date_type.fromisoformat(request.periodStart[:10])
    if request.periodEnd:
        from datetime import date as date_type

        period_end = date_type.fromisoformat(request.periodEnd[:10])

    state_delta = interaction_report_request_state_delta(http_request)
    logger.info(
        "Interaction report generate request project_id=%s interaction=%s period_start=%s period_end=%s",
        project_id,
        entity_key,
        request.periodStart,
        request.periodEnd,
    )
    try:
        report = await generate_interaction_report(
            interaction_report_runner,
            project_id=project_id,
            interaction_name=entity_key,
            period_start=period_start,
            period_end=period_end,
            state_delta=state_delta,
        )
    except InteractionReportRunnerError as exc:
        logger.warning(
            "Interaction report generate failed project_id=%s interaction=%s status=%s detail=%s",
            project_id,
            entity_key,
            exc.status_code,
            exc.message,
        )
        raise HTTPException(status_code=exc.status_code, detail=exc.message) from exc

    logger.info(
        "Interaction report generate success project_id=%s interaction=%s rating=%s",
        project_id,
        entity_key,
        report.verdict.rating,
    )
    return InteractionReportGenerateResponse(
        report=report.model_dump(mode="json"),
        cached=False,
    )


@app.post("/rca/report")
async def generate_root_cause_report(
    request: RcaReportRequest,
    authorization: str | None = Header(default=None, alias="Authorization"),
    project_id: str | None = Header(default=None, alias="X-Project-ID"),
) -> RcaReportResponse:
    """Generate a non-conversational RCA report for an interaction.

    Accepts root-cause data two ways (in priority order):
    1. **Embedded** – ``rootCausePayload`` in the request body (preferred; avoids callback auth).
    2. **Callback** – omit ``rootCausePayload``; pulse_ai calls pulse-server to fetch it.
       Requires ``Authorization: Bearer <jwt>`` and ``X-Project-ID`` (forwarded by the proxy).
       Uses the async RCA pipeline (``/v1/ai/rca/report`` + job poll) and reads ``rootCausePayload``
       from the completed report.
    """
    try:
        if request.rootCausePayload is not None:
            example_sessions = None
            if isinstance(request.rootCausePayload, dict):
                example_sessions = request.rootCausePayload.get("exampleSessionIds")
            payload = RootCausePayloadSchema.model_validate(request.rootCausePayload)
        else:
            auth_value, project_value = _require_headers_for_rca_callback(
                authorization,
                project_id,
            )
            payload = await fetch_root_cause_payload(
                interaction_name=request.entityKey,
                date_value=request.date,
                authorization=auth_value,
                project_id=project_value,
            )
            example_sessions = None
        return await generate_rca_report(
            runner=rca_runner,
            payload=payload,
            interaction_name=request.entityKey,
            example_session_ids=example_sessions,
            error_attribution_payload=request.errorAttributionPayload,
            analysis_lookback_days=request.analysisLookbackDays,
        )
    except RootCauseFetchError as error:
        raise HTTPException(status_code=error.status_code, detail=error.message) from error
    except RcaRunnerError as error:
        raise HTTPException(status_code=error.status_code, detail=error.message) from error


@app.post("/rca/screen-report")
async def generate_screen_root_cause_narrative(
    request: ScreenRcaReportRequest,
) -> ScreenRcaReportResponse:
    """Generate executive summary and recommendations for screen-level frustration RCA.

    Requires **rootCausePayload** (tabular JSON from GET /v1/screens/{screen}/root-cause).
    """
    if not request.screenName or not str(request.screenName).strip():
        raise HTTPException(status_code=400, detail="screenName is required")
    try:
        payload = RootCausePayloadSchema.model_validate(request.rootCausePayload)
    except Exception as exc:  # noqa: BLE001
        raise HTTPException(
            status_code=400,
            detail=f"Invalid rootCausePayload: {exc}",
        ) from exc
    try:
        return await generate_screen_rca_report(
            runner=screen_rca_runner,
            payload=payload,
            screen_name=request.screenName.strip(),
            start_iso=request.start,
            end_iso=request.end,
            date_str=request.date,
            as_of_iso=request.asOf,
        )
    except ScreenRcaRunnerError as error:
        raise HTTPException(status_code=error.status_code, detail=error.message) from error


@app.post("/rca/session-report")
async def generate_session_root_cause_narrative(
    request: SessionRcaReportRequest,
) -> SessionRcaReportResponse:
    """Generate executive summary, segment insights, and recommendations for session quality RCA.

    Requires **rootCausePayload** (tabular JSON from GET /v1/sessions/rca).
    """
    try:
        payload = RootCausePayloadSchema.model_validate(request.rootCausePayload)
    except Exception as exc:  # noqa: BLE001
        raise HTTPException(
            status_code=400,
            detail=f"Invalid rootCausePayload: {exc}",
        ) from exc
    example_sessions_by_label: dict[str, list[str]] | None = None
    degrading_interactions_by_label: dict[str, list[dict]] | None = None
    if isinstance(request.rootCausePayload, dict):
        raw_segments = request.rootCausePayload.get("segments") or []
        by_label: dict[str, list[str]] = {}
        by_label_interactions: dict[str, list[dict]] = {}
        for seg in raw_segments:
            label = seg.get("label")
            ids = seg.get("exampleSessionIds")
            if label and ids:
                by_label[label] = ids
            interactions = seg.get("degradingInteractions")
            if label and interactions:
                by_label_interactions[label] = interactions
        if by_label:
            example_sessions_by_label = by_label
        if by_label_interactions:
            degrading_interactions_by_label = by_label_interactions
    try:
        return await generate_session_rca_report(
            runner=session_rca_runner,
            payload=payload,
            date_str=request.date,
            as_of_iso=request.asOf,
            example_sessions_by_label=example_sessions_by_label,
            degrading_interactions_by_label=degrading_interactions_by_label,
        )
    except SessionRcaRunnerError as error:
        raise HTTPException(status_code=error.status_code, detail=error.message) from error

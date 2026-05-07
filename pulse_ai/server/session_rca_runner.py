from __future__ import annotations

import asyncio
import json
import logging
import uuid
from typing import Any

from google.genai.types import Content

from pulse_ai.constants import (
    HTTP_TIMEOUT_GATEWAY,
    RCA_PIPELINE_TIMEOUT_SECONDS,
    USER_ID_SESSION_RCA,
)
from pulse_ai.schemas import RootCausePayloadSchema
from pulse_ai.schemas.session_rca_narrative_v1 import SessionRcaNarrativeV1
from pulse_ai.server.schemas import SessionRcaReportPayloadSchema, SessionRcaReportResponse

logger = logging.getLogger(__name__)


class SessionRcaRunnerError(Exception):
    def __init__(self, status_code: int, message: str) -> None:
        super().__init__(message)
        self.status_code = status_code
        self.message = message


def _build_session_rca_user_message(
    payload: RootCausePayloadSchema,
    date_str: str | None,
    as_of_iso: str | None,
) -> str:
    serialized = json.dumps(payload.model_dump(), ensure_ascii=True, default=str)
    lines = [
        f"Analysis anchor date (yyyy-MM-dd): {date_str or 'not provided'}",
        f"Analysis window end (ISO UTC, exclusive): {as_of_iso or 'not provided'}",
        "",
        "RootCausePayload (JSON):",
        serialized,
    ]
    return "\n".join(lines)


async def generate_session_rca_report(
    runner: Any,
    payload: RootCausePayloadSchema,
    date_str: str | None = None,
    as_of_iso: str | None = None,
) -> SessionRcaReportResponse:
    session_id = str(uuid.uuid4())
    user_text = _build_session_rca_user_message(payload, date_str, as_of_iso)
    message = Content.model_validate(
        {"role": "user", "parts": [{"text": user_text}]},
    )

    async def _run() -> None:
        async for _ in runner.run_async(
            user_id=USER_ID_SESSION_RCA,
            session_id=session_id,
            new_message=message,
        ):
            pass

    try:
        await asyncio.wait_for(_run(), timeout=RCA_PIPELINE_TIMEOUT_SECONDS)
    except TimeoutError as error:
        raise SessionRcaRunnerError(
            HTTP_TIMEOUT_GATEWAY,
            "Session RCA narrative generation timed out",
        ) from error
    except Exception as error:  # noqa: BLE001
        logger.exception("Session RCA pipeline execution failed")
        raise SessionRcaRunnerError(500, "Session RCA narrative generation failed") from error

    session = await runner.session_service.get_session(
        app_name=runner.app_name,
        user_id=USER_ID_SESSION_RCA,
        session_id=session_id,
    )

    narrative: SessionRcaNarrativeV1 | None = None
    if session:
        raw = session.state.get("session_rca_narrative")
        if raw:
            try:
                narrative = SessionRcaNarrativeV1.model_validate(raw)
            except Exception:
                logger.warning(
                    "Failed to validate session RCA narrative from session state",
                    exc_info=True,
                )

    if narrative is None:
        logger.error("Session RCA narrative missing, session_id=%s", session_id)
        raise SessionRcaRunnerError(500, "Session RCA narrative missing structured payload")

    report_payload = SessionRcaReportPayloadSchema(narrative=narrative)
    response = SessionRcaReportResponse(report=report_payload, cached=False)

    try:
        await runner.session_service.delete_session(
            app_name=runner.app_name,
            user_id=USER_ID_SESSION_RCA,
            session_id=session_id,
        )
    except Exception:
        logger.warning(
            "Failed to delete ephemeral session RCA session %s",
            session_id,
            exc_info=True,
        )

    return response

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
    USER_ID_FUNNEL_RCA,
)
from pulse_ai.output_guard import sanitize_pii
from pulse_ai.schemas import RootCausePayloadSchema
from pulse_ai.schemas.funnel_rca_structured_v1 import (
    FunnelRcaStructuredResponseV1,
    FunnelRcaStructuredV1,
)
from pulse_ai.server.schemas import FunnelRcaReportPayloadSchema, FunnelRcaReportResponse

logger = logging.getLogger(__name__)


class FunnelRcaRunnerError(Exception):
    def __init__(self, status_code: int, message: str) -> None:
        super().__init__(message)
        self.status_code = status_code
        self.message = message


def _build_funnel_rca_user_message(
    payload: RootCausePayloadSchema,
    funnel_id: int,
    focus_step_index: int,
    date_str: str | None,
    start_iso: str | None,
    end_iso: str | None,
) -> str:
    serialized = json.dumps(payload.model_dump(), ensure_ascii=True, default=str)
    lines = [
        f"Funnel id: {funnel_id}",
        f"Focus step index (dropped from): {focus_step_index}",
        f"Analysis anchor date: {date_str or 'not provided'}",
        f"Window start (ISO UTC): {start_iso or 'not provided'}",
        f"Window end (ISO UTC, exclusive): {end_iso or 'not provided'}",
        "",
        "RootCausePayload (JSON):",
        serialized,
    ]
    return "\n".join(lines)


async def generate_funnel_rca_report(
    runner: Any,
    payload: RootCausePayloadSchema,
    funnel_id: int,
    focus_step_index: int,
    date_str: str | None = None,
    start_iso: str | None = None,
    end_iso: str | None = None,
    example_sessions_by_label: dict[str, list[str]] | None = None,
) -> FunnelRcaReportResponse:
    session_id = str(uuid.uuid4())
    user_text = _build_funnel_rca_user_message(
        payload, funnel_id, focus_step_index, date_str, start_iso, end_iso
    )
    message = Content.model_validate(
        {"role": "user", "parts": [{"text": user_text}]},
    )

    async def _run() -> None:
        async for _ in runner.run_async(
            user_id=USER_ID_FUNNEL_RCA,
            session_id=session_id,
            new_message=message,
        ):
            pass

    try:
        await asyncio.wait_for(_run(), timeout=RCA_PIPELINE_TIMEOUT_SECONDS)
    except TimeoutError as error:
        raise FunnelRcaRunnerError(
            HTTP_TIMEOUT_GATEWAY,
            "Funnel RCA report generation timed out",
        ) from error
    except Exception as error:  # noqa: BLE001
        logger.exception("Funnel RCA pipeline execution failed")
        raise FunnelRcaRunnerError(500, "Funnel RCA report generation failed") from error

    session = await runner.session_service.get_session(
        app_name=runner.app_name,
        user_id=USER_ID_FUNNEL_RCA,
        session_id=session_id,
    )

    llm_structured: FunnelRcaStructuredV1 | None = None
    if session:
        raw = session.state.get("funnel_rca_structured")
        if raw:
            try:
                llm_structured = FunnelRcaStructuredV1.model_validate(raw)
            except Exception:
                logger.warning(
                    "Failed to validate funnel RCA structured report from session state",
                    exc_info=True,
                )

    if llm_structured is None:
        logger.error("Funnel RCA structured report missing, session_id=%s", session_id)
        raise FunnelRcaRunnerError(500, "Funnel RCA structured report missing")

    structured = FunnelRcaStructuredResponseV1.from_llm_output(llm_structured)

    if example_sessions_by_label:
        for seg in structured.segments:
            ids = example_sessions_by_label.get(seg.title)
            if ids:
                seg.affected_sessions = ids

    structured.executive_summary = sanitize_pii(structured.executive_summary)
    for seg in structured.segments:
        if seg.insights:
            seg.insights = sanitize_pii(seg.insights)
    structured.recommendations = [
        sanitize_pii(r) for r in structured.recommendations
    ]

    return FunnelRcaReportResponse(
        report=FunnelRcaReportPayloadSchema(structured=structured),
        cached=False,
    )

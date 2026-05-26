from __future__ import annotations

import asyncio
import json
import logging
import uuid
from typing import Any

from google.genai.types import Content
from pydantic import ValidationError

from pulse_ai.constants import (
    HTTP_TIMEOUT_GATEWAY,
    RCA_PIPELINE_TIMEOUT_SECONDS,
    USER_ID_SCREEN_RCA,
    USER_ID_SCREEN_RCA_V2,
)
from pulse_ai.schemas import RootCausePayloadSchema
from pulse_ai.schemas.screen_rca_narrative_v1 import ScreenRcaNarrativeV1
from pulse_ai.schemas.screen_rca_structured_v2 import ScreenRcaStructuredV2
from pulse_ai.server.schemas import (
    ScreenRcaReportPayloadSchema,
    ScreenRcaReportResponse,
    ScreenRcaV2ReportPayloadSchema,
    ScreenRcaV2ReportResponse,
)

logger = logging.getLogger(__name__)


class ScreenRcaRunnerError(Exception):
    def __init__(self, status_code: int, message: str) -> None:
        super().__init__(message)
        self.status_code = status_code
        self.message = message


def _build_screen_rca_user_message(
    screen_name: str,
    payload: RootCausePayloadSchema,
    start_iso: str | None,
    end_iso: str | None,
    date_str: str | None,
    as_of_iso: str | None,
) -> str:
    serialized = json.dumps(payload.model_dump(), ensure_ascii=True, default=str)
    lines = [
        f"Screen name: {screen_name}",
        f"Analysis window — start (ISO UTC): {start_iso or 'not provided'}",
        f"Analysis window — end (ISO UTC, exclusive): {end_iso or 'not provided'}",
        f"Legacy date (yyyy-MM-dd): {date_str or 'not provided'}",
        f"Legacy asOf (ISO): {as_of_iso or 'not provided'}",
        "",
        "RootCausePayload (JSON):",
        serialized,
    ]
    return "\n".join(lines)


def _build_screen_rca_v2_user_message(
    screen_name: str,
    problems: list[dict],
    evidences: dict,
    start_iso: str,
    end_iso: str,
) -> str:
    payload = {
        "problems": problems,
        "evidences": evidences,
    }
    serialized = json.dumps(payload, ensure_ascii=True, default=str)
    return "\n".join([
        f"Screen name: {screen_name}",
        f"Analysis window — start (ISO UTC): {start_iso}",
        f"Analysis window — end (ISO UTC, exclusive): {end_iso}",
        "",
        "Payload (pre-ranked problems + evidences):",
        serialized,
    ])


async def generate_screen_rca_report(
    runner: Any,
    payload: RootCausePayloadSchema,
    screen_name: str,
    start_iso: str | None = None,
    end_iso: str | None = None,
    date_str: str | None = None,
    as_of_iso: str | None = None,
) -> ScreenRcaReportResponse:
    """
    Runs the screen RCA narrative pipeline and returns typed response.

    Uses RCA_PIPELINE_TIMEOUT_SECONDS (same budget as interaction RCA).
    """
    session_id = str(uuid.uuid4())
    user_text = _build_screen_rca_user_message(
        screen_name,
        payload,
        start_iso,
        end_iso,
        date_str,
        as_of_iso,
    )
    message = Content.model_validate(
        {"role": "user", "parts": [{"text": user_text}]},
    )

    async def _run() -> None:
        async for _ in runner.run_async(
            user_id=USER_ID_SCREEN_RCA,
            session_id=session_id,
            new_message=message,
        ):
            pass

    try:
        await asyncio.wait_for(_run(), timeout=RCA_PIPELINE_TIMEOUT_SECONDS)
    except TimeoutError as error:
        raise ScreenRcaRunnerError(
            HTTP_TIMEOUT_GATEWAY,
            "Screen RCA narrative generation timed out",
        ) from error
    except Exception as error:  # noqa: BLE001
        logger.exception("Screen RCA pipeline execution failed")
        raise ScreenRcaRunnerError(500, "Screen RCA narrative generation failed") from error

    session = await runner.session_service.get_session(
        app_name=runner.app_name,
        user_id=USER_ID_SCREEN_RCA,
        session_id=session_id,
    )

    narrative: ScreenRcaNarrativeV1 | None = None
    if session:
        raw = session.state.get("screen_rca_narrative")
        if raw:
            try:
                narrative = ScreenRcaNarrativeV1.model_validate(raw)
            except Exception:
                logger.warning(
                    "Failed to validate screen RCA narrative from session state",
                    exc_info=True,
                )

    if narrative is None:
        logger.error("Screen RCA narrative missing, session_id=%s", session_id)
        raise ScreenRcaRunnerError(500, "Screen RCA narrative missing structured payload")

    report_payload = ScreenRcaReportPayloadSchema(narrative=narrative)
    response = ScreenRcaReportResponse(report=report_payload, cached=False)

    try:
        await runner.session_service.delete_session(
            app_name=runner.app_name,
            user_id=USER_ID_SCREEN_RCA,
            session_id=session_id,
        )
    except Exception:
        logger.warning(
            "Failed to delete ephemeral screen RCA session %s",
            session_id,
            exc_info=True,
        )

    return response


async def _delete_session(runner: Any, user_id: str, session_id: str) -> None:
    """Helper to safely delete ephemeral session."""
    try:
        await runner.session_service.delete_session(
            app_name=runner.app_name,
            user_id=user_id,
            session_id=session_id,
        )
    except Exception:
        logger.warning(
            "Failed to delete ephemeral screen RCA V2 session %s",
            session_id,
            exc_info=True,
        )


async def generate_screen_rca_report_v2(
    runner: Any,
    problems: list[dict],
    evidences: dict,
    screen_name: str,
    start_iso: str,
    end_iso: str,
) -> ScreenRcaV2ReportResponse:
    """
    Runs the screen RCA v2 agent (multi-problem + LLM summary) with retry.

    Uses RCA_PIPELINE_TIMEOUT_SECONDS for each attempt.
    """
    for attempt in range(2):
        session_id = str(uuid.uuid4())
        user_text = _build_screen_rca_v2_user_message(
            screen_name, problems, evidences, start_iso, end_iso
        )
        message = Content.model_validate({"role": "user", "parts": [{"text": user_text}]})

        async def _run() -> None:
            async for _ in runner.run_async(
                user_id=USER_ID_SCREEN_RCA_V2,
                session_id=session_id,
                new_message=message,
            ):
                pass

        try:
            await asyncio.wait_for(_run(), timeout=RCA_PIPELINE_TIMEOUT_SECONDS)
        except TimeoutError as e:
            raise ScreenRcaRunnerError(HTTP_TIMEOUT_GATEWAY, "Screen RCA V2 timed out") from e

        session = await runner.session_service.get_session(
            app_name=runner.app_name,
            user_id=USER_ID_SCREEN_RCA_V2,
            session_id=session_id,
        )
        if session is None:
            logger.warning(
                "Screen RCA V2 session missing after pipeline run, session_id=%s, attempt=%d",
                session_id,
                attempt,
            )
            if attempt == 1:
                raise ScreenRcaRunnerError(500, "Screen RCA V2 session missing after retry")
            continue

        raw = session.state.get("screen_rca_structured_v2")
        if raw is None:
            logger.warning(
                "Screen RCA V2 structured payload missing from session state, session_id=%s, attempt=%d",
                session_id,
                attempt,
            )
            await _delete_session(runner, USER_ID_SCREEN_RCA_V2, session_id)
            if attempt == 1:
                raise ScreenRcaRunnerError(
                    500,
                    "Screen RCA V2 structured payload missing after retry",
                )
            continue

        try:
            result = ScreenRcaStructuredV2.model_validate(raw)
            await _delete_session(runner, USER_ID_SCREEN_RCA_V2, session_id)
            return ScreenRcaV2ReportResponse(
                report=ScreenRcaV2ReportPayloadSchema(structured=result)
            )
        except ValidationError as error:
            logger.warning(
                "Screen RCA V2 schema validation failed, session_id=%s, attempt=%d: %s",
                session_id,
                attempt,
                error,
                exc_info=True,
            )
            await _delete_session(runner, USER_ID_SCREEN_RCA_V2, session_id)
            if attempt == 1:
                raise ScreenRcaRunnerError(
                    500,
                    "Screen RCA V2 schema validation failed after retry",
                ) from error
        except Exception as error:  # noqa: BLE001
            logger.exception(
                "Screen RCA V2 unexpected error validating structured payload, session_id=%s, attempt=%d",
                session_id,
                attempt,
            )
            await _delete_session(runner, USER_ID_SCREEN_RCA_V2, session_id)
            if attempt == 1:
                raise ScreenRcaRunnerError(
                    500,
                    "Screen RCA V2 structured payload validation failed after retry",
                ) from error

    raise ScreenRcaRunnerError(500, "Screen RCA V2 failed")

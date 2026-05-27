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
    USER_ID_SCREEN_RCA_V2,
)
from pulse_ai.schemas.screen_rca_structured_v2 import ScreenRcaStructuredV2
from pulse_ai.server.schemas import (
    ScreenRcaV2ReportPayloadSchema,
    ScreenRcaV2ReportResponse,
)

logger = logging.getLogger(__name__)


class ScreenRcaRunnerError(Exception):
    def __init__(self, status_code: int, message: str) -> None:
        super().__init__(message)
        self.status_code = status_code
        self.message = message


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
            # Override problems with original backend input — LLM must not lose any fields
            from pulse_ai.schemas.screen_rca_structured_v2 import ScreenRcaProblem
            result = result.model_copy(
                update={"problems": [ScreenRcaProblem.model_validate(p) for p in problems]}
            )
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

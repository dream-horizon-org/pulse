"""Runner logic for POST /insight/anr/day and POST /insight/anr/merge."""

from __future__ import annotations

import asyncio
import json
import logging
import uuid
from typing import Any

from google.genai.types import Content
from pydantic import ValidationError

from pulse_ai.constants import (
    ANR_INSIGHT_PIPELINE_TIMEOUT_SECONDS,
    USER_ID_ANR_INSIGHT,
)
from pulse_ai.schemas.anr_insight_v1 import AnrDayInsightV1, AnrInsightReportV1

logger = logging.getLogger(__name__)

MAX_RETRIES = 2


class AnrInsightRunnerError(Exception):
    def __init__(self, status_code: int, message: str) -> None:
        super().__init__(message)
        self.status_code = status_code
        self.message = message


# ---------------------------------------------------------------------------
# Day insight
# ---------------------------------------------------------------------------

def _build_day_prompt(entity_key: str, date: str, data: dict[str, Any]) -> str:
    return (
        f"Generate a daily ANR insight for the following snapshot.\n"
        f"entityKey: {entity_key}\n"
        f"date: {date}\n"
        f"data: {json.dumps(data, ensure_ascii=True)}"
    )


async def _run_single_day_attempt(
    runner: Any,
    session_id: str,
    message: Content,
) -> AnrDayInsightV1 | None:
    async def _run() -> None:
        async for _ in runner.run_async(
            user_id=USER_ID_ANR_INSIGHT,
            session_id=session_id,
            new_message=message,
        ):
            pass

    try:
        await asyncio.wait_for(_run(), timeout=ANR_INSIGHT_PIPELINE_TIMEOUT_SECONDS)
    except TimeoutError:
        logger.warning("ANR day attempt timed out session_id=%s", session_id)
        return None
    except Exception:
        logger.exception("ANR day attempt failed session_id=%s", session_id)
        return None

    session = await runner.session_service.get_session(
        app_name=runner.app_name,
        user_id=USER_ID_ANR_INSIGHT,
        session_id=session_id,
    )
    if not session:
        return None

    raw = session.state.get("anr_day_insight")
    if not raw:
        logger.warning("No anr_day_insight in session state session_id=%s", session_id)
        return None

    try:
        return AnrDayInsightV1.model_validate(raw)
    except ValidationError as exc:
        logger.warning(
            "AnrDayInsightV1 validation failed session_id=%s errors=%s",
            session_id,
            exc.errors(),
        )
        return None


async def generate_anr_day_insight(
    runner: Any,
    entity_key: str,
    date: str,
    data: dict[str, Any],
) -> AnrDayInsightV1:
    """Run the day insight pipeline with retries. Raises AnrInsightRunnerError on failure."""
    prompt = _build_day_prompt(entity_key, date, data)
    message = Content.model_validate({"role": "user", "parts": [{"text": prompt}]})

    result: AnrDayInsightV1 | None = None
    for attempt in range(MAX_RETRIES):
        session_id = str(uuid.uuid4())
        logger.debug("ANR day attempt %d/%d session_id=%s", attempt + 1, MAX_RETRIES, session_id)

        result = await _run_single_day_attempt(runner, session_id, message)

        try:
            await runner.session_service.delete_session(
                app_name=runner.app_name,
                user_id=USER_ID_ANR_INSIGHT,
                session_id=session_id,
            )
        except Exception:
            pass

        if result is not None:
            break
        if attempt < MAX_RETRIES - 1:
            logger.info("ANR day attempt %d failed, retrying...", attempt + 1)

    if result is None:
        raise AnrInsightRunnerError(500, "ANR day insight generation failed after retries")
    return result


# ---------------------------------------------------------------------------
# Merge insight
# ---------------------------------------------------------------------------

def _build_merge_prompt(
    entity_key: str,
    start_date: str,
    end_date: str,
    day_insights: list[Any],
) -> str:
    body = {
        "entityKey": entity_key,
        "startDate": start_date,
        "endDate": end_date,
        "dayInsights": day_insights,
    }
    return (
        "Generate the final ANR insight report by aggregating the daily insights below.\n"
        f"{json.dumps(body, ensure_ascii=True)}"
    )


async def _run_single_merge_attempt(
    runner: Any,
    session_id: str,
    message: Content,
) -> AnrInsightReportV1 | None:
    async def _run() -> None:
        async for _ in runner.run_async(
            user_id=USER_ID_ANR_INSIGHT,
            session_id=session_id,
            new_message=message,
        ):
            pass

    try:
        await asyncio.wait_for(_run(), timeout=ANR_INSIGHT_PIPELINE_TIMEOUT_SECONDS)
    except TimeoutError:
        logger.warning("ANR merge attempt timed out session_id=%s", session_id)
        return None
    except Exception:
        logger.exception("ANR merge attempt failed session_id=%s", session_id)
        return None

    session = await runner.session_service.get_session(
        app_name=runner.app_name,
        user_id=USER_ID_ANR_INSIGHT,
        session_id=session_id,
    )
    if not session:
        return None

    raw = session.state.get("anr_merge_report")
    if not raw:
        logger.warning("No anr_merge_report in session state session_id=%s", session_id)
        return None

    try:
        return AnrInsightReportV1.model_validate(raw)
    except ValidationError as exc:
        logger.warning(
            "AnrInsightReportV1 validation failed session_id=%s errors=%s",
            session_id,
            exc.errors(),
        )
        return None


async def generate_anr_merge_report(
    runner: Any,
    entity_key: str,
    start_date: str,
    end_date: str,
    day_insights: list[Any],
) -> AnrInsightReportV1:
    """Run the merge pipeline with retries. Raises AnrInsightRunnerError on failure."""
    prompt = _build_merge_prompt(entity_key, start_date, end_date, day_insights)
    message = Content.model_validate({"role": "user", "parts": [{"text": prompt}]})

    result: AnrInsightReportV1 | None = None
    for attempt in range(MAX_RETRIES):
        session_id = str(uuid.uuid4())
        logger.debug(
            "ANR merge attempt %d/%d session_id=%s", attempt + 1, MAX_RETRIES, session_id,
        )

        result = await _run_single_merge_attempt(runner, session_id, message)

        try:
            await runner.session_service.delete_session(
                app_name=runner.app_name,
                user_id=USER_ID_ANR_INSIGHT,
                session_id=session_id,
            )
        except Exception:
            pass

        if result is not None:
            break
        if attempt < MAX_RETRIES - 1:
            logger.info("ANR merge attempt %d failed, retrying...", attempt + 1)

    if result is None:
        raise AnrInsightRunnerError(500, "ANR merge report generation failed after retries")
    return result

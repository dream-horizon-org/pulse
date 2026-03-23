from __future__ import annotations

import asyncio
import json
import logging
import uuid
from typing import Any

from google.genai.types import Content

from pulse_ai.agents.rca.schemas import ChartConfig, TableConfig
from pulse_ai.constants import (
    HTTP_TIMEOUT_GATEWAY,
    RCA_ANALYZER_AGENT_NAME,
    RCA_PIPELINE_TIMEOUT_SECONDS,
    USER_ID_RCA
)
from pulse_ai.schemas import RootCausePayloadSchema
from pulse_ai.server.schemas import (
    ChartBlockSchema,
    ReportPayloadSchema,
    RcaReportResponse,
    TableBlockSchema,
)

logger = logging.getLogger(__name__)



class RcaRunnerError(Exception):
    def __init__(self, status_code: int, message: str) -> None:
        super().__init__(message)
        self.status_code = status_code
        self.message = message


def _build_rca_prompt(interaction_name: str, payload: RootCausePayloadSchema) -> str:
    serialized_payload = json.dumps(payload.model_dump(), ensure_ascii=True)
    return (
        "Generate a root cause analysis report for the given interaction.\n"
        f"Interaction: {interaction_name}\n"
        f"RootCausePayload(JSON): {serialized_payload}"
    )


def _extract_blocks_and_text(event_parts: Any) -> tuple[list[ChartBlockSchema], list[TableBlockSchema], str]:
    charts: list[ChartBlockSchema] = []
    tables: list[TableBlockSchema] = []
    text_output_parts: list[str] = []

    for part in event_parts:
        if part.text:
            text_output_parts.append(part.text)
        if part.function_response:
            response = part.function_response.response
            if isinstance(response, dict):
                chart_value = response.get("chart")
                table_value = response.get("table")
                if isinstance(chart_value, dict):
                    parsed_chart = ChartConfig.model_validate(chart_value)
                    charts.append(ChartBlockSchema(
                        type="chart",
                        title=parsed_chart.title,
                        data=parsed_chart.data,
                        description=parsed_chart.description,
                    ))
                if isinstance(table_value, dict):
                    parsed_table = TableConfig.model_validate(table_value)
                    tables.append(TableBlockSchema(
                        type="table",
                        title=parsed_table.title,
                        columns=parsed_table.columns,
                        rows=parsed_table.rows,
                        description=parsed_table.description,
                    ))

    return charts, tables, "".join(text_output_parts)


async def generate_rca_report(
    runner: Any,
    payload: RootCausePayloadSchema,
    interaction_name: str,
) -> RcaReportResponse:
    """
    Runs the RCA pipeline in one shot and returns typed report response.

    Timeout behavior:
    - Uses RCA_PIPELINE_TIMEOUT_SECONDS.
    - Raises RcaRunnerError(504) on timeout.
    """
    session_id = str(uuid.uuid4())
    prompt = _build_rca_prompt(interaction_name, payload)
    message = Content.model_validate(
        {"role": "user", "parts": [{"text": prompt}]},
    )

    charts: list[ChartBlockSchema] = []
    tables: list[TableBlockSchema] = []
    rca_insights: str | None = None
    markdown: str | None = None

    async def _run() -> None:
        nonlocal markdown, rca_insights
        async for event in runner.run_async(
            user_id=USER_ID_RCA,
            session_id=session_id,
            new_message=message,
        ):
            if not event.content or not event.content.parts:
                continue

            current_charts, current_tables, text_output = _extract_blocks_and_text(event.content.parts)
            if current_charts:
                charts.extend(current_charts)
            if current_tables:
                tables.extend(current_tables)
            if text_output:
                markdown = text_output
            if event.author == RCA_ANALYZER_AGENT_NAME and text_output:
                rca_insights = text_output

    try:
        await asyncio.wait_for(_run(), timeout=RCA_PIPELINE_TIMEOUT_SECONDS)
    except TimeoutError as error:
        raise RcaRunnerError(HTTP_TIMEOUT_GATEWAY, "RCA report generation timed out") from error
    except Exception as error:  # noqa: BLE001
        logger.exception("RCA pipeline execution failed")
        raise RcaRunnerError(500, "RCA report generation failed") from error

    report_payload = ReportPayloadSchema(
        markdown=markdown,
        charts=charts,
        tables=tables,
    )
    return RcaReportResponse(report=report_payload, rca_insights=rca_insights, cached=False)

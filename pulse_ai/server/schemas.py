from __future__ import annotations

from typing import Any, Literal

from pydantic import BaseModel, Field


class RcaReportRequest(BaseModel):
    interactionName: str
    date: str | None = None
    rootCausePayload: dict[str, Any] | None = None


class ChartBlockSchema(BaseModel):
    type: Literal["chart"] = "chart"
    title: str
    data: dict[str, Any]
    description: str | None = None


class TableBlockSchema(BaseModel):
    type: Literal["table"] = "table"
    title: str
    columns: list[dict[str, Any]]
    rows: list[dict[str, Any]]
    description: str | None = None


class ReportPayloadSchema(BaseModel):
    markdown: str | None = None
    charts: list[ChartBlockSchema] = Field(default_factory=list)
    tables: list[TableBlockSchema] = Field(default_factory=list)


class RcaReportResponse(BaseModel):
    report: ReportPayloadSchema
    rca_insights: str | None = None
    cached: bool = False

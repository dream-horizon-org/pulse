from __future__ import annotations

from typing import Any, Literal

from pydantic import BaseModel, Field


class RcaInsightsSchema(BaseModel):
    summary: str
    key_findings: list[str] = Field(default_factory=list)
    recommended_visualizations: str | None = None


class ChartConfig(BaseModel):
    type: Literal["line", "bar", "pie", "area"] = "line"
    title: str
    data: dict[str, Any]
    description: str | None = None


class TableConfig(BaseModel):
    title: str
    columns: list[dict[str, Any]]
    rows: list[dict[str, Any]]
    description: str | None = None

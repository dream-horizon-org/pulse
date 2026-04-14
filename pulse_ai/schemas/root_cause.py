from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


class RootCauseSegmentSchema(BaseModel):
    model_config = ConfigDict(populate_by_name=True)
    
    label: str
    dimensions: dict[str, str] | None = None
    metrics: dict[str, int | float | str]
    deltas: dict[str, float]
    exampleSessionIds: list[str] | None = Field(None, alias="exampleSessionIds")


class RootCausePayloadSchema(BaseModel):
    baseline: dict[str, int | float | str]
    segments: list[RootCauseSegmentSchema]
    mode: Literal["hierarchical", "flat"] | None = None
    cachedAt: str | None = None
    everythingGood: bool | None = None
    noDataAvailable: bool | None = None
    message: str | None = None

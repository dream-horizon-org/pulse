from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, ConfigDict, Field

# pulse-server uses Map<String, Object>; ClickHouse / JSON can emit nulls for missing metrics.
RootCauseScalar = int | float | str | None


class RootCauseSegmentSchema(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    label: str
    dimensions: dict[str, str | None] | None = None
    metrics: dict[str, RootCauseScalar]
    deltas: dict[str, float | None]
    exampleSessionIds: list[str] | None = Field(None, alias="exampleSessionIds")


class RootCausePayloadSchema(BaseModel):
    baseline: dict[str, RootCauseScalar]
    segments: list[RootCauseSegmentSchema]
    mode: Literal["hierarchical", "flat"] | None = None
    cachedAt: str | None = None
    everythingGood: bool | None = None
    noDataAvailable: bool | None = None
    message: str | None = None

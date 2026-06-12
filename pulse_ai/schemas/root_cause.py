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
    serverRank: int | None = Field(
        None,
        alias="serverRank",
        description=(
            "1-based Pulse priority after server merge/sort (lower = more important). "
            "Aligns with final segment list order: hybrid mode lists 2D+ segments before 1D flat."
        ),
    )


class RootCausePayloadSchema(BaseModel):
    baseline: dict[str, RootCauseScalar]
    segments: list[RootCauseSegmentSchema]
    mode: Literal["hierarchical", "flat", "hybrid"] | None = None
    cachedAt: str | None = None
    everythingGood: bool | None = None
    noDataAvailable: bool | None = None
    message: str | None = None

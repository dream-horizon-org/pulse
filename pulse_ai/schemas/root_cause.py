from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


class RcaHeatmapFiltersSchema(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    breakpoint: str | None = None
    platform: str | None = None
    app_version: str | None = Field(None, alias="app_version")
    geographical_region: str | None = Field(None, alias="geographical_region")
    from_date: str | None = Field(None, alias="from_date")
    to_date: str | None = Field(None, alias="to_date")


class RcaRelatedHeatmapsSchema(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    screens: list[str] | None = None
    heatmap_filters: RcaHeatmapFiltersSchema | None = Field(
        None,
        alias="heatmap_filters",
    )


class RootCauseSegmentSchema(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    label: str
    dimensions: dict[str, str] | None = None
    metrics: dict[str, int | float | str]
    deltas: dict[str, float]
    exampleSessionIds: list[str] | None = Field(None, alias="exampleSessionIds")
    related_heatmaps: RcaRelatedHeatmapsSchema | None = Field(
        None,
        alias="related_heatmaps",
    )


class RootCausePayloadSchema(BaseModel):
    baseline: dict[str, int | float | str]
    segments: list[RootCauseSegmentSchema]
    mode: Literal["hierarchical", "flat"] | None = None
    cachedAt: str | None = None
    everythingGood: bool | None = None
    noDataAvailable: bool | None = None
    message: str | None = None

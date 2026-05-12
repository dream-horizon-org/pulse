# domains / heatmap

Brief: [`/docs/components/backend-server.md`](../../../components/backend-server.md) ·
Index: [`../index.md`](../index.md) ·
Peers: [interaction](interaction.md), [screen](screen.md)

## Purpose

Serve click/interaction heatmap data + screenshot URLs for a given screen.

## Source

- `resources/heatmap/HeatmapController.java` (`@Path("/v1/heatmap")`)
- `resources/heatmap/models/`
- `service/heatmap/HeatmapService.java`, `HeatmapServiceImpl.java`
- `service/heatmap/HeatmapScreenshotUrlResolver.java`
- `dao/heatmap/HeatmapQueries.java`
- `module/HeatmapModule.java`

## Public surface

| Method | Path |
|---|---|
| GET | `/v1/heatmap/data` |

Query params: project, screen name, time range, platform, app version.

## Internal design

- Reads `interaction_heatmaps_daily` from ClickHouse (pre-aggregated daily by
  ingestion).
- Screenshot URLs resolved via `HeatmapScreenshotUrlResolver` against
  CloudFront/S3 (clients from `MainModule`).

## Dependencies

ClickHouse `interaction_heatmaps_daily`; S3/CloudFront for screenshots;
[interaction](interaction.md) for click semantics.

## Data contracts

ClickHouse columns: `ProjectId`, `Platform`, `ScreenName`, `Date`,
`ElementId`, `Count`, `XBucket`, `YBucket`.

## Tests

`src/test/java/.../resources/heatmap/*`, `.../service/heatmap/*`.

## Rebuild recipe

1. `HeatmapController` GET `/data`.
2. `HeatmapServiceImpl` builds ClickHouse query (project, time, screen).
3. Resolver returns signed CloudFront URLs.
4. `HeatmapModule` installed in `MainModule`.

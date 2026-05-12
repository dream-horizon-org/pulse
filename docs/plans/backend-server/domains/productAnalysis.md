# domains / productAnalysis

Brief: [`/docs/components/backend-server.md`](../../../components/backend-server.md) ·
Index: [`../index.md`](../index.md) ·
Peers: [analytics](analytics.md), [eventdefinition](eventdefinition.md)

## Purpose

Funnel + journey product-analysis APIs (UI-facing) plus funnel-events
catalog.

## Source

- `resources/productAnalysis/FunnelEventsController.java`
  (`@Path("/v1/funnels")` — events, filters, filter values)
- `resources/productAnalysis/funnel/` — funnel CRUD + results controller(s)
- `resources/productAnalysis/journey/` — journey CRUD + results
- `resources/productAnalysis/models/`
- `service/productAnalysis/AnalysisEntityTags.java`
- `service/productAnalysis/funnel/FunnelService.java`, `impl/`,
  `FunnelResultsMapper.java`
- `service/productAnalysis/journey/JourneyService.java`, `impl/`,
  `JourneyResultsMapper.java`
- `service/productAnalysis/eventcatalog/`
- `dao/productAnalysis/`:
  - `funneldefinition/`, `funnelresults/`, `funneljourneytag/`
  - `journey/`, `journeyresults/`
  - `eventcatalog/`

## Public surface (FunnelEventsController)

| Method | Path |
|---|---|
| GET | `/v1/funnels/events` |
| GET | `/v1/funnels/filters` |
| GET | `/v1/funnels/filters/{filterKey}/values` |

Funnel + journey CRUD live in `funnel/` and `journey/` subpackages. Compute
is triggered by [analytics](analytics.md) `/internal/analytics/*` endpoints.

## Internal design

- Definitions in MySQL (`funneldefinition`, `journey` DAOs).
- Results materialized into MySQL (`funnelresults`, `journeyresults`) by the
  analytics batch (ClickHouse or Spark/EMR backend).
- Tags via `funneljourneytag` DAO and `AnalysisEntityTags`.
- Event catalog (`eventcatalog`) populates the filter dropdowns.

## Dependencies

[analytics](analytics.md) (compute), [eventdefinition](eventdefinition.md)
(events list), ClickHouse, MySQL.

## Data contracts

Errors: `FUNNEL_NOT_FOUND` (BE1010/404), `FUNNEL_CREATION_FAILED`
(BE1011/400), `JOURNEY_NOT_FOUND` (BE1012/404),
`JOURNEY_CREATION_FAILED` (BE1013/400).

MySQL: `funnel_definitions`, `funnel_results`, `journeys`,
`journey_results`, `funnel_journey_tags`, `event_catalog`.

## Tests

`src/test/java/.../resources/productAnalysis/*`,
`.../service/funnel/*`, `.../service/journey/*`,
`.../service/productAnalysis/*`.

## Rebuild recipe

1. Definition CRUD controllers (funnel + journey subpackages).
2. `FunnelEventsController` for catalog/filter endpoints.
3. Services in `funnel/`, `journey/` with `*ResultsMapper`.
4. DAOs split by concern (definition vs results vs tags).
5. Wire compute trigger to [analytics](analytics.md)
   `/internal/analytics/funnels|journeys`.

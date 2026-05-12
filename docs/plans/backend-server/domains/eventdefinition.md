# domains / eventdefinition

Brief: [`/docs/components/backend-server.md`](../../../components/backend-server.md) ·
Index: [`../index.md`](../index.md) ·
Peers: [productAnalysis](productAnalysis.md)

## Purpose

Catalog of event definitions (name, category, schema) + search across observed
events.

## Source

- `resources/eventdefinition/EventDefinitionController.java`
  (`@Path("/v1/event-definitions")`)
- `resources/eventdefinition/EventSearchController.java`
  (`@Path("/v1/events")`)
- `resources/eventdefinition/EventDefinitionMapper.java`
- `resources/eventdefinition/models/`
- `service/eventdefinition/EventDefinitionService.java`
- `service/eventdefinition/impl/EventDefinitionServiceImpl.java`
- `service/eventdefinition/models/`
- `dao/eventdefinition/EventDefinitionDao.java`,
  `EventDefinitionQueries.java`
- `error/EventDefinitionNotFoundException.java`
- `module/EventDefinitionModule.java`

## Public surface

| Method | Path |
|---|---|
| GET | `/v1/event-definitions` |
| GET | `/v1/event-definitions/categories` |
| GET | `/v1/event-definitions/{id}` |
| POST | `/v1/event-definitions` |
| PUT | `/v1/event-definitions/{id}` |
| DELETE | `/v1/event-definitions/{id}` |
| POST | `/v1/event-definitions/bulk` |
| GET | `/v1/events` |

## Internal design

- CRUD controller for definitions; `EventSearchController` aggregates observed
  events from ClickHouse for autocomplete.
- `EventDefinitionNotFoundException` mapped via standard error handler.
- Module installed by `MainModule` for service binding.

## Dependencies

MySQL `event_definitions` (and `event_categories`); ClickHouse for search
counts.

## Data contracts

MySQL columns (per `EventDefinitionQueries`): id, project_id, name,
category, schema_json, created_at, updated_at.

## Tests

`src/test/java/.../resources/eventdefinition/*`,
`.../service/eventdefinition/*`.

## Rebuild recipe

1. Controller with REST CRUD + `/categories` + `/bulk`.
2. `EventDefinitionService` interface + impl in `impl/`.
3. `EventDefinitionDao` + `EventDefinitionQueries`.
4. Register module in `MainModule.install(new EventDefinitionModule())`.

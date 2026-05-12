# domains / session

Brief: [`/docs/components/backend-server.md`](../../../components/backend-server.md) ·
Index: [`../index.md`](../index.md) ·
Peers: [breadcrumb](breadcrumb.md), [interaction](interaction.md)

## Purpose

Session listing, detail, replay (snapshots), and session ingest from native
SDK.

## Source

- `resources/session/SessionListingResource.java` (`@Path("/v1/sessions")`,
  POST `/listing`, GET `/filters`)
- `resources/session/SessionDetailResource.java`
  (`@Path("/v1/sessions")`, GET `/{sessionId}`)
- `resources/session/SessionReplay.java`
  (`@Path("/v1/sessions")`, GET `/{sessionId}/snapshots-source`,
  GET `/{sessionId}/snapshots-data`)
- `resources/session/Sessions.java` (`@Path("/api/v1")`, POST `/sessions`)
- `resources/session/SessionReplayMapper.java`
- `resources/session/models/`
- `service/session/SessionService.java`,
  `SessionListingService.java`, `SessionDetailService.java`,
  `SessionReplayService.java`, `SessionBlockFetcher.java`,
  `FilterConfigService.java`, `models/`
- `dao/session/` (listing query builder + filter spec)
- `dao/sessiondetail/SessionDetailDao.java`, `SessionDetailQueries.java`,
  `models/`
- `dao/sessionreplay/SessionReplayDao.java`, `models/`, `query/`

## Public surface

| Method | Path |
|---|---|
| POST | `/v1/sessions/listing` |
| GET | `/v1/sessions/filters` |
| GET | `/v1/sessions/{sessionId}` |
| GET | `/v1/sessions/{sessionId}/snapshots-source` |
| GET | `/v1/sessions/{sessionId}/snapshots-data` |
| POST | `/api/v1/sessions` |

## Internal design

- Listing uses `dao/session/SessionListingQueryBuilder` driven by typed
  fields/operators/quick-filters/sort enums.
- Cursor pagination via `SessionListingCursorCodec`.
- Detail queries ClickHouse for the timeline; replay reads snapshot blocks
  fetched by `SessionBlockFetcher` (S3-backed).
- `Sessions.java` (`/api/v1/sessions`) is the SDK ingest path.

## Dependencies

ClickHouse (`otel_traces`, `otel_logs`, session tables), S3 (replay
snapshots), MySQL (filter configs). [breadcrumb](breadcrumb.md) consumes
the same `SessionId`.

## Data contracts

ClickHouse session-attribute columns include `SessionId`, `Platform`,
`AppVersion`. Replay snapshots are stored as S3 objects keyed by
`projectId/sessionId/blockN`.

## Tests

`src/test/java/.../resources/session/*`, `.../service/session/*`.

## Rebuild recipe

1. Four resource classes per the table above.
2. `SessionListingQueryBuilder` + filter enums + cursor codec.
3. `SessionReplayService` + `SessionBlockFetcher` (S3).
4. Ingest endpoint persists session header in ClickHouse via the write
   client.

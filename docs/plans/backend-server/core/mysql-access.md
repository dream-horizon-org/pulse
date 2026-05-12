# core / mysql-access

Brief: [`/docs/components/backend-server.md`](../../../components/backend-server.md) ·
Index: [`../index.md`](../index.md)

## Purpose

MySQL connectivity for tenant/project state.

## Source

- `.../client/mysql/MysqlClient.java` (interface)
- `.../client/mysql/MysqlClientImpl.java` (Vert.x mysql-client pool)
- All `.../dao/<domain>/` and root-level DAOs
  (`AlertsDao.java`, `HealthCheckDao.java`).

## Public surface

`MysqlClient`:

- `Single<RowSet<Row>> preparedQuery(String sql, Tuple params)`
- `Completable execute(String sql, Tuple params)`
- transaction helpers via `Vertx` pool.

Each DAO injects `MysqlClient` and exposes RxJava3
`Single<T>`/`Maybe<T>`/`Completable` methods.

## Internal design

- Config `mysql-default.conf` → `MysqlClientImpl` (host, port, db, pool size).
- DAO pattern:
  - `XDao.java` — methods returning RxJava types.
  - `XQueries.java` — `static final UPPER_SNAKE_CASE` SQL constants.
  - `models/` — row-mapping POJOs (Lombok `@Data`).
- Rows mapped manually; no ORM.

## Gotchas

- Never inline SQL in DAOs — keep in `Queries.java`.
- Use prepared statements (`Tuple`) — never string-concat user input.
- Pool size from HOCON; defaults in `mysql-default.conf`.

## Dependencies

`io.vertx.mysqlclient`, RxJava3, Lombok.

## Data contracts

Schema lives in `backend/server/src/main/resources/db/` (Flyway / SQL).
Tables per domain are listed in their respective handbook entries.

## Tests

`src/test/java/.../dao/*` per domain.

## Rebuild recipe

1. Add `MysqlClient` interface + `MysqlClientImpl` wrapping Vert.x mysql-client.
2. For each domain create `XDao` + `XQueries` + `models/`.
3. Inject `MysqlClient` via constructor.
4. Return RxJava3 types and let services compose.

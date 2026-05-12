# backend-server

## What

Pulse REST API. Serves the dashboard (`pulse-ui`), MCP server, and AI agent. Owns
tenant/project/auth state in MySQL and proxies analytics queries to ClickHouse
plus Athena/EMR Serverless/Spark for heavier jobs.

## Path + stack

- Path: `backend/server/`
- Lang: Java 17
- Frameworks: Vert.x 4.5 (RxJava3 bindings), Guice, MapStruct, Jakarta REST
  (`com.dream11.rest`), Lombok, JOOQ-style hand-written SQL constants.
- Build: Maven (`pom.xml`).
- Port: `8080`.

## Build / dev

```bash
cd backend/server
mvn clean install                         # build + unit tests
mvn verify                                # + checkstyle + JaCoCo (35% / 80% on changed)
mvn -Dtest=AuthServiceTest test            # single class
mvn -Dtest=AuthServiceTest#shouldLoginUser test
```

Run inside the stack via `deploy/scripts/start.sh`; logs via
`deploy/scripts/logs.sh server`.

## Inputs / outputs

| Direction | Peer | Transport |
|---|---|---|
| Inbound | `pulse-ui`, `pulse-mcp`, `pulse-ai` | HTTP/JSON, JWT or API key |
| Inbound webhooks | Slack interactive, SES bounces | HTTPS |
| State | MySQL (`pulse` schema) | mysql-client (Vert.x) |
| Read analytics | ClickHouse (per-project credentials) | HTTP/JDBC via `chclient` |
| Read analytics | AWS Athena (S3-Parquet custom events) | AWS SDK |
| Compute | EMR Serverless, Spark, RCA jobs | AWS SDK |
| Outbound | Slack, Email (SES), webhooks | HTTPS + SQS queue |
| Authz | OpenFGA | gRPC |

## Key files

| File | Role |
|---|---|
| `backend/server/src/main/java/org/dreamhorizon/pulseserver/MainApplication.java` | Boot entrypoint |
| `.../MainModule.java` | Top-level Guice wiring |
| `.../verticle/MainVerticle.java` | Reads config, creates clients, deploys REST verticle |
| `.../verticle/RestVerticle.java` | Mounts Jakarta resources + filters |
| `.../verticle/VertxAuthChain.java` | JWT / API key auth filter chain |
| `.../error/ServiceError.java` | Central error enum (BE10xx codes) |
| `.../resources/` | All HTTP controllers (one folder per domain) |
| `.../service/` | Domain services (interface + impl) |
| `.../dao/` | DAOs + `Queries.java` SQL constants |
| `.../module/` | Per-domain Guice modules |
| `src/main/resources/conf/*.conf` | HOCON config per subsystem |

## Owners / runbook

Owners: backend team (placeholder). Runbook entries TBD; check
`deploy/scripts/logs.sh server` and `/health` endpoint for liveness.

## Handbook

Detailed sub-component handbook: [`/docs/plans/backend-server/index.md`](../plans/backend-server/index.md).

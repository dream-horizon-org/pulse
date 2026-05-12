# core / verticles

Brief: [`/docs/components/backend-server.md`](../../../components/backend-server.md) ·
Index: [`../index.md`](../index.md)

## Purpose

Vert.x process lifecycle: read config, create shared clients, deploy REST and
background verticles.

## Source

- `backend/server/src/main/java/org/dreamhorizon/pulseserver/MainApplication.java`
- `.../verticle/MainVerticle.java` (~537 lines)
- `.../verticle/RestVerticle.java` (~94 lines)
- `.../verticle/AnrCrashLogConsumerVerticle.java`
- `.../verticle/AiSseProxyHandler.java`
- `.../verticle/VertxAuthChain.java`

## Public surface

| Verticle | Listens / consumes |
|---|---|
| `MainVerticle` | Boot only; deploys others |
| `RestVerticle` | HTTP `:8080`, mounts Jakarta resources from `resources/` |
| `AnrCrashLogConsumerVerticle` | Internal worker for ANR crash logs |
| `AiSseProxyHandler` | `/v1/ai/*` SSE proxy to `pulse-ai` (8000) |

## Internal design

- `MainVerticle.rxStart()` pulls config via `ConfigUtils.getConfigRetriever(vertx)`.
  Sections: `app`, `mysql`, `webclient`, `clickhouse`, `athena`, `emrServerless`,
  `spark`, `notification`, `rootcause`, `openfga`, `analyticsEngine`.
- Shared state stored via `vertx.SharedDataUtils.put(...)` so worker verticles
  can fetch typed config (`ApplicationConfig`, `ClickhouseConfig`, etc.).
- Constructs `MysqlClientImpl`, two `WebClient`s (general + AI SSE with long
  idle), `AiStreamingHttpClient`, then bootstraps `GuiceInjector`.
- Deploys `RestVerticle` per CPU core (`CpuCoreSensor`).
- `RestVerticle` mounts every controller annotated with Jakarta `@Path` from
  the `resources/` package via `com.dream11.rest`.
- `VertxAuthChain` runs before resource handlers (see `core/auth.md`).

## Gotchas

- AI proxy uses its own `WebClient` with extended timeouts; do not reuse the
  general one.
- Config defaults live in `src/main/resources/conf/*-default.conf`; env overlays
  apply on top.
- `StartupConfigValidator` fails fast if required config is missing.

## Dependencies

`config/*`, `client/mysql`, `client/chclient`, `guice/GuiceInjector`,
`module/*`, all `resources/*`.

## Data contracts

- HOCON keys above; SharedData typed by class.

## Tests

`src/test/java/.../verticle/` (smoke + auth chain tests).

## Rebuild recipe

1. Implement `MainApplication` with `Vertx.rxClusteredVertx`-style boot.
2. Add `MainVerticle extends AbstractVerticle` (rxjava3); load HOCON,
   instantiate clients, `SharedDataUtils.put` each typed config.
3. Build Guice injector with `MainModule` + `VertxAbstractModule(vertx)`.
4. `vertx.deployVerticle(RestVerticle::new, options)` per core.
5. Deploy worker verticles (ANR consumer, SSE proxy handler).

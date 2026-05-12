# core / guice-modules

Brief: [`/docs/components/backend-server.md`](../../../components/backend-server.md) ·
Index: [`../index.md`](../index.md)

## Purpose

Dependency-injection wiring for clients, services, DAOs, and Jakarta resources.

## Source

- `.../MainModule.java` (top-level binder)
- `.../guice/GuiceInjector.java` (injector factory)
- `.../guice/OpenFgaServiceProvider.java`
- `.../module/`
  - `ConfigModule.java`
  - `EventDefinitionModule.java`
  - `HeatmapModule.java`
  - `InteractionModule.java`
  - `QueryEngineModule.java`
  - `RcaModule.java`
  - `UploadInteractionDetailModule.java`
  - `ValidationModule.java`
  - `VertxAbstractModule.java`

## Public surface

`GuiceInjector.create(vertx, mysqlClient, webClient, aiClient, ...)` returns an
`Injector` with every controller bound via `Multibinder<Object>` so `com.dream11.rest`
can mount them.

## Internal design

- `MainModule` extends `AbstractModule`:
  - Binds singletons: `MysqlClient`, `WebClient`, `ObjectMapper`,
    `OpenFgaService` (via `@Provides` with `OpenFgaServiceProvider`),
    `ClickhouseProjectConnectionPoolManager`, S3/CloudFront clients,
    `EmrServerlessJobClient`.
  - Binds service interfaces to impls (e.g.
    `IncidentService` → `IncidentServiceImpl`).
  - Installs per-domain modules listed above.
  - Registers controllers via `Multibinder.newSetBinder(binder(), Object.class, Names.named("rest"))`.
- `VertxAbstractModule(vertx)` exposes the `Vertx` instance.
- `ConfigModule` exposes typed `*Config` POJOs pulled from `SharedDataUtils`.

## Gotchas

- Adding a new resource requires registering it in the `Multibinder` block in
  `MainModule` (or its delegated module).
- Per-domain modules exist where Guice binding gets non-trivial (notification
  providers, RCA pipeline, query engine).
- Avoid field injection in tests; prefer constructor injection (matches
  `@RequiredArgsConstructor(onConstructor = @__({@Inject}))`).

## Dependencies

Lombok, Guice `multibindings`, Vert.x, `com.dream11.rest`.

## Data contracts

n/a.

## Tests

`src/test/java/.../service/*` uses Mockito; Guice graph not booted in unit
tests.

## Rebuild recipe

1. Create `MainModule extends AbstractModule`.
2. Add `@Provides @Singleton` methods for each shared client.
3. `bind(IFoo.class).to(FooImpl.class)` for every service.
4. `Multibinder<Object>` (named `"rest"`) addBinding each controller class.
5. Per-domain installs (`install(new XModule())`) for heavy graphs.

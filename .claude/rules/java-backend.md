---
paths:
  - "backend/**/*.java"
---

# Java Backend Conventions

## Package Structure

```
org.dreamhorizon.pulseserver/
├── resources/          # REST controllers grouped by domain
│   └── <domain>/
│       ├── <Domain>Controller.java
│       ├── Rest<Domain>Mapper.java      # MapStruct (REST ↔ service)
│       └── models/                      # REST request/response DTOs
├── service/            # Business logic grouped by domain
│   └── <domain>/
│       ├── <Domain>Service.java         # Interface
│       └── impl/<Domain>ServiceImpl.java
├── dao/                # Data access grouped by domain
│   └── <domain>/
│       ├── <Domain>Dao.java
│       ├── Queries.java                 # SQL as static constants
│       └── models/                      # Row/DB models
├── error/              # ServiceError enum (codes: BE1001 format)
├── module/             # Guice modules (*Module.java)
└── verticle/           # MainVerticle, RestVerticle
```

## Service Layer

- Define interface + impl; return RxJava3 `Single<T>`, `Maybe<T>`, or `Completable` — never block
- Inject via Guice: `@RequiredArgsConstructor(onConstructor = @__({@Inject}))`
- Annotate with `@Slf4j` for logging

## DAO Layer

- SQL strings in `Queries.java` as `static final UPPER_SNAKE_CASE` constants
- Use `MysqlClient` or `ClickhouseQueryService`

## DTOs

- Lombok `@Data` on all DTOs
- `@JsonIgnoreProperties(ignoreUnknown = true)` on response DTOs
- MapStruct mappers with `INSTANCE = Mappers.getMapper(...)` pattern

## Error Handling

- Use `ServiceError` enum with codes like `BE1001`
- Throw via `ServiceError.X.getException()` or `getCustomException(message, cause)`
- Response shape: `Response<T>` with `data` and `Error.of(code, message)`

## Testing

- JUnit 5 + Mockito + AssertJ
- Method naming: `should*` (e.g., `shouldThrowExceptionIfInteractionAlreadyPresent`)
- Group with `@Nested` classes
- JaCoCo: **35% overall**, **80% on changed files**

## Code Style

- Google Checkstyle: 140-char lines, 2-space indent
- No wildcard imports
- Constants: `UPPER_SNAKE_CASE`

---
name: add-api-endpoint
description: Scaffold a complete new REST API endpoint in the Java backend following Pulse conventions. Usage: /add-api-endpoint <domain> <HTTP method> <path>
---

Scaffold a new endpoint for domain `$ARGUMENTS` following these steps in order:

1. **DTOs** — Create request/response DTOs in `backend/server/src/main/java/org/dreamhorizon/pulseserver/resources/<domain>/models/`
   - Lombok `@Data`, `@JsonIgnoreProperties(ignoreUnknown = true)` on response DTOs

2. **Service Interface** — Create `<Domain>Service.java` in `service/<domain>/`
   - Return RxJava3 types (`Single<T>`, `Maybe<T>`, `Completable`)

3. **Service Implementation** — Create `<Domain>ServiceImpl.java` in `service/<domain>/impl/`
   - `@Slf4j`, `@RequiredArgsConstructor(onConstructor = @__({@Inject}))`

4. **SQL** — Add query constants to `dao/<domain>/Queries.java`

5. **DAO** — Create `<Domain>Dao.java` using `MysqlClient` or `ClickhouseQueryService`

6. **MapStruct Mapper** — `Rest<Domain>Mapper.java` with `INSTANCE` pattern

7. **Controller** — Create `<Domain>Controller.java` in `resources/<domain>/`
   - JAX-RS annotations, `@RequiresPermission`, `RestResponse.jaxrsRestHandler()`

8. **ServiceError** — Add any new error codes to `error/ServiceError.java` (format: `BE####`)

9. **Guice Module** — Bind new service/DAO in a `*Module.java`, register in `MainModule`

10. **Tests** — Write `<Domain>ServiceImplTest.java` with JUnit 5 + Mockito + AssertJ

After scaffolding, run `cd backend/server && mvn verify` to confirm it compiles and tests pass.

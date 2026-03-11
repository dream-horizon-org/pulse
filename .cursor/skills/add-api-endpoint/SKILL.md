---
name: add-api-endpoint
description: Step-by-step workflow for adding a new REST API endpoint to pulse-server. Use when creating a new backend endpoint, route, resource, or API in backend/server/.
---

# Add API Endpoint

## Workflow

Copy this checklist and track progress:

```
- [ ] Step 1: Create/update DTO
- [ ] Step 2: Create/update Service interface
- [ ] Step 3: Create/update Service implementation
- [ ] Step 4: Create/update DAO + Queries
- [ ] Step 5: Create MapStruct mapper
- [ ] Step 6: Create REST resource (controller)
- [ ] Step 7: Add ServiceError codes
- [ ] Step 8: Register in Guice module
- [ ] Step 9: Write unit tests
- [ ] Step 10: Verify build
```

## Step 1: Create/Update DTO

Location: `backend/server/src/main/java/org/dreamhorizon/pulseserver/dto/`

```java
@Data
public class MyRequest {
    private String name;
    private String description;
}

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MyResponse {
    private String id;
    private String name;
}
```

## Step 2: Service Interface

Location: `backend/server/src/main/java/org/dreamhorizon/pulseserver/service/<domain>/`

```java
public interface MyService {
    Single<MyResponse> create(MyRequest request);
    Single<List<MyResponse>> list();
}
```

## Step 3: Service Implementation

Location: `service/<domain>/impl/`

```java
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class MyServiceImpl implements MyService {
    private final MyDao myDao;
    // Implementation using RxJava
}
```

## Step 4: DAO + Queries

```java
public class MyQueries {
    public static final String INSERT = "INSERT INTO ...";
    public static final String SELECT_ALL = "SELECT ...";
}
```

## Step 5: MapStruct Mapper

```java
@Mapper
public interface MyMapper {
    MyMapper INSTANCE = Mappers.getMapper(MyMapper.class);
    MyResponse toResponse(MyModel model);
}
```

## Step 6: REST Resource

Location: `resources/`

```java
@Path("/v1/my-resource")
public class MyResource {
    @Inject private MyService service;

    @GET
    public void list(RoutingContext ctx) {
        RestResponse.jaxrsRestHandler(ctx, service.list());
    }
}
```

## Step 7: ServiceError Codes

Add to `ServiceError.java` enum if new failure cases exist.

## Step 8: Guice Binding

Add `bind(MyService.class).to(MyServiceImpl.class)` in the appropriate module.

## Step 9: Tests

JUnit 5 + Mockito + AssertJ, `@Nested` grouping, `should*` naming.

## Step 10: Verify

```bash
cd backend/server && mvn clean install
```

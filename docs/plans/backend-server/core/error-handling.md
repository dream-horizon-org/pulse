# core / error-handling

Brief: [`/docs/components/backend-server.md`](../../../components/backend-server.md) ·
Index: [`../index.md`](../index.md)

## Purpose

Central error enum + JSON shape consumed by all controllers.

## Source

- `.../error/ServiceError.java` (~138 lines)
- `.../error/EventDefinitionNotFoundException.java`
- Framework: `com.dream11.rest.exception.RestError` + `ExceptionResponseEntity`.

## Public surface

`ServiceError` implements `RestError`. Each entry: `errorCode`, `errorMessage`,
`httpStatusCode`. Throw via `ServiceError.X.getException()` which yields a
`WebApplicationException` consumed by the framework. Also exposes
`toJson()` for native (non-Jakarta) Vert.x handlers (e.g. AI SSE proxy).

## Codes (subset)

| Code | HTTP | Meaning |
|---|---|---|
| `BE1001` | 400 | Invalid JSON / body |
| `BE1002` | 400 | Missing body params |
| `BE1003` | 400 | Invalid request params |
| `BE1004` | 400 | Missing query params |
| `BE1005` | 400 | Missing header params |
| `BE1006` | 400 | Missing path params |
| `BE1007` | 500 | Generic internal |
| `BE1009` | 400 | Unsupported characters |
| `BE1010` | 404 | Funnel not found (or 503 AI not configured — overloaded) |
| `BE1011` | 400 | Funnel creation failed (or 502 AI proxy bad gateway) |
| `BE1012` | 404 | Journey not found |
| `BE1013` | 400 | Journey creation failed |
| `401` | 401 | Unauthorised |
| `403` | 403 | Forbidden |
| `404` | 404 | Not found |
| `409` | 409 | Duplicate channel / member / suggested interaction |
| `500` | 500 | Database / dataflow / cron errors |

Note: a few codes (BE1010, BE1011) are reused for AI proxy semantics — be
careful when adding new ones.

## Internal design

- All controllers return `CompletionStage<Response<T>>` where `Response` is
  `com.dream11.rest.Response`. Errors wrap as `Error.of(code, message)`.
- Services raise `ServiceError.X.getException()` from RxJava chains; the
  framework maps to `ExceptionResponseEntity` JSON.
- Native handlers (SSE proxy) use `ServiceError.X.toJson()` so client parsers
  see identical shape.

## Gotchas

- Do not introduce ad-hoc `RuntimeException` from controllers — always funnel
  through `ServiceError`.
- New codes go at the bottom of the enum to keep diffs stable.

## Dependencies

`com.dream11.rest`.

## Tests

`src/test/java/.../error/` — enum shape; per-service tests assert thrown codes.

## Rebuild recipe

1. Add `ServiceError` enum implementing `RestError`.
2. Provide `getException()` returning `WebApplicationException` with
   `Response.status(httpStatusCode).entity(...)`.
3. Add `toJson()` for native handlers.
4. Plumb framework `ExceptionResponseEntity` into `RestVerticle`.

# API client

Every backend call goes through one of two functions in
`pulse-ui/src/helpers/makeRequest/`:

- `makeRequest<D>(config)` - JSON request/response.
- `streamAiRunSseWithAuth(init)` - SSE stream for AI runs.

Both wrap the call in `withTimeout` and add a 401-refresh-retry path.

## Base URL

`REACT_APP_PULSE_SERVER_URL` -> exported as `API_BASE_URL` from
`src/constants/Constants.ts`. Defaults to the backend `:8080`.

## Route table

`API_ROUTES` (in `src/constants/Constants.ts`) maps a key to:

```ts
{
  key: string;
  apiPath: string | ((...args) => string);
  method: "GET" | "POST" | "PUT" | "DELETE";
}
```

Examples (used across hooks):
- `API_ROUTES.DATA_QUERY` - generic ClickHouse-proxy query.
- `API_ROUTES.LOGIN`, `API_ROUTES.REFRESH_TOKEN`.
- `API_ROUTES.GET_PROJECT`, `API_ROUTES.USER_PROJECTS`.
- `API_ROUTES.ALERTS_*`, `API_ROUTES.FUNNELS_*`, `API_ROUTES.JOURNEYS_*`.
- Leaf RCA routes live in `src/constants/API.ts` (`POST_RCA_REPORT_ROUTE`,
  `GET_RCA_JOB_ROUTE`, `GET_RCA_STATUS_ROUTE`,
  `POST_SCREEN_RCA_NARRATIVE_ROUTE`, `GET_SCREEN_ROOT_CAUSE_ROUTE`) to
  avoid circular imports.

## 401 -> refresh flow

`makeRequest`:

1. Calls `makeRequestToServer(config)`.
2. If status is `HTTP_STATUS.UNAUTHORIZED` (401):
   a. Calls `getAndSetAccessTokenFromRefreshToken()` (POST to
      `API_ROUTES.REFRESH_TOKEN` using the refresh-token cookie).
   b. On success, retries the original request once.
   c. On failure, clears cookies (`removeAllCookies`), clears
      `sessionStorage`, dispatches a logout event
      (`dispatchLogoutEvent`), and redirects to `ROUTES.LOGIN.basePath`.
3. Returns `processServerResponse(response, unwrapped)`:
   - shape `{ data, error, status }`.
   - `unwrapped: true` flattens the `data` envelope.

`streamAiRunSseWithAuth` follows the identical pattern for SSE responses.

## Generic data query hook

`src/hooks/useGetDataQuery/useGetDataQuery.ts` is the canonical hook for
all real-time ClickHouse-backed queries. Inputs:

```ts
{
  requestBody: {
    dataType: "TRACES" | "LOGS" | ...;
    timeRange: { start: string; end: string };
    select: Array<{ function: "COL" | "CUSTOM"; param: {...}; alias }>;
    groupBy?: string[];
    orderBy?: Array<{ field; direction }>;
    filters?: Array<{ field; operator: OperatorType; value }>;
    limit?: number;
  };
  enabled?: boolean;
  refetchInterval?: number | false;
}
```

It:
- Parses `start`/`end` to UTC ISO via `dayjs.utc` (rejects invalid).
- Gates `enabled` on a valid project (`useProjectQueryEnabled`).
- POSTs to `API_BASE_URL + API_ROUTES.DATA_QUERY.apiPath` through
  `makeRequest<DataQueryResponse>`.
- Returns `useQuery` result with `data.data.fields` / `data.data.rows`.

Most list/detail screens compose hooks on top of `useGetDataQuery` (e.g.
`useGetScreenNames`, `useGetScreenDetails`, `useGetInteractions`,
`useGetActiveSessionsData`).

## Conventions

- Always `makeRequest<T>()`; never raw `fetch`.
- Hook folder = `useXxx/index.ts` + `useXxx.ts` + `useXxx.interface.ts`.
- Mutations: invalidate the related query keys on success.
- Errors: surface `{ data: null, error, status }` from `makeRequest`;
  bubble up to `QueryState`/`ErrorAndEmptyState` for rendering.

## Rebuild recipe

1. Implement `src/helpers/withTimeout` (Promise race with abort).
2. Implement `src/helpers/cookies/` (get/set/remove).
3. Implement `getAccessTokenFromRefreshToken/` (POST refresh-token).
4. Implement `makeRequestToServer/` (raw fetch + headers).
5. Implement `makeRequest/` exactly as documented; wire 401 refresh.
6. Add `processServerResponse` to normalise to `{data, error, status}`.
7. Build `useGetDataQuery` as the first hook; everything else composes
   on top.

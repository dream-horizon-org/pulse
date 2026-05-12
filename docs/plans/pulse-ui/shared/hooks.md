# Shared hooks

Folder pattern (under `pulse-ui/src/hooks/`):

```
useXxx/
  index.ts                   # re-export
  useXxx.ts                  # implementation
  useXxx.interface.ts        # types
```

Aggregated barrel: `src/hooks/index.ts`. Cross-hook interfaces live in
`src/hooks/hooks.interface.ts`.

Every hook either:
- wraps `useQuery` via `useGetDataQuery` (real-time ClickHouse queries
  proxied through `API_ROUTES.DATA_QUERY`), or
- wraps `useQuery` / `useMutation` directly against a typed
  `API_ROUTES.*` entry using `makeRequest<T>`.

## Generic / building blocks

- `useGetDataQuery` - canonical ClickHouse-proxy query (see
  [`../core/api-client.md`](../core/api-client.md)).
- `useProjectQueryEnabled` - returns `true` only after the current
  `projectId` is resolved; used as the `enabled` gate everywhere.
- `useQueryError`, `useQueryMetadata`, `useQueryStats` - bind extra
  state (errors, duration, row counts) to a `useGetDataQuery` result.
- `useAnalytics` - frontend analytics events.
- `usePermissions`, `useTierLimits`, `useUserExperiments`,
  `useInternalRoles`, `useIsInternalRoute` - feature gating.
- `useSdkConfig`, `useSessionReplayFromActiveConfig`,
  `useHeatmapFromActiveConfig` - read SDK side config.

## Screens (real-time data)

- `useGetScreenNames`, `useGetScreenDetails`, `useGetScreens`,
  `useGetScreensHealthData`, `useGetScreenNameToEventQueryMapping`,
  `useGetScreenRootCause`, `useGetScreenRcaNarrative`,
  `useRegenerateScreenRcaNarrative`.

## Sessions / replay

- `useGetActiveSessionsData`, `useGetSessionData`,
  `useGetSessionDetails`, `useGetSessionReplays`, `useGetSpanDetails`,
  `useGetRequestIdFromTime`.

## Critical interactions

- `useGetInteractions`, `useGetInteractionDetails`,
  `useGetInteractionDetailsGraphs`, `useGetInteractionListFilters`,
  `useGetInteractionTime`, `useGetProblematicInteractions`,
  `useGetProblematicInteractionsStats`, `useGetSuggestedInteractions`,
  `useGetTopInteractionsHealthData`, `useDeleteInteraction`,
  `useUpdateInteraction`.

## Alerts

- `useGetAlertList`, `useGetAlertDetails`, `useGetAlertFilters`,
  `useGetAlertMetrics`, `useGetAlertScopes`, `useGetAlertSeverities`,
  `useGetAlertEvaluationHistory`, `useGetAlertNotificationChannels`,
  `useGetNotificationChannelById`, `useCreateAlert`, `useUpdateAlert`,
  `useDeleteAlert`, `useResumeAlert`, `useSnoozeAlert`,
  `useCreateNotificationChannel`, `useUpdateNotificationChannel`,
  `useDeleteNotificationChannel`, `useGetIncidents`.

## Funnels / Journeys

- `useCreateFunnel`, `useGetFunnelData`, `useGetFunnelDetail`,
  `useGetFunnelsList`, `useUpdateFunnel`, `useDeleteFunnel`,
  `useStopFunnel`.
- `useCreateJourney`, `useGetJourneyDetail`, `useGetJourneysList`,
  `useUpdateJourney`, `useDeleteJourney`, `useStopJourney`.

## App Vitals / Errors

- `useGetErrorRate`, `useCachedErrorRate`, `useGetErrorAttribution`,
  `useGetAppStats`, `useGetApdexScore`, `useGetCachedApdexScore`.

## AI / RCA

- `useAiQuery` - mutation that POSTs to AI agent via
  `streamAiRunSseWithAuth`.
- `useGetRcaReport`, `useRegenerateRcaReport`, `useGetJobStatus`,
  `useGetGraphDataFromJobId`.
- `useGetSuggestedQueries`, `useGetUserEvents`.

## Universal query

- `useRunUniversalQuery`, `useValidateUniversalQuery`, `useSubmitQuery`,
  `useGetQueryHistory`, `useCancelQuery`, `useQueryResultFromQueryId`,
  `useQueryResultFromQueryId_diff`, `useUniversalQueryTables`,
  `useUniversalQueryTableColumns`, `useGetEventProps`,
  `useGetTelemetryFilters`, `useGetDashboardFilters`.

## Auth / org / projects

- `useLogin`, `useAcceptTnc`, `useGetTncStatus`, `useCompleteOnboarding`,
  `useCreateProject`, `useCreateTenant`, `useGetProject`,
  `useUserProjects`, `useProjectMembers`, `useTenantMembers`,
  `useProjectApiKeys`, `useUserApiKeys`, `useInternalTenants`.

## Support / engagement / heatmaps

- `useContactSupport`, `useContactUs`, `useGetUserEngagementData`,
  `useGetUserLastActiveToday`, `useHeatmapData`.

See [`../core/api-client.md`](../core/api-client.md) for hook
conventions; see screen docs for which hooks each screen consumes.

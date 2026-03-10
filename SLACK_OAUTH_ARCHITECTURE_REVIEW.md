# Slack OAuth Integration — Architecture Review

**Branch:** `feat/slack-oauth-integration`  
**Review Date:** March 10, 2025  
**Phases:** 1 & 2 complete, Phase 3 in progress

---

## Executive Summary

The Slack OAuth integration has a solid foundation but has several critical gaps that block production readiness. The main issues are: **API contract mismatches** (install endpoint), **missing backend endpoint** (channels), **no OAuth callback handling** (user sees raw JSON), and **state management gaps** after OAuth redirect.

---

## 1. Architecture Review

### 1.1 OAuth Flow (Current)

```
Settings → Connect Slack → GET /install → Redirect to Slack → User approves
  → Slack redirects to Backend /callback?code=...&state=projectId
  → Backend exchanges code, stores token, returns JSON
  → User sees raw JSON in browser (no redirect back to UI)
```

### 1.2 Critical API Contract Mismatch: Install Endpoint

| Layer | Expects | Sends |
|-------|---------|-------|
| **Backend** `SlackOAuthController.install` | `@QueryParam("projectId")` | — |
| **Frontend** `useSlackInstall` | — | `X-Project-Id` header |

**Impact:** The backend will receive `null` for `projectId` and fail validation (`@NotBlank`). The install flow will not work.

**Fix:** Either:
- **Option A:** Frontend sends projectId as query param:  
  `GET /v1/integrations/slack/install?projectId=${projectId}`
- **Option B:** Backend accepts projectId from `X-Project-ID` header (via `ProjectContext`) as fallback when query param is missing.

**Recommendation:** Option A — keep backend contract explicit; add query param in frontend.

---

### 1.3 Missing Backend Endpoint: Slack Channels

| Component | Endpoint | Status |
|-----------|----------|--------|
| `useSlackChannels` | `GET /v1/integrations/slack/channels` | **404 — Not implemented** |
| `SlackOAuthController` | Only `/install`, `/callback` | No `/channels` |

**Impact:** `isSlackConnected` is derived from `slackChannels.length > 0`. The channels query will fail, so the UI will always show "Connect to Slack" even after successful OAuth.

**Fix:** Implement `GET /v1/integrations/slack/channels` in the backend that:
1. Uses `X-Project-ID` (or projectId from context) to find the stored Slack token
2. Calls Slack API `conversations.list` with the bot token
3. Returns `SlackChannelListDto[]` (`{ id, name, isPrivate, isMember }`)

---

### 1.4 Hook Design Assessment

| Hook | Design | Notes |
|------|--------|-------|
| **useSlackInstall** | Imperative, `getInstallUrl()` returns URL | Good. Uses `useState` for loading/error. Consider `useMutation` for consistency with other hooks. |
| **useSlackChannels** | Declarative, React Query | Good. `enabled: !!projectId` prevents unnecessary calls. Returns `[]` on error. |

**Recommendations:**
- `useSlackInstall`: Consider `useMutation` for `getInstallUrl` to align with `useCreateNotificationChannel` pattern and get built-in `isPending`/`reset`.
- Both hooks: Ensure `X-Project-ID` header casing matches backend (`X-Project-ID`, not `X-Project-Id`). HTTP headers are case-insensitive but consistency helps.

---

## 2. State Management

### 2.1 Current Approach

- **Workspace connection status:** Derived from `useSlackChannels` — `isSlackConnected = slackChannels?.length > 0`
- **Project context:** `useProjectContext()` provides `projectId` from `ProjectContext` (sessionStorage-backed)
- **Notification channels list:** `useGetAlertNotificationChannels` (legacy API)
- **React Query:** Used for `useSlackChannels`; mutations invalidate `GET_ALERT_NOTIFICATION_CHANNELS`

### 2.2 Recommendations

| Question | Recommendation |
|----------|----------------|
| **Global state for workspace connection?** | **No.** Keep it derived from `useSlackChannels`. Adding a separate context would duplicate source of truth. |
| **React Query invalidation after OAuth?** | **Yes.** When user returns from OAuth callback, invalidate `[SLACK_CHANNELS.key, projectId]` and `[GET_ALERT_NOTIFICATION_CHANNELS.key]` so the UI reflects the new connection. |
| **Workspace context?** | **No.** Workspace info (name, id) is project-scoped and can stay in React Query cache. `ProjectContext` is for project selection, not Slack metadata. |
| **ProjectContext for workspace info?** | **No.** ProjectContext should remain focused on project identity. Slack workspace state is better as query data. |

### 2.3 Post-OAuth Cache Invalidation

When the user lands back on the Settings page after OAuth:

1. **Callback page** (Phase 3) should call `queryClient.invalidateQueries({ queryKey: [API_ROUTES.SLACK_CHANNELS.key, projectId] })` and `queryClient.invalidateQueries({ queryKey: [API_ROUTES.GET_ALERT_NOTIFICATION_CHANNELS.key] })` before redirecting.
2. If there is no callback page and the backend redirects directly, the frontend Settings page should detect OAuth return (e.g. via `?slack_success=1` in URL) and invalidate.

---

## 3. Routing & Navigation

### 3.1 OAuth Callback Strategy

**Current:** Backend `/callback` returns JSON. Slack redirects the user's browser to the backend URL. User sees raw JSON.

**Recommended:** Backend should **redirect** to a frontend page instead of returning JSON:

```
Slack → Backend /callback
  → Backend exchanges code, stores token
  → Backend responds with 302 Redirect to:
     /projects/{projectId}/settings/notifications?slack_success=1
     (or ?slack_error=... on failure)
```

**Alternative:** Keep backend returning JSON, but set `redirect_uri` in Slack app config to a **frontend** route, e.g. `/integrations/slack/callback`. The frontend page would:
1. Read `code` and `state` from URL
2. Call backend `POST /v1/integrations/slack/exchange` (new endpoint) with `{ code, state }`
3. Backend exchanges code, stores token, returns result
4. Frontend shows success/error and redirects to Settings

**Recommendation:** Backend redirect is simpler and avoids CORS/auth issues. Implement server-side redirect in `SlackOAuthController.callback`.

### 3.2 State Parameter

- Backend correctly uses `state` = `projectId` in install URL (`SlackOAuthService.generateInstallUrl`).
- Callback receives `state` as `projectId` (`SlackOAuthCallbackRequest`).
- **Verified:** projectId flows correctly through OAuth.

### 3.3 Redirect Back to Settings

- After OAuth, redirect to: `/projects/{projectId}/settings/notifications`
- Use `projectId` from `state` (already available in callback).
- Add optional query params: `?slack_success=1` or `?slack_error=access_denied` for UX feedback.

---

## 4. Error Handling

### 4.1 Current Coverage

| Scenario | Handled? | Notes |
|----------|----------|-------|
| User denies OAuth | ✅ Backend | Returns `success: false`, `message: "User denied..."` |
| Invalid/missing code | ✅ Backend | Validation in `SlackOAuthCallbackRequest` |
| Slack API errors | ✅ Backend | `ServiceError.INVALID_SLACK_CODE` |
| Network errors (install) | ⚠️ Partial | `useSlackInstall` sets `error` state; NotificationChannels shows toast |
| useSlackChannels 404/500 | ⚠️ Partial | Returns `[]`; no user-visible error message |
| Token expired | ❌ Not handled | No refresh or re-auth flow |

### 4.2 Recommendations

1. **useSlackChannels:** Surface errors (e.g. `isError`, `error`) so the UI can show "Failed to load channels" and a retry action.
2. **useSlackInstall:** `error` is available but `NotificationChannels` does not display it. Add error UI when `error` is set.
3. **Callback page:** Show clear messages for `access_denied`, `invalid_code`, etc., with a link back to Settings.
4. **Recovery:** Add "Retry" or "Reconnect" when channels fail to load (e.g. token revoked).

---

## 5. Type Safety

### 5.1 Interface Review

**`useGetAlertNotificationChannels.interface.ts`:**

- `SlackOAuthResponseDto` — matches backend (`success`, `workspaceId`, `workspaceName`, `channelId`, `message`, `installUrl`).
- `SlackChannelListDto` — `{ id, name, isPrivate, isMember }` — correct.
- `ChannelType`, `SlackChannelConfig`, etc. — well-defined.

**`useGetDataQuery.interface.ts`:** No change needed for Slack OAuth; unrelated to this flow.

### 5.2 Type Mismatches

| Location | Issue |
|---------|-------|
| `NotificationChannels.tsx` | Uses `NotificationChannelType` (legacy `'slack' \| 'email'`) and `AlertNotificationChannelItem` while the interface defines `ChannelType` and `NotificationChannelDto`. Mixed legacy/new types. |
| `handleSubmit` | Sends `config: formData.slackOAuthConfig.channelId` to legacy `CreateNotificationChannel` API. Legacy API expects `config` as string (webhook URL). OAuth channel config is different. |

**Recommendation:** Clarify whether OAuth-created channels use the legacy API or the new `/v1/notifications/channels` API. The backend OAuth creates a channel in `project_notification_channels` (new system), while the UI uses legacy `notification_channels`. There may be two systems in play — align on one or document the migration path.

---

## 6. Design Issues & Edge Cases

### 6.1 Race Conditions

- **OAuth redirect:** User clicks "Connect", navigates away. When they return, `useSlackChannels` may still be loading. Ensure loading state is shown.
- **Modal open during redirect:** `setSlackConnecting(false)` runs in `finally` before `window.location.href` — the redirect happens asynchronously. The `finally` runs immediately; the redirect is queued. Consider keeping `slackConnecting` true until the page unloads, or accept that it will reset (user has left the page).

### 6.2 Edge Cases

1. **Multiple workspaces:** One project can have only one Slack connection (backend `createOrUpdateSlackChannel` upserts). UI does not support "disconnect" or "switch workspace."
2. **Channel selection before OAuth:** Form allows selecting channel type before connecting. Submit is correctly disabled when `!isSlackConnected`.
3. **Edit existing OAuth channel:** `handleOpenEditModal` infers `slack_oauth` vs `slack_webhook` from `config.startsWith('http')`. OAuth config is JSON with `accessToken` — may not start with `http`. Verify edit flow for OAuth channels.

### 6.3 Backend/Frontend Data Model Mismatch

- **Backend OAuth:** Creates a channel with `SlackChannelConfig` (`accessToken`, `botName`). No `channelId` (target channel for posting).
- **UI:** User selects a channel (e.g. `#alerts`) and submits with `config = channelId`.
- **Gap:** The backend OAuth flow does not persist the user-selected channel. The UI assumes the user will create a "notification channel" with the selected Slack channel — but the OAuth callback already creates a channel. Need to align: either OAuth only stores the token and the user creates a channel with channelId, or OAuth creates a placeholder and the user updates it with channelId.

---

## 7. Recommendations Summary

### Critical (Must Fix)

1. **Install endpoint:** Send `projectId` as query param:  
   `GET /v1/integrations/slack/install?projectId=${projectId}`
2. **Channels endpoint:** Implement `GET /v1/integrations/slack/channels` in the backend.
3. **OAuth callback UX:** Backend should redirect to frontend Settings page (with success/error params) instead of returning JSON.
4. **OAuth + channel creation:** Clarify and implement the flow for storing the user-selected Slack channel ID (either in OAuth callback or in a separate create/update step).

### High Priority

5. **Cache invalidation:** After OAuth return, invalidate `SLACK_CHANNELS` and `GET_ALERT_NOTIFICATION_CHANNELS`.
6. **Error handling:** Show `useSlackInstall.error` and `useSlackChannels` errors in the UI.
7. **Header casing:** Use `X-Project-ID` consistently (match backend).

### Nice to Have

8. **useSlackInstall:** Refactor to `useMutation` for consistency.
9. **Disconnect workspace:** Add ability to disconnect and reconnect Slack.
10. **Edit OAuth channel:** Fix detection of OAuth vs webhook when editing (config format).

---

## 8. Testing Strategy

| Area | Approach |
|------|----------|
| **Unit** | `useSlackInstall` — mock `makeRequest`, assert URL and headers. `useSlackChannels` — mock React Query, assert `enabled` and `queryKey`. |
| **Integration** | Mock backend install/callback/channels; test full flow from Connect → redirect → return → channel list. |
| **E2E** | Use Slack test app; automate OAuth flow and verify channel list and create channel. |
| **Error paths** | Simulate 401, 404, 500 on install and channels; verify error UI. |

---

## 9. Production Readiness

| Criterion | Status |
|-----------|--------|
| Install URL generation | ❌ Broken (projectId mismatch) |
| OAuth callback | ⚠️ Works but poor UX (JSON response) |
| Channels list | ❌ 404 (endpoint missing) |
| State management | ✅ Adequate |
| Error handling | ⚠️ Partial |
| Type safety | ⚠️ Mixed legacy/new |
| Cache invalidation | ❌ Not implemented post-OAuth |

**Verdict:** Not production-ready. Address the critical items (install param, channels endpoint, callback redirect, channel creation flow) before release.

---

## Appendix: Suggested Code Changes

### A. useSlackInstall — Add projectId to URL

```typescript
// useSlackInstall.ts
const url = options.projectId
  ? `${API_BASE_URL}${API_ROUTES.SLACK_INSTALL.apiPath}?projectId=${encodeURIComponent(options.projectId)}`
  : `${API_BASE_URL}${API_ROUTES.SLACK_INSTALL.apiPath}`;

const response = await makeRequest<string>({
  url,
  init: {
    method: API_ROUTES.SLACK_INSTALL.method,
    headers: { "Content-Type": "application/json" },
    // Keep X-Project-ID for TenantFilter if needed by other middleware
    ...(options.projectId && { "X-Project-ID": options.projectId }),
  },
});
```

### B. Backend callback — Redirect to frontend

```java
// SlackOAuthController - return 302 instead of JSON for browser redirect
String frontendBase = config.getFrontendBaseUrl(); // e.g. https://app.pulse.io
String redirectPath = String.format("/projects/%s/settings/notifications", projectId);
if (success) {
  return Response.seeOther(URI.create(frontendBase + redirectPath + "?slack_success=1")).build();
} else {
  return Response.seeOther(URI.create(frontendBase + redirectPath + "?slack_error=" + encode(message))).build();
}
```

### C. Settings page — Invalidate on OAuth return

```typescript
// NotificationChannels.tsx or Settings.tsx
const searchParams = new URLSearchParams(location.search);
const projectId = useProjectContext().projectId;
const queryClient = useQueryClient();

useEffect(() => {
  if (searchParams.get('slack_success') === '1' && projectId) {
    queryClient.invalidateQueries({ queryKey: [API_ROUTES.SLACK_CHANNELS.key, projectId] });
    queryClient.invalidateQueries({ queryKey: [API_ROUTES.GET_ALERT_NOTIFICATION_CHANNELS.key] });
    // Clear query param
    navigate(location.pathname, { replace: true });
  }
}, [searchParams, projectId, queryClient, navigate, location.pathname]);
```

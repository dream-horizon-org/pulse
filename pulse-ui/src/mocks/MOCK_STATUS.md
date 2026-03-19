# Mock Server Status

Last Updated: 2024-03-12

**Coverage**: 67/75 endpoints (89%)

---

## ✅ Fully Implemented (Phase 1 Complete)

| Endpoint | Method | Handler | Notes |
|----------|--------|---------|-------|
| `/v1/auth/login` | POST | `handleAuthEndpoints` | Returns mock JWT, tenant context |
| `/v1/auth/token/refresh` | POST | `handleAuthEndpoints` | Refreshes tokens |
| `/v1/auth/token/verify` | GET | `handleAuthEndpoints` | Validates tokens |
| `/v1/users/me/projects` | GET | `handleV1UserEndpoints` | Returns tenant + projects |
| `/v1/onboarding/complete` | POST | `handleOnboardingEndpoints` | Creates tenant + project |
| `/v1/tnc/status` | GET | `handleTncEndpoints` | Returns accepted: true |
| `/v1/tnc/accept` | POST | `handleTncEndpoints` | Accepts TNC |
| `/v1/tnc/documents` | GET | `handleTncEndpoints` | Returns documents |
| `/v1/tnc/history` | GET | `handleTncEndpoints` | Returns history |
| `/v1/interactions/performance-metric/distribution` | POST | `handleDataQueryEndpoint` | V2 data query generator |
| `/telemetry-filters` | GET | `handleDashboardFiltersEndpoint` | Dashboard filters |
| `/v1/interactions/filter-options` | GET | `handleDashboardFiltersEndpoint` | Filter options |
| `/v1/breadcrumbs` | POST | `handleBreadcrumbsEndpoint` | Breadcrumb events |
| `/query/metadata/table` | GET | `handleRealtimeQueryEndpoints` | Table metadata |
| `/query/tables` | GET | `handleRealtimeQueryEndpoints` | Available tables |
| `/query/history` | GET | `handleRealtimeQueryEndpoints` | Query history |
| `/query/job/*` | GET | `handleRealtimeQueryEndpoints` | Query job status |
| `/query/ai` | POST | `handleRealtimeQueryEndpoints` | AI query assistance |
| `/query` | POST | `handleRealtimeQueryEndpoints` | Execute query |
| `/v1/interactions` | GET/POST | `handleJobEndpoints` | Interactions CRUD |
| `/job/*` | GET/POST/PUT/DELETE | `handleJobEndpoints` | Job management |
| `/permission/check` | GET | `handlePermissionEndpoints` | Permission checks |
| `/alert` | GET/POST/PUT/DELETE | `handleAlertEndpoints` | Alerts CRUD |
| `/session-replays` | GET | `handleSessionReplaysEndpoints` | Session replays |
| `/getApdexScore` | POST | `handleAnalyticsEndpoints` | APDEX metrics |
| `/getErrorRate` | POST | `handleAnalyticsEndpoints` | Error rate |
| `/getInteractionTime` | POST | `handleAnalyticsEndpoints` | Interaction timing |
| `/getInteractionCategory` | POST | `handleAnalyticsEndpoints` | Interaction categories |
| `/api/v1/interaction/insights` | POST | `handleInteractionInsightsEndpoint` | Insights |
| `/getUserEvent` | GET | `handleUserEventsEndpoints` | User events |
| `/validateQuery` | POST | `handleUniversalQueryEndpoints` | Query validation |
| `/fetchQueryData` | POST | `handleUniversalQueryEndpoints` | Fetch results |
| `/getQuery/*` | GET | `handleUniversalQueryEndpoints` | Query details |
| `/analytics-report` | GET | `handleAnalyticsReportEndpoints` | Reports |
| `/incident/generateReport` | POST | `handleAnalyticsReportEndpoints` | Generate report |
| `/anomaly/*` | GET | `handleAnomalyEndpoints` | Anomaly detection |
| `/v1/configs/*` | GET/POST/PUT | `handleSdkConfigV1Endpoints` | SDK config V1 |
| `/sdk-config` | GET/POST/PUT | `handleSdkConfigEndpoints` | SDK config legacy |
| `/events` | GET/POST | `handleEventEndpoints` | Events |
| `/whitelist` | POST | `handleEventEndpoints` | Whitelist events |

---

## ⚠️ Partially Implemented

| Endpoint | Method | What's Missing | Priority |
|----------|--------|----------------|----------|
| `/v1/auth/social/authenticate` | POST | Old flow, needs deprecation | P3 |

---

## ✅ Phase 2 Complete - Tenant & Project Management

### Tenant Operations
| Endpoint | Method | Handler | Status | Notes |
|----------|--------|---------|--------|-------|
| `/v1/tenants/:tenantId` | GET | `handleTenantEndpoints` | ✅ | Returns tenant details |
| `/v1/tenants` | GET | — | ❌ P3 | Tenant list (admin only) |
| `/v1/tenants/:tenantId` | PUT | `handleTenantEndpoints` | ✅ | Update org details |
| `/v1/tenants/:tenantId/deactivate` | PUT | — | ❌ P3 | Deactivate org |
| `/v1/tenants/:tenantId/activate` | PUT | — | ❌ P3 | Activate org |

### Tenant Member Management
| Endpoint | Method | Handler | Status | Notes |
|----------|--------|---------|--------|-------|
| `/v1/tenants/:tenantId/members` | GET | `handleTenantEndpoints` | ✅ | Lists members with roles |
| `/v1/tenants/:tenantId/members` | POST | `handleTenantEndpoints` | ✅ | Single & bulk invite support |
| `/v1/tenants/:tenantId/members/:userId` | DELETE | `handleTenantEndpoints` | ✅ | Remove members |
| `/v1/tenants/:tenantId/members/:userId` | PATCH | `handleTenantEndpoints` | ✅ | Update roles |
| `/v1/tenants/:tenantId/members/leave` | DELETE | — | ❌ P3 | Leave org |

### Project Operations
| Endpoint | Method | Handler | Status | Notes |
|----------|--------|---------|--------|-------|
| `/v1/projects` | POST | `handleProjectEndpoints` | ✅ | Creates project with API key |
| `/v1/projects/:projectId` | GET | `handleProjectEndpoints` | ✅ | Returns project details |
| `/v1/projects/:projectId` | PUT | `handleProjectEndpoints` | ✅ | Update project |
| `/v1/projects/:projectId` | DELETE | — | ❌ P3 | Delete project |

### Project Member Management
| Endpoint | Method | Handler | Status | Notes |
|----------|--------|---------|--------|-------|
| `/v1/projects/:projectId/members` | GET | `handleProjectEndpoints` | ✅ | Lists members with roles |
| `/v1/projects/:projectId/members` | POST | `handleProjectEndpoints` | ✅ | Single & bulk invite support |
| `/v1/projects/:projectId/members/:userId` | DELETE | `handleProjectEndpoints` | ✅ | Remove collaborators |
| `/v1/projects/:projectId/members/:userId` | PATCH | `handleProjectEndpoints` | ✅ | Update roles |
| `/v1/projects/:projectId/members/leave` | DELETE | — | ❌ P3 | Leave project |

---

## ✅ Phase 3 Complete - Remaining CRUD Operations

### API Keys
| Endpoint | Method | Handler | Status | Notes |
|----------|--------|---------|--------|-------|
| `/v1/projects/:projectId/api-keys` | GET | `handleProjectEndpoints` | ✅ | List active API keys (masked) |
| `/v1/projects/:projectId/api-keys` | POST | `handleProjectEndpoints` | ✅ | Generate new API key |
| `/v1/projects/:projectId/api-keys/:apiKeyId` | DELETE | `handleProjectEndpoints` | ✅ | Revoke API key |

### Project Settings
| Endpoint | Method | Handler | Status | Notes |
|----------|--------|---------|--------|-------|
| `/v1/projects/:projectId/settings` | GET | `handleProjectEndpoints` | ✅ | Get project settings |
| `/v1/projects/:projectId/settings` | PUT | `handleProjectEndpoints` | ✅ | Update project settings |

### Auth / Tenant Lookup
| Endpoint | Method | Handler | Status | Notes |
|----------|--------|---------|--------|-------|
| `/v1/auth/tenant/lookup` | GET | `handleAuthEndpoints` | ✅ | Lookup tenant by domain |

## ❌ Phase 4 - Remaining Endpoints (Low Priority)

| Endpoint | Method | Notes |
|----------|--------|-------|
| `/v1/tenants` | GET | Tenant list (admin only) |
| `/v1/tenants/:tenantId/deactivate` | PUT | Deactivate org |
| `/v1/tenants/:tenantId/activate` | PUT | Activate org |
| `/v1/tenants/:tenantId/members/leave` | DELETE | Leave org |
| `/v1/projects/:projectId` | DELETE | Delete project |
| `/v1/projects/:projectId/members/leave` | DELETE | Leave project |

---

## 📊 Statistics

- **Total Backend Endpoints**: ~75
- **Mocked Endpoints**: 67
- **Coverage**: 89%
- **Phase 1**: ✅ Complete (Auth, Onboarding, TNC)
- **Phase 2**: ✅ Complete (Project/Tenant CRUD + Member Lists/Invite)
- **Phase 3**: ✅ Complete (Member removal/role update, entity updates, API keys, settings, tenant lookup)

---

## 🎯 Next Priority - Phase 4

**Remaining Endpoints** (8 endpoints - Low Priority):
- Tenant list, deactivate/activate, leave org
- Delete project, leave project

---

## 🔍 Known Issues

1. **Analytics graphs not loading**: Requires `projectId` in context (backend-dependent)
2. **Mock data reset on refresh**: In-memory only, no persistence
3. **No validation**: Mock accepts any input (backend validates)

## 📝 Recent Updates

### Phase 3 (Completed 2024-03-12)
- ✅ Implemented member removal (DELETE) for tenant and project members
- ✅ Implemented role updates (PATCH) for tenant and project members
- ✅ Implemented project update (PUT) - name, description, isActive
- ✅ Implemented tenant update (PUT) - name, description
- ✅ Implemented API key management (GET list, POST create, DELETE revoke)
- ✅ Implemented project settings (GET, PUT)
- ✅ Implemented tenant lookup (GET /v1/auth/tenant/lookup)
- ✅ Added validation: cannot remove/demote last admin
- ✅ Coverage increased from 73% to 89%

### Phase 2 (Completed 2024-03-12)
- ✅ Implemented project creation and details endpoints
- ✅ Implemented tenant details endpoint
- ✅ Implemented member listing for both tenant and project
- ✅ Implemented member invite (single & bulk) for both tenant and project
- ✅ Added realistic mock data with Indian names and mixed roles
- ✅ Fixed TypeScript compilation errors (Array.from instead of spread)
- ✅ Coverage increased from 64% to 73%

---

## 📝 Notes

- Mock server returns realistic data with Indian context (states, cities, devices)
- Error simulation: 10% by default (configurable via `REACT_APP_MOCK_ERROR_RATE`)
- Delay simulation: 500ms by default (configurable via `REACT_APP_MOCK_DELAY`)
- All mocked endpoints log to console when `REACT_APP_MOCK_LOGGING=true`

# Add SCREEN_NAVIGATION feature enum to backend

## Package

backend/server

## Context

Add `SCREEN_NAVIGATION` enum value to `Features.java` and update `DefaultSdkConfigTemplate.java` to include the new feature in the default SDK config. This allows backend to serve the feature flag to SDK clients.

(Note: This is configuration-only; SDK handles feature gate logic. No TDD needed for enum addition.)

## Acceptance Criteria

- [ ] **`SCREEN_NAVIGATION` enum added** — added to `org.dreamhorizon.pulseserver.service.configs.models.Features` enum
- [ ] **DefaultSdkConfigTemplate updated** — `SCREEN_NAVIGATION` added to `expectedFeatures` list
- [ ] **DefaultSdkConfigTemplateTest updated** — test expectations updated (feature count incremented)
- [ ] **Feature default ON** — `SCREEN_NAVIGATION` defaults to ON in SDK config template
- [ ] **Tests pass** — `DefaultSdkConfigTemplateTest` verifies enum exists + config correct
- [ ] **Checkstyle passes** — no style violations

## Implementation hints

1. `Features.java` is an enum; add one line with name + description.
2. Update `DefaultSdkConfigTemplate.expectedFeatures` list to include `PulseFeature.SCREEN_NAVIGATION`.
3. Increment expected feature count in test.
4. No business logic changes needed (enum is configuration only).

## Eval

```bash
cd backend/server && \
  grep -q "SCREEN_NAVIGATION" src/main/java/org/dreamhorizon/pulseserver/service/configs/models/Features.java && \
  mvn -q -Dtest=DefaultSdkConfigTemplateTest test && \
  mvn -q checkstyle:check
```

## Out of Scope

- SDK feature gate logic (handled in web-sdk issue 4)
- Remote config serving logic (already implemented)
- UI visualization

## Blocked by

None (can be done in parallel, but should merge after SDK is ready)

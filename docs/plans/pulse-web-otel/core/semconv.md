# core/semconv

## 1. Purpose

Single source of truth for every stable string the Web SDK emits — resource keys, signal attribute keys, `pulse.type` values, log bodies, click-type enum, and fixed values (SDK name, version). Mirrors `pulse-android-otel` `PulseAttributes`.

## 2. Source location

- `pulse-web-otel/src/semconv.ts` — `PulseWebSemconv` const object

## 3. Public surface

Exported from `src/index.ts`:

```ts
export { PulseWebSemconv } from "./semconv";
```

Shape (abridged — see file for full list):

```ts
PulseWebSemconv = {
  ResourceKey: { SERVICE_NAME, SERVICE_VERSION, APP_BUILD_NAME, PLATFORM,
                 TELEMETRY_SDK_NAME, RUM_SDK_NAME, RUM_SDK_VERSION,
                 INSTALLATION_ID, PROJECT_ID, BROWSER_NAME, BROWSER_VERSION,
                 OS_NAME, OS_VERSION, DEVICE_TYPE, SCREEN_RESOLUTION,
                 SCREEN_ASPECT_RATIO, SCREEN_COLOR_DEPTH, BROWSER_LANGUAGE,
                 NETWORK_ONLINE, TIMEZONE },
  AttributeKey: { PULSE_TYPE, PULSE_SAMPLED, SESSION_ID, WINDOW_ID,
                  SESSION_PREVIOUS_ID, SESSION_START_REASON,
                  SESSION_DURATION_MS, SESSION_DURATION, SESSION_END_REASON,
                  SCREEN_NAME, PAGE_TITLE, NAVIGATION_TYPE, PAGE_URL,
                  URL_PATH, EXCEPTION_TYPE, EXCEPTION_MESSAGE,
                  EXCEPTION_STACKTRACE, ERROR_FILENAME, ERROR_LINENO,
                  ERROR_COLNO, NON_FATAL_TYPE, NON_FATAL_IS_MANUAL,
                  CLICK_TYPE, CLICK_IS_RAGE, CLICK_RAGE_COUNT,
                  APP_SCREEN_COORDINATE_X/Y/NX/NY, APP_WIDGET_NAME,
                  APP_WIDGET_ID, APP_CLICK_CONTEXT,
                  DEVICE_SCREEN_WIDTH, DEVICE_SCREEN_HEIGHT,
                  WEB_VITAL_NAME, WEB_VITAL_VALUE, WEB_VITAL_RATING,
                  WEB_VITAL_NAVIGATION_TYPE,
                  HTTP_REQUEST_METHOD, URL_FULL, HTTP_RESPONSE_STATUS_CODE,
                  HTTP_REQUEST_BODY_SIZE, HTTP_RESPONSE_BODY_SIZE,
                  SERVER_ADDRESS, SERVER_PORT, NETWORK_PROTOCOL_VERSION,
                  NETWORK_CONNECTION_TYPE, NETWORK_EFFECTIVE_TYPE,
                  NETWORK_RTT, NETWORK_DOWNLINK,
                  USER_ID, PULSE_USER_PREVIOUS_ID, EVENT_NAME,
                  BATTERY_PERCENT, STORAGE_FREE, SPAN_EXPORTER, ... },
  PulseType: { SESSION_START: "session.start",
               SESSION_END: "session.end",
               DEVICE_CRASH: "device.crash",
               NON_FATAL: "non_fatal",
               APP_CLICK: "app.click",
               WEB_VITAL: "web_vital",
               SCREEN_LOAD: "screen_load",
               SCREEN_SESSION: "screen_session",
               INTERACTION: "interaction",
               INSTALLATION_START: "pulse.app.installation.start",
               USER_SESSION_START: "pulse.user.session.start",
               USER_SESSION_END: "pulse.user.session.end",
               CUSTOM_EVENT: "custom_event" },
  LogBody:   { SESSION_START, SESSION_END, APP_WIDGET_CLICK,
               WEB_VITAL, SCREEN_LOAD, SCREEN_SESSION,
               APP_INSTALLATION_START, USER_SESSION_START, USER_SESSION_END },
  ClickTypeValue: { ... },
  FixedValue: { RUM_SDK_NAME, TELEMETRY_SDK_NAME, ... },
};
```

## 4. Internal design

Plain `as const` object literal — TypeScript narrows every value, so consumers get string-literal types. No runtime cost, no class wrapping. The `network` instrumentation builds its `pulse.type` dynamically (`network.<status>`) and so does not use a `PulseType.HTTP` constant — historically called `http` in the spec.

## 5. Dependencies

None.

## 6. Data contracts

This file *is* the contract. Any change here is a breaking change for the Collector + ClickHouse query layer.

## 7. Tests

Indirectly covered by every instrumentation test, plus `src/__tests__/otlp-log-event-name.test.ts` for event names.

## 8. History / decisions

Canonical SPEC: `pulse-web-otel/docs/instrumentations/sdk-core/SPEC.md`. Key compatibility decisions:

- `app.click` matches Android `RumAttributes.TOUCH`.
- `web_vital.*` keys follow web-vitals JS field names.
- `app.build_name` is Pulse-specific and is **the** stack-trace key — `service.version` is informational only.

## 9. Rebuild recipe

1. Recreate `PulseWebSemconv` as a deeply nested `as const` object.
2. Keep the comments — they are the cross-platform mapping notes (Android parity, OTel recommended keys).
3. Never inline strings elsewhere in the SDK — always reference `PulseWebSemconv.AttributeKey.X`.

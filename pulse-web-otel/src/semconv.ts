/**
 * Stable OTEL resource keys, signal attribute keys, and Pulse `pulse.type` values
 * for the web SDK. Keeps ingest contracts consistent and greppable.
 */

export const PulseWebSemconv = {
  ResourceKey: {
    SERVICE_NAME: "service.name",
    SERVICE_VERSION: "service.version",
    /**
     * Android {@code RumConstants.Attributes.BUILD_NAME} — Pulse backend maps this to stack trace
     * {@code AppVersion}. Web mirrors {@code service.version} / {@code PulseWebConfig.serviceVersion}.
     */
    APP_BUILD_NAME: "app.build_name",
    PLATFORM: "platform",
    /** OTel resource key; value matches {@code FixedValue.TELEMETRY_SDK_NAME} — mobile SDK parity. */
    TELEMETRY_SDK_NAME: "telemetry.sdk.name",
    RUM_SDK_NAME: "rum.sdk.name",
    RUM_SDK_VERSION: "rum.sdk.version",
    INSTALLATION_ID: "installation.id",
    PROJECT_ID: "project.id",
    BROWSER_NAME: "browser.name",
    BROWSER_VERSION: "browser.version",
    OS_NAME: "os.name",
    OS_VERSION: "os.version",
    DEVICE_TYPE: "device.type",
    SCREEN_RESOLUTION: "screen.resolution",
    SCREEN_ASPECT_RATIO: "screen.aspect_ratio",
    SCREEN_COLOR_DEPTH: "screen.color_depth",
    BROWSER_LANGUAGE: "browser.language",
    NETWORK_ONLINE: "network.online",
    TIMEZONE: "timezone",
  },
  AttributeKey: {
    PULSE_TYPE: "pulse.type",
    PULSE_SAMPLED: "pulse.sampled",
    SESSION_ID: "session.id",
    WINDOW_ID: "window.id",
    SESSION_PREVIOUS_ID: "session.previous_id",
    SESSION_START_REASON: "session.start_reason",
    SESSION_DURATION_MS: "session.duration_ms",
    /** Duplicate of {@link SESSION_DURATION_MS} for FINAL-PLAN / dashboard parity (`session.duration`). */
    SESSION_DURATION: "session.duration",
    SESSION_END_REASON: "session.end_reason",
    INSTALLATION_ID: "installation.id",
    SCREEN_NAME: "screen.name",
    /** Document title from `document.title` (screen navigation logs). */
    PAGE_TITLE: "page.title",
    /** Browser navigation API type on initial load (`navigate` \| `reload` \| `back_forward`). */
    NAVIGATION_TYPE: "navigation.type",
    PLATFORM: "platform",
    URL_PATH: "url.path",
    PAGE_URL: "page.url",
    NETWORK_CONNECTION_TYPE: "network.connection.type",
    NETWORK_EFFECTIVE_TYPE: "network.effective_type",
    NETWORK_RTT: "network.rtt",
    NETWORK_DOWNLINK: "network.downlink",
    USER_ID: "user.id",
    PULSE_USER_PREVIOUS_ID: "pulse.user.previous_id",
    EVENT_NAME: "event.name",
    EXCEPTION_TYPE: "exception.type",
    EXCEPTION_MESSAGE: "exception.message",
    EXCEPTION_STACKTRACE: "exception.stacktrace",
    ERROR_FILENAME: "error.filename",
    ERROR_LINENO: "error.lineno",
    ERROR_COLNO: "error.colno",
    BATTERY_PERCENT: "battery.percent",
    STORAGE_FREE: "storage.free",
    NON_FATAL_TYPE: "non_fatal.type",
    NON_FATAL_IS_MANUAL: "non_fatal.is_manual",
    /**
     * Number of `device.crash` logs emitted during the session.
     * Attached to `session.end` — Android parity: `pulse.session.crash.count`.
     * Omitted when 0 (matching Android `?.let` pattern).
     */
    SESSION_CRASH_COUNT: "pulse.session.crash.count",
    /**
     * Number of `non_fatal` logs emitted during the session.
     * Attached to `session.end` — Android parity: `pulse.session.non_fatal.count`.
     * Omitted when 0 (matching Android `?.let` pattern).
     */
    SESSION_NON_FATAL_COUNT: "pulse.session.non_fatal.count",
    /** Init log (`otel.initialization.events`) — exporter wiring hint (Android parity). */
    SPAN_EXPORTER: "span.exporter",
    CLICK_TYPE: "click.type",
    CLICK_IS_RAGE: "click.is_rage",
    CLICK_RAGE_COUNT: "click.rage_count",
    APP_SCREEN_COORDINATE_X: "app.screen.coordinate.x",
    APP_SCREEN_COORDINATE_Y: "app.screen.coordinate.y",
    APP_SCREEN_COORDINATE_NX: "app.screen.coordinate.nx",
    APP_SCREEN_COORDINATE_NY: "app.screen.coordinate.ny",
    APP_WIDGET_NAME: "app.widget.name",
    APP_WIDGET_ID: "app.widget.id",
    APP_CLICK_CONTEXT: "app.click.context",
    /** Logical viewport (`window.innerWidth` / `innerHeight`), Android `device.screen.*` parity. */
    DEVICE_SCREEN_WIDTH: "device.screen.width",
    DEVICE_SCREEN_HEIGHT: "device.screen.height",
    /** Web vital metric name: LCP, INP, CLS, FCP, TTFB (`web-vitals` v5+; FID removed upstream). */
    WEB_VITAL_NAME: "web_vital.name",
    WEB_VITAL_VALUE: "web_vital.value",
    WEB_VITAL_RATING: "web_vital.rating",
    WEB_VITAL_NAVIGATION_TYPE: "web_vital.navigation_type",
    /** UUID per navigation (cold, SPA, BFCache) — join vitals to `screen_load` spans. */
    NAVIGATION_ID: "navigation_id",
    /** Derived from {@code Metric.navigationType}: soft-nav vs page load (omitted when undefined). */
    WEB_VITAL_CONTEXT: "web_vital.context",
    /** Incremental delta since last callback (CLS/INP with {@code reportAllChanges}). */
    WEB_VITAL_DELTA: "web_vital.delta",
    /** Stable OTel HTTP semconv keys for CLIENT spans; {@code pulse.type} values are {@code network.<statusCode>} (Android parity). */
    HTTP_REQUEST_METHOD: "http.request.method",
    HTTP_REQUEST_METHOD_ORIGINAL: "http.request.method_original",
    URL_FULL: "url.full",
    HTTP_RESPONSE_STATUS_CODE: "http.response.status_code",
    HTTP_REQUEST_BODY_SIZE: "http.request.body.size",
    HTTP_RESPONSE_BODY_SIZE: "http.response.body.size",
    SERVER_ADDRESS: "server.address",
    SERVER_PORT: "server.port",
    /** OTel Recommended — populated from Resource Timing `nextHopProtocol` when available. */
    NETWORK_PROTOCOL_VERSION: "network.protocol.version",
    PEER_SERVICE: "peer.service",
    /** Pulse convenience duplicate of span duration (integer ms). */
    HTTP_DURATION_MS: "http.duration",
    GRAPHQL_OPERATION_NAME: "graphql.operation.name",
    GRAPHQL_OPERATION_TYPE: "graphql.operation.type",
    ERROR_TYPE: "error.type",
    /** Screen navigation attributes */
    LAST_SCREEN_NAME: "last.screen.name",
    START_TYPE: "start.type",
    PAGE_LOAD_TIME: "page.load_time",
    TTFB: "ttfb",
    DNS_TIME: "dns.time",
    TCP_TIME: "tcp.time",
    DOM_PROCESSING_TIME: "dom.processing_time",
    TTI: "tti",
  },
  FixedValue: {
    PLATFORM_WEB: "web",
    /** OTel {@code telemetry.sdk.name} — matches Android/iOS/RN Pulse SDK naming. */
    TELEMETRY_SDK_NAME: "pulse_web_js",
    RUM_SDK_NAME: "pulse_web_js",
    EVENT_NAME_CUSTOM_EVENT: "pulse.custom_event",
  },
  /**
   * OTLP / Android parity strings for log {@code event_name} (protobuf) and {@link Logger.emit} {@code eventName}.
   * We also set {@link AttributeKey.EVENT_NAME} (`event.name`) for collectors that index attributes.
   * See {@code src/__tests__/otlp-log-event-name.test.ts} for sdk-logs wiring.
   */
  LogEventName: {
    DEVICE_CRASH: "device.crash",
    CUSTOM_NON_FATAL: "pulse.custom_non_fatal",
  },
  ClickTypeValue: {
    GOOD: "good",
    DEAD: "dead",
  },
  PulseType: {
    INSTALLATION_START: "pulse.app.installation.start",
    CUSTOM_EVENT: "custom_event",
    NON_FATAL: "non_fatal",
    DEVICE_CRASH: "device.crash",
    SESSION_START: "session.start",
    SESSION_END: "session.end",
    USER_SESSION_START: "pulse.user.session.start",
    USER_SESSION_END: "pulse.user.session.end",
    INTERACTION: "interaction",
    /** Same value as Android `PulseAttributes.PulseTypeValues.TOUCH` (`app.click`). */
    APP_CLICK: "app.click",
    WEB_VITAL: "web_vital",
    SCREEN_LOAD: "screen_load",
    SCREEN_SESSION: "screen_session",
    /** Emitted after `screen_load` on cold/reload navigations when Navigation Timing TTI is available. RN/Web parity — Android does not emit this. */
    SCREEN_INTERACTIVE: "screen_interactive",
    CUSTOM_SPAN: "custom_span",
  },
  LogBody: {
    SESSION_START: "session.start",
    SESSION_END: "session.end",
    APP_INSTALLATION_START: "pulse.app.installation.start",
    USER_SESSION_START: "pulse.user.session.start",
    USER_SESSION_END: "pulse.user.session.end",
    /** OTLP log body; matches Android log event name `app.widget.click`. */
    APP_WIDGET_CLICK: "app.widget.click",
    WEB_VITAL: "web_vital",
    SCREEN_LOAD: "screen_load",
    SCREEN_SESSION: "screen_session",
  },
  /**
   * Init milestones as OTLP **logs** (Android `SdkInitializationEvents` / `RumConstants.Events`).
   * Same strings as `io.opentelemetry.android.common.RumConstants.Events` INIT_EVENT_*.
   */
  RumSdkInit: {
    STARTED: "rum.sdk.init.started",
    SPAN_EXPORTER: "rum.sdk.init.span.exporter",
  },
  InteractionAttributeKey: {
    ID: "pulse.interaction.id",
    NAME: "pulse.interaction.name",
    CONFIG_ID: "pulse.interaction.config.id",
    CONFIG_NAME: "pulse.interaction.config.name",
    COMPLETE_TIME: "pulse.interaction.complete_time",
    APDEX_SCORE: "pulse.interaction.apdex_score",
    USER_CATEGORY: "pulse.interaction.user_category",
    IS_ERROR: "pulse.interaction.is_error",
    ERROR_TYPE: "pulse.interaction.error.type",
    ERROR_MESSAGE: "pulse.interaction.error.message",
    /** String array of in-flight interaction flow names — stamped on concurrent spans. */
    NAMES: "pulse.interaction.names",
    /** String array of in-flight interaction IDs — stamped on concurrent spans. */
    IDS: "pulse.interaction.ids",
  },
  InteractionUserCategory: {
    EXCELLENT: "Excellent",
    GOOD: "Good",
    AVERAGE: "Average",
    POOR: "Poor",
  },
  /**
   * Canonical lowercase HTTP header names — never copy values onto spans from optional capture config.
   * Used by {@code isSensitiveCapturedHeaderName} in {@code utils/network-http.ts}.
   */
  SensitiveCapturedHeaderName: {
    AUTHORIZATION: "authorization",
    COOKIE: "cookie",
    SET_COOKIE: "set-cookie",
    PROXY_AUTHORIZATION: "proxy-authorization",
    X_API_KEY: "x-api-key",
    X_AUTH_TOKEN: "x-auth-token",
  },
  /**
   * Lowercase query param names — when {@code captureQueryParams} is true, values are replaced
   * with {@code ***} (keys kept). See {@code isSensitiveQueryParamName} in {@code utils/network-http.ts}.
   */
  SensitiveQueryParamName: {
    TOKEN: "token",
    ACCESS_TOKEN: "access_token",
    REFRESH_TOKEN: "refresh_token",
    ID_TOKEN: "id_token",
    BEARER: "bearer",
    API_KEY: "api_key",
    APIKEY: "apikey",
    PASSWORD: "password",
    SECRET: "secret",
    CLIENT_SECRET: "client_secret",
    SIGNATURE: "signature",
    SIG: "sig",
    AUTH: "auth",
  },
} as const;

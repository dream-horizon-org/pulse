export enum PulseType {
  INTERACTION = "interaction",
  SCREEN_SESSION = "screen_session",
  SCREEN_LOAD = "screen_load",
  NAVIGATION = "navigation",
  APP_START = "app_start",
  SCREEN_NAME = "screen.name",
  SESSION_START = "session.start",
  NETWORK_LIKE = "network.%"
}

export enum STATUS_CODE {
  UN_SET = "Unset",
  ERROR = "Error",
  OK = "Ok",
}
export enum COLUMN_NAME {
  EXCEPTION_TYPE = "ExceptionType",
  DEVICE_MODEL = "DeviceModel",
  NETWORK_PROVIDER = "NetworkProvider",
  OS_VERSION = "OsVersion",
  PLATFORM = "Platform",
  STATE = "GeoState",
  COUNTRY = "GeoCountry",
  APP_VERSION = "AppVersion",
  APP_VERSION_CODE = "AppVersionCode",
  DURATION = "Duration",
  USER_ID = "UserId",
  TIMESTAMP = "Timestamp",
  SPAN_ID = "SpanId",
  TRACE_ID = "TraceId",
  SESSION_ID = "SessionId",
  PULSE_TYPE = "PulseType",
  SPAN_NAME = "SpanName",
  /** otel_logs — OTLP log record body (e.g. custom event name). */
  BODY = "Body",
  /** otel_logs — OTLP EventName column. */
  EVENT_NAME = "EventName",
  /** otel_logs — LogAttributes map (use in expressions, e.g. toJSONString). */
  LOG_ATTRIBUTES = "LogAttributes",
  DEVICE_MANUFACTURER = "device.manufacturer",
  OS_TYPE = "os.type",
  OS_DESCRIPTION = "os.description",
  FROZEN_FRAME_COUNT = "app.interaction.frozen_frame_count",
  IS_ERROR = "isError",
  EVENTS_NAME = "Events.Name",
  EVENTS_TIMESTAMP = "Events.Timestamp",
  HTTP_URL = "HttpUrl",
  NETWORK_STATUS_CODE = 'HttpStatusCode',
  INSTALLATION_ID = 'AppInstallationId',
  GRAPHQL_OPERATION_NAME = 'GraphqlName',
  GRAPHQL_OPERATION_TYPE = 'GraphqlType',
  SCREEN_NAME = 'ScreenName'
}

/**
 * Response field aliases for LOG data-query selects (must match each select item's `alias`).
 * Used when reading `PerformanceMetricDistributionRes.fields` / row indices.
 */
export enum LogDataQueryAlias {
  TIMESTAMP = "timestamp",
  BODY = "body",
  PULSE_TYPE = "pulse_type",
  EVENT_NAME = "event_name",
  LOG_ATTRIBUTES_JSON = "log_attributes",
  SPAN_ID = "span_id",
  /** "1" / "0" — cheap hint for whether LogAttributes has keys (list query only). */
  HAS_LOG_ATTRIBUTES = "has_log_attributes",
}

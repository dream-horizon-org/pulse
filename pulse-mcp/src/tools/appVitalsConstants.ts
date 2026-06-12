/**
 * Column and pulse-type strings for App Vitals distribution queries.
 * Keep in sync with pulse-ui/src/constants/PulseOtelSemcov.ts
 */
export const COLUMN_NAME = {
  EXCEPTION_TYPE: "ExceptionType",
  DEVICE_MODEL: "DeviceModel",
  NETWORK_PROVIDER: "NetworkProvider",
  OS_VERSION: "OsVersion",
  PLATFORM: "Platform",
  STATE: "GeoState",
  APP_VERSION: "AppVersion",
  USER_ID: "UserId",
  TIMESTAMP: "Timestamp",
  SESSION_ID: "SessionId",
  PULSE_TYPE: "PulseType",
  APP_INSTALLATION_ID: "AppInstallationId",
} as const;

/** app start filter value for denominator queries */
export const PULSE_TYPE_APP_START = "app_start";
export const PULSE_TYPE_SESSION_START = "session.start";

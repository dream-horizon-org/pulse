/**
 * Constants for Pulse React Native OpenTelemetry integration
 */

export enum SPAN_NAMES {
  SCREEN_SESSION = 'ScreenSession',
  NAVIGATED = 'Navigated',
  SCREEN_INTERACTIVE = 'ScreenInteractive',
}

export enum ATTRIBUTE_KEYS {
  PULSE_TYPE = 'pulse.type',
  SCREEN_NAME = 'screen.name',
  ROUTE_KEY = 'routeKey',
  LAST_SCREEN_NAME = 'last.screen.name',
  ROUTE_HAS_BEEN_SEEN = 'routeHasBeenSeen',
  PLATFORM = 'platform',
  GRAPHQL_OPERATION_NAME = 'graphql.operation.name',
  GRAPHQL_OPERATION_TYPE = 'graphql.operation.type',
  HTTP_METHOD = 'http.method',
  HTTP_URL = 'http.url',
  HTTP_STATUS_CODE = 'http.status_code',
  HTTP_REQUEST_TYPE = 'http.request.type',
  HTTP_REQUEST_HEADER = 'http.request.header',
  HTTP_RESPONSE_HEADER = 'http.response.header',
  ERROR_MESSAGE = 'error.message',
  ERROR_STACK = 'error.stack',
}

export enum PULSE_TYPES {
  SCREEN_SESSION = 'screen_session',
  SCREEN_LOAD = 'screen_load',
  SCREEN_INTERACTIVE = 'screen_interactive',
  NETWORK = 'network',
}

export const PULSE_FEATURE_NAMES = {
  RN_SCREEN_LOAD: 'rn_screen_load',
  SCREEN_SESSION: 'screen_session',
  RN_SCREEN_INTERACTIVE: 'rn_screen_interactive',
  NETWORK_INSTRUMENTATION: 'network_instrumentation',
  CUSTOM_EVENTS: 'custom_events',
  JS_CRASH: 'js_crash',
} as const;

export type PulseFeatureName =
  (typeof PULSE_FEATURE_NAMES)[keyof typeof PULSE_FEATURE_NAMES];

export type NavigationFeatureName =
  | typeof PULSE_FEATURE_NAMES.SCREEN_SESSION
  | typeof PULSE_FEATURE_NAMES.RN_SCREEN_LOAD
  | typeof PULSE_FEATURE_NAMES.RN_SCREEN_INTERACTIVE;

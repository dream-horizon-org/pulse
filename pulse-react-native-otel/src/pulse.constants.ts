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
  PHASE = 'phase',
  LAST_SCREEN_NAME = 'last.screen.name',
  ROUTE_HAS_BEEN_SEEN = 'routeHasBeenSeen',
  PLATFORM = 'platform',
}

export enum PULSE_TYPES {
  SCREEN_SESSION = 'screen_session',
  SCREEN_LOAD = 'screen_load',
  SCREEN_INTERACTIVE = 'screen_interactive',
}

export enum PHASE_VALUES {
  START = 'start',
}

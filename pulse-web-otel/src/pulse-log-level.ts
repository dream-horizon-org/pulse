/**
 * SDK log verbosity. Numeric order matches React Native and Android Kotlin ordinals
 * (lower = more verbose; {@link PulseLogLevel.NONE} suppresses all SDK diagnostics).
 */
export enum PulseLogLevel {
  VERBOSE = 0,
  DEBUG = 1,
  INFO = 2,
  WARN = 3,
  ERROR = 4,
  NONE = 5,
}

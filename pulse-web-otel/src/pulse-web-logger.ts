import { PulseLogLevel } from "./pulse-log-level";

const TAG = "PulseWeb";

let currentLevel: PulseLogLevel = PulseLogLevel.NONE;

export const PulseWebLogger = {
  setLevel(level: PulseLogLevel): void {
    currentLevel = level;
  },

  getLevel(): PulseLogLevel {
    return currentLevel;
  },

  /** Test isolation — resets module-level level. */
  resetForTesting(): void {
    currentLevel = PulseLogLevel.NONE;
  },

  verbose(message: string): void {
    if (currentLevel <= PulseLogLevel.VERBOSE) {
      console.debug(`${TAG} ${message}`);
    }
  },

  debug(message: string): void {
    if (currentLevel <= PulseLogLevel.DEBUG) {
      console.debug(`${TAG} ${message}`);
    }
  },

  info(message: string): void {
    if (currentLevel <= PulseLogLevel.INFO) {
      console.log(`${TAG} ${message}`);
    }
  },

  warn(message: string): void {
    if (currentLevel <= PulseLogLevel.WARN) {
      console.warn(`${TAG} ${message}`);
    }
  },

  error(message: string): void {
    if (currentLevel <= PulseLogLevel.ERROR) {
      console.error(`${TAG} ${message}`);
    }
  },
};

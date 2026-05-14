// M3: Error instrumentation — captures unhandled JS errors and promise rejections
// as OTLP log records. Maps to device.crash / non_fatal pulse.type values.
// Android parity: CrashReporter.kt / NonFatalReporter.kt

import { logs, SeverityNumber } from "@opentelemetry/api-logs";
import { context } from "@opentelemetry/api";
import type {
  PulseInstrumentation,
  SdkContext,
} from "../instrumentation-registry";
import { PulseWebSemconv } from "../semconv";

export class ErrorInstrumentation implements PulseInstrumentation {
  readonly name = "errors";

  private onErrorHandler?: (e: ErrorEvent) => void;
  private onRejectionHandler?: (e: PromiseRejectionEvent) => void;
  private dedupeCache = new Map<string, number>();
  private readonly DEDUPE_WINDOW_MS = 5_000;

  // Device state — prefetched on install, kept fresh via event listeners
  private batteryPercent: number | undefined;
  private storageFreeBytes: number | undefined;
  // Retained so it can be detached on uninstall() — avoids leak on SDK restarts
  private batteryLevelChangeHandler?: () => void;
  private batteryRef?: {
    removeEventListener(type: string, cb: () => void): void;
  };

  install(_sdk: SdkContext): void {
    if (typeof window === "undefined") return;

    const K = PulseWebSemconv.AttributeKey;
    const T = PulseWebSemconv.PulseType;
    const eventNames = PulseWebSemconv.LogEventName;
    const logger = logs.getLogger("pulse-web-errors");

    // Prefetch device state in background — available on next crash
    void this.prefetchDeviceState();

    this.onErrorHandler = (e: ErrorEvent) => {
      // Cross-origin errors — browser blocks stack/filename for security.
      // Emit a stub device.crash to preserve crash counts (Android parity: always records a stub).
      if (e.message === "Script error." && !e.filename) {
        logger.emit({
          eventName: eventNames.DEVICE_CRASH,
          body: "Script error.",
          timestamp: Date.now(),
          severityNumber: SeverityNumber.FATAL,
          severityText: "FATAL",
          context: context.active(),
          attributes: {
            [K.EVENT_NAME]: eventNames.DEVICE_CRASH,
            [K.PULSE_TYPE]: T.DEVICE_CRASH,
            [K.EXCEPTION_TYPE]: "Error",
            [K.EXCEPTION_MESSAGE]: "Script error.",
            [K.EXCEPTION_STACKTRACE]: "",
            [K.ERROR_FILENAME]: "",
            [K.URL_PATH]: window.location.pathname,
            ...(this.batteryPercent !== undefined && {
              [K.BATTERY_PERCENT]: this.batteryPercent,
            }),
            ...(this.storageFreeBytes !== undefined && {
              [K.STORAGE_FREE]: this.storageFreeBytes,
            }),
          },
        });
        return;
      }

      const error = e.error instanceof Error ? e.error : new Error(e.message);

      const fingerprint = `${error.name}:${error.message}:${e.filename}:${e.lineno}:${e.colno}`;
      if (this.isDuplicate(fingerprint)) return;

      // BUG-1: guard against browsers (iOS WebKit) that set e.filename to the
      // string "undefined" instead of an empty string or a real URL.
      const filename =
        e.filename && e.filename !== "undefined" ? e.filename : "unknown";

      // BUG-2 mitigation| platfrom limitations for iOS: JSC (iOS WebKit) produces "@" for anonymous setTimeout
      // callbacks — construct a fallback frame from onerror location attrs.
      const rawStack = error.stack ?? "";
      const stack =
        !rawStack || rawStack.trim() === "@"
          ? `@${filename}:${e.lineno}:${e.colno}`
          : rawStack;

      logger.emit({
        eventName: eventNames.DEVICE_CRASH,
        body: error.message,
        timestamp: Date.now(),
        severityNumber: SeverityNumber.FATAL,
        severityText: "FATAL",
        context: context.active(),
        attributes: {
          [K.EVENT_NAME]: eventNames.DEVICE_CRASH,
          [K.PULSE_TYPE]: T.DEVICE_CRASH,
          [K.EXCEPTION_TYPE]: error.name,
          [K.EXCEPTION_MESSAGE]: error.message,
          [K.EXCEPTION_STACKTRACE]: stack,
          [K.ERROR_FILENAME]: filename,
          [K.ERROR_LINENO]: e.lineno,
          [K.ERROR_COLNO]: e.colno,
          [K.URL_PATH]: window.location.pathname,
          ...(this.batteryPercent !== undefined && {
            [K.BATTERY_PERCENT]: this.batteryPercent,
          }),
          ...(this.storageFreeBytes !== undefined && {
            [K.STORAGE_FREE]: this.storageFreeBytes,
          }),
        },
      });
    };

    this.onRejectionHandler = (e: PromiseRejectionEvent) => {
      const error =
        e.reason instanceof Error
          ? e.reason
          : new Error(String(e.reason ?? "Unknown rejection"));

      // Include first stack frame so distinct async errors with identical messages aren't deduped
      const firstFrame = error.stack?.split("\n")[1] ?? "";
      const fingerprint = `${error.name}:${error.message}:${firstFrame}`;
      if (this.isDuplicate(fingerprint)) return;

      logger.emit({
        eventName: eventNames.CUSTOM_NON_FATAL,
        body: error.message,
        timestamp: Date.now(),
        severityNumber: SeverityNumber.WARN,
        severityText: "WARN",
        context: context.active(),
        attributes: {
          [K.EVENT_NAME]: eventNames.CUSTOM_NON_FATAL,
          [K.PULSE_TYPE]: T.NON_FATAL,
          [K.EXCEPTION_TYPE]: error.name,
          [K.EXCEPTION_MESSAGE]: error.message,
          [K.EXCEPTION_STACKTRACE]: error.stack ?? "",
          [K.URL_PATH]: window.location.pathname,
          [K.NON_FATAL_IS_MANUAL]: false,
        },
      });
    };

    window.addEventListener("error", this.onErrorHandler);
    window.addEventListener("unhandledrejection", this.onRejectionHandler);
  }

  uninstall(): void {
    if (this.onErrorHandler) {
      window.removeEventListener("error", this.onErrorHandler);
    }
    if (this.onRejectionHandler) {
      window.removeEventListener("unhandledrejection", this.onRejectionHandler);
    }
    if (this.batteryRef && this.batteryLevelChangeHandler) {
      this.batteryRef.removeEventListener(
        "levelchange",
        this.batteryLevelChangeHandler,
      );
    }
    this.dedupeCache.clear();
    this.batteryPercent = undefined;
    this.storageFreeBytes = undefined;
    this.batteryLevelChangeHandler = undefined;
    this.batteryRef = undefined;
  }

  private async prefetchDeviceState(): Promise<void> {
    if ("getBattery" in navigator) {
      try {
        const battery = await (
          navigator as Navigator & {
            getBattery(): Promise<{
              level: number;
              addEventListener(type: string, cb: () => void): void;
              removeEventListener(type: string, cb: () => void): void;
            }>;
          }
        ).getBattery();
        this.batteryPercent = Math.round(battery.level * 100);
        this.batteryLevelChangeHandler = () => {
          this.batteryPercent = Math.round(battery.level * 100);
        };
        this.batteryRef = battery;
        battery.addEventListener("levelchange", this.batteryLevelChangeHandler);
      } catch {
        /* not supported */
      }
    }
    if (
      "storage" in navigator &&
      navigator.storage != null &&
      "estimate" in navigator.storage
    ) {
      try {
        const { quota = 0, usage = 0 } = await navigator.storage.estimate();
        this.storageFreeBytes = quota - usage;
      } catch {
        /* not supported */
      }
    }
  }

  private isDuplicate(fingerprint: string): boolean {
    const now = Date.now();
    // Prune stale entries
    for (const [key, ts] of this.dedupeCache) {
      if (now - ts >= this.DEDUPE_WINDOW_MS) this.dedupeCache.delete(key);
    }
    const last = this.dedupeCache.get(fingerprint);
    if (last !== undefined && now - last < this.DEDUPE_WINDOW_MS) return true;
    this.dedupeCache.set(fingerprint, now);
    return false;
  }
}
